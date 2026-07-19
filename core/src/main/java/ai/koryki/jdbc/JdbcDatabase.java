/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.jdbc;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.TypeDescriptor;

import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JdbcDatabase<C extends ResultProcessor<?>> implements Database<C> {

    private String name;
    private TypeDecoder decoder;
    private Connection connection;
    private final ZoneId modelZone;

//    public JdbcDatabase(String name, Connection conn) {
//        this(name, conn, null);
//    }

    public JdbcDatabase(String name, Connection conn, ZoneId modelZone) {
        this.name = name;
        this.connection = conn;
        this.modelZone = modelZone;
        this.decoder = new CoreDecoder(this.modelZone);
        String tz = sessionTimeZoneStatement(this.modelZone);
        if (tz != null) {
            sessionInit(tz);
        }
    }

    /** The model time zone (docs/TEMPORAL.md). */
    public ZoneId getModelZone() {
        return modelZone;
    }

    /** Dialect subclasses install a decoder that also handles their native interval objects. */
    protected void setDecoder(TypeDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * The dialect's statement that pins the session to {@code zone}, or {@code null} if the dialect
     * has no session time zone (SQLite, SQL Server). Driven from the base constructor. Overrides
     * format {@code zone} with {@link #zoneLiteral}/{@link #offsetLiteral}. A pure function of
     * {@code zone} (called during construction, so it must not touch subclass state).
     */
    protected String sessionTimeZoneStatement(ZoneId zone) {
        return null;
    }

    /** Render {@code zone} as an IANA name / offset for dialects whose SET accepts it (default UTC → "UTC"). */
    protected static String zoneLiteral(ZoneId zone) {
        return zone.normalized().equals(ZoneOffset.UTC) ? "UTC" : zone.getId();
    }

    /** Render {@code zone} as a numeric UTC offset (no tz tables needed); falls back to the name for a region zone. */
    protected static String offsetLiteral(ZoneId zone) {
        ZoneId z = zone.normalized();
        if (z instanceof ZoneOffset off) {
            return off.equals(ZoneOffset.UTC) ? "+00:00" : off.getId();
        }
        return zone.getId();
    }

    public DatabaseMetaData getMetadata() throws SQLException {
        return connection.getMetaData();
    }

    /**
     * Pins session state on the wrapped connection. The base constructor uses this to fix the
     * session time zone to the model zone (via {@link #sessionTimeZoneStatement}) right after
     * connecting — per docs/TEMPORAL.md, zone-aware reads and {@code now()} must never depend on
     * who runs the query. Subclasses may also call it for further session pins.
     */
    protected void sessionInit(String sql) {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new KorykiaiException("session init failed: " + sql, e);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    @Override
    public void execute(String sql, Consumer<PreparedStatement> statementConsumer) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            statementConsumer.accept(stmt);
        } catch (SQLException e) {
            throw new KorykiaiException(e);
        }
    }

    public void execute(PreparedStatement statement, C processor) {

        List<? extends ColumnInfo> infos = processor.getInfos();
        try (ResultSet r = statement.executeQuery()) {

            ResultSetMetaData meta = r.getMetaData();
            processor.metadata(meta);
            int columns = meta.getColumnCount();
            boolean typed = infos != null && infos.size() == columns;
            while (r.next()) {

                List<Object> row = new ArrayList<>();
                for (int i = 0; i < columns; i++) {
                    ColumnInfo info = typed ? infos.get(i) : null;
                    row.add(read(r, i + 1, meta.getColumnType(i + 1), info));
                }

                if (!processor.append(row)) {
                    break;
                }
            }
        } catch (SQLException e) {
            throw new KorykiaiException(e);
        }
    }

    private Object read(ResultSet rs, int i, int jdbcType, ColumnInfo info) throws SQLException {

        // An INSTANT column is an absolute point in time. The JDBC type the driver reports for it
        // is NOT a reliable discriminator (pgjdbc and MariaDB both report Types.TIMESTAMP yet need
        // opposite handling), so read it through the dialect-aware readInstant() hook BEFORE the
        // lossy getTimestamp().toLocalDateTime() below would bake in the JVM zone. The result is a
        // java.time.Instant; LocaleFormat renders it in the model zone (UTC), JVM-zone-independent.
        TypeDescriptor instTd = info != null ? info.getTypeDescriptor() : null;
        if (instTd != null && CoreTypeEncoding.INSTANT.equals(instTd.getTypeEncoding())) {
            Instant inst = readInstant(rs, i, jdbcType);
            return inst == null ? null : decoder.decode(inst, info);
        }

        // Null-detect up front with getObject — it is null-safe for every storage class and sets
        // the driver's "last read column", whereas calling wasNull() after a typed getter that
        // short-circuited on NULL throws on SQLite ("column -1 out of bounds"): SQLite reports the
        // column type per row, so a NULL in an otherwise-textual column comes back typed NUMERIC and
        // getBigDecimal()/wasNull() disagree. After this guard the value is known non-null, so the
        // primitive getters below no longer need a wasNull() check.
        if (rs.getObject(i) == null) {
            return null;
        }

        Object v;

        switch (jdbcType) {

            case Types.DECIMAL:
            case Types.NUMERIC:
                v = rs.getBigDecimal(i); break;

            case Types.FLOAT:
            case Types.DOUBLE:
                v = rs.getDouble(i);break;

            case Types.INTEGER:
            case Types.BIGINT:
                v = rs.getLong(i);break;

            case Types.VARCHAR:
            case Types.CHAR:
                v = rs.getString(i);break;

            case Types.DATE:
                v = readDateColumn(rs, i); break;

            case Types.TIMESTAMP:
                // a naive TIMESTAMP is a wall-clock value: carry it as zone-neutral
                // LocalDateTime. toLocalDateTime() recovers the wall-clock regardless of
                // JVM zone (the read zone cancels); toInstant() would NOT — it pins the
                // value to the JVM default zone, making reads machine-dependent.
                Timestamp timestamp = rs.getTimestamp(i);
                v = timestamp != null ? timestamp.toLocalDateTime() : null; break;

            default:
                v = rs.getObject(i);break;
        }

        // JDBC legacy date/time types → canonical java.time
        if (v instanceof Date d)            v = d.toLocalDate();
        else if (v instanceof Time t)       v = t.toLocalTime();
        else if (v instanceof Timestamp ts) v = ts.toLocalDateTime();   // wall-clock, zone-neutral

        return decoder.decode(v, info);
    }

    /**
     * Read an {@code INSTANT}-encoded column as the absolute point in time it denotes, independent
     * of the JVM default zone. The default asks the driver for an {@link OffsetDateTime} (JDBC 4.2),
     * which is correct for the zone-aware native types — Postgres {@code timestamptz}, SQL Server
     * {@code datetimeoffset}, Oracle {@code TIMESTAMP WITH TIME ZONE}, DuckDB {@code TIMESTAMPTZ}.
     *
     * <p>Dialects whose instant is not zone-carrying override this:
     * <ul>
     *   <li>MariaDB stores it as a naive {@code TIMESTAMP} read under a UTC-pinned session — its
     *       wall-clock already <em>is</em> the UTC value, and {@code getObject(OffsetDateTime.class)}
     *       would wrongly stamp it with the JVM offset.</li>
     *   <li>SQLite stores it as text and does not support {@code getObject(OffsetDateTime.class)}.</li>
     *   <li>Trino exposes the instant with the type of whatever backend it federates, so it branches
     *       on {@code jdbcType} — hence that parameter is part of the contract.</li>
     * </ul>
     *
     * @param jdbcType the {@link java.sql.Types} the driver reports for the column
     * @return the instant, or {@code null} if the column is SQL NULL
     */
    protected Instant readInstant(ResultSet rs, int i, int jdbcType) throws SQLException {
        OffsetDateTime odt = rs.getObject(i, OffsetDateTime.class);
        return odt != null ? odt.toInstant() : null;
    }

    /** Read a DATE column as a {@link LocalDate}. Override in dialects whose JDBC driver does not
     *  support {@code getDate()} reliably for text-stored dates (e.g. SQLite). */
    protected LocalDate readDateColumn(ResultSet rs, int i) throws SQLException {
        Date date = rs.getDate(i);
        return date != null ? date.toLocalDate() : null;
    }

    /**
     * Read an INSTANT that the dialect exposes as a <em>naive</em> timestamp holding the model-zone
     * wall-clock (the session is pinned to {@link #getModelZone()}). {@code toLocalDateTime()} recovers
     * that wall-clock JVM-zone-independently; reinterpreting it at the model zone yields the instant.
     * Used by MariaDB, and by Trino when it federates a backend whose instant maps to a naive
     * {@code timestamp}.
     */
    protected final Instant naiveInstant(ResultSet rs, int i) throws SQLException {
        Timestamp ts = rs.getTimestamp(i);
        return ts != null ? ts.toLocalDateTime().atZone(modelZone).toInstant() : null;
    }


    public Connection getConnection() {
        return connection;
    }

    protected void setConnection(Connection conn) {
        this.connection = conn;
        // A swapped-in connection (e.g. a reconnect after an error) is born with the driver's default
        // session zone, so re-pin it to the model zone — otherwise zone-dependent reads (Trino
        // from_unixtime, now(), …) silently drift to the client JVM zone for the rest of the session.
        String tz = sessionTimeZoneStatement(modelZone);
        if (tz != null) {
            sessionInit(tz);
        }
    }
}
