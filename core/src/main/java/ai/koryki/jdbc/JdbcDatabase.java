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

import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Consumer;

public class JdbcDatabase<C extends ResultProcessor<?>> implements Database<C> {

    private String name;
    private Connection connection;

    public JdbcDatabase(String name, Connection conn) {
        this.name = name;
        this.connection = conn;
    }

    public DatabaseMetaData getMetadata() throws SQLException {
        return connection.getMetaData();
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
            throw new RuntimeException(e);
        }
    }

    public void execute(PreparedStatement statement, C processor) {

        try (ResultSet r = statement.executeQuery()) {

            ResultSetMetaData meta = r.getMetaData();
            processor.metadata(meta);
            while (r.next()) {

                List<Object> row = new ArrayList<>();


                for (int i = 0; i < meta.getColumnCount(); i++) {

                    row.add(read(r, i + 1, meta.getColumnType(i + 1)));
                }

                if (!processor.append(row)) {
                    break;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    ZoneId ZONE = ZoneId.of("UTC");
    Calendar CAL = Calendar.getInstance(TimeZone.getTimeZone(ZONE));


    static Object read(ResultSet rs, int i, int type) throws SQLException {

        Object v;

        switch (type) {

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
                v = rs.getDate(i).toLocalDate();break;

            case Types.TIMESTAMP:
                v = rs.getTimestamp(i).toInstant();break;

            default:
                v = rs.getObject(i);break;
        }

        if (rs.wasNull()) {
            return null;
        }

        // JDBC legacy date/time types → java.time
        if (v instanceof Date) {
            return ((Date)v).toLocalDate();
        }

        if (v instanceof Time) {
            return ((Time)v).toLocalTime();
        }

        if (v instanceof Timestamp) {
            return ((Timestamp)v).toInstant();   // neutral gegenüber Zeitzonen
        }

        // bereits moderne Typen (einige Treiber liefern die direkt)
        if (v instanceof LocalDate ||
                v instanceof LocalTime ||
                v instanceof LocalDateTime ||
                v instanceof OffsetDateTime ||
                v instanceof Instant) {
            return v;
        }

        return v;
    }


    public Connection getConnection() {
        return connection;
    }
}
