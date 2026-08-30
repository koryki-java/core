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
package ai.koryki.iql;

import ai.koryki.catalog.types.*;
import ai.koryki.iql.functions.ConditionalReconciler;
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.SqlTemplate;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;
import ai.koryki.iql.typing.EpochEncodings;
import ai.koryki.iql.typing.InstantEncodings;
import ai.koryki.iql.typing.IntervalEncodings;
import ai.koryki.iql.typing.TimeEncodings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface SqlDialect {

    /**
     * An identifier as this dialect must read it: bare where it accepts a bare name, quoted where
     * it does not.
     *
     * <p><b>The one place the decision is made.</b> It used to be a private helper copied
     * byte-for-byte into {@code SqlQueryRenderer} and {@code SqlSelectRenderer}, and that is
     * exactly how the CTE column list came to be emitted raw: the fix was applied twice and the
     * call site that belonged to neither copy was never found. One method means a position that
     * forgets to call it is the only way left to get this wrong.
     *
     * <p><b>Conditional rather than always.</b> Quoting every name would be the tidier rule, but it
     * changes what thousands of existing assertions and goldens expect, and - on Oracle and
     * Snowflake - what the name actually resolves to. Quoting only where it is needed leaves every
     * ordinary query byte-for-byte as it was and fixes exactly the case that was broken. The vast
     * majority of rendered SQL is untouched by any of this: the catalogs hold lowercase names, and
     * all eight engines either fold an unquoted name or ignore case, so a bare lowercase name
     * resolves everywhere.
     *
     * <p><b>A quoted name is rendered unfolded</b>, and the reasoning took a wrong turn before it
     * landed here. The tempting argument is: Oracle and Snowflake fold unquoted names up, so a
     * quoted name must be written upper-cased. It is wrong, and Snowflake said so -
     * {@code Object '"KORYKI TEST UMSATZ 2026"' does not exist}. What the argument misses is that
     * folding only ever applies to a name written <em>without</em> quotes. A name that could not
     * have been written bare was created quoted and is stored exactly as typed, so rendering it
     * unchanged is the only thing that can match.
     *
     * <p>That holds outright for a name carrying a space or a special character. For one that is
     * merely mixed case it is a contract rather than a deduction - {@code CREATE TABLE Betrag}
     * parses unquoted on Oracle and stores {@code BETRAG} - and since nothing here introspects a
     * database (the catalogs are hand-maintained JSON), the renderer cannot tell the two apart. The
     * contract is stated where the value is authored: an entity's {@code table} and an attribute's
     * {@code column} carry the <em>exact stored spelling</em>.
     *
     * <p>Qualified names never arrive here as one string; the callers build them as
     * {@code renderIdentifier(alias) + "." + renderIdentifier(column)}, so
     * {@code "Umsatz 2026"."Order Date"} comes out correctly rather than as one quoted blob.
     *
     * @param mode the caller's forced form, or {@link Identifier#lowercase} for the usual path
     */
    default String renderIdentifier(Identifier mode, String name) {

        if (mode.isQuoted()) {
            // The caller asked for quoted output and chose the case with it; that choice wins.
            return quote(Identifier.bare(mode, name));
        }
        // Judged on the name as the catalog holds it, NOT on the folded form: folding to lower case
        // first would hide the very difference that makes "Betrag" need quotes. The string that
        // decides is the string that is emitted -- the two used to be derived separately, which
        // left them free to disagree the moment a dialect changed one of them.
        String bare = Identifier.bare(Identifier.quoted, name);
        if (Identifier.needsQuoting(bare) || isReserved(bare)) {
            return quote(bare);
        }
        return Identifier.normal(mode, name);
    }

    /**
     * Words this engine will not accept as a bare identifier.
     *
     * <p>A dialect property, because no two of the eight agree. The shared baseline is the SQL
     * standard's list; Oracle adds {@code date}, {@code level}, {@code number} and some forty more,
     * MariaDB adds {@code key} and {@code index}, SQL Server adds {@code key} and {@code top}. One
     * union of all of them would be wrong in the other direction - it quotes a name on the seven
     * engines that take it bare, and quoting is not free: on Oracle and Snowflake it changes which
     * column the name resolves to.
     *
     * <p>The previous single list was documented as holding "the genuinely reserved words and no
     * more", and argued against {@code date} on the grounds that it "is not reserved in any of the
     * eight engines here". Oracle reserves it.
     */
    default boolean isReserved(String name) {
        return Identifier.isStandardReserved(name);
    }

    /**
     * Wraps an identifier in this dialect's quotes.
     *
     * <p>The standard says double quotes and most engines agree, but not all: MariaDB and MySQL use
     * backticks unless {@code ANSI_QUOTES} is set, and SQL Server uses brackets - which is why this
     * is a method and not a character. A quote inside the name is escaped by doubling it, the one
     * convention they share.
     */
    default String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    /**
     * The function renderer for this dialect. The default is the shared, dialect-neutral
     * canonical set; dialects that add or replace functions override this to return their
     * own {@code static final} renderer (assembled once at class load).
     */
    default FunctionRenderer getFunctionRenderer() {
        return StandardFunctions.canonical();
    }

    /**
     * Validators for constructs this dialect cannot express — the counterpart to
     * {@link #getFunctionRenderer()} for everything that is not a function.
     *
     * <p>A dialect declares an unsupported <em>function</em> through its renderer, and
     * {@code FunctionValidator} turns that into a {@link ai.koryki.iql.validate.Violation} with
     * category {@link ai.koryki.iql.validate.Violation#UNSUPPORTED}. Language constructs have no
     * such declaration: {@code GROUP BY ROLLUP} is emitted by the shared renderer for every
     * dialect, so a dialect that lacks it fails only when the driver rejects the finished SQL —
     * with the server's message and no position in the query.
     *
     * <p>Validators returned here run in the analysis stage alongside the core ones, so the query
     * fails before execution with a located violation. They must use category
     * {@code UNSUPPORTED}: that is what marks a failure as "this dialect cannot express this
     * query" rather than "this query is wrong", and it is what lets a shared test fixture be
     * skipped for this dialect instead of failing (see
     * {@code ValidateException#isOnlyUnsupported}).
     *
     * @param context schema, alias scopes, catalog and source positions — see
     *                {@link ai.koryki.iql.validate.ValidationContext}
     * @return the dialect's validators; empty by default
     */
    default List<Collector<List<Violation>>> validators(ai.koryki.iql.validate.ValidationContext context) {
        return List.of();
    }

    /**
     * {@code TIME 'HH:mm:ss[.SSS]'} — via {@link #plainTime}, not {@code LocalTime.toString()}.
     * That form omits the seconds when they are zero ({@code TIME '12:00'}), which is a shape a
     * database is free to reject: SQL Server does, which is why {@code MssqlDialect} had to override
     * this in the first place. Four dialects still take the default, and it held only because those
     * four happen to accept it.
     */
    default String timeLiteral(LocalTime time) {
        return "TIME '" + plainTime(time) + "'";
    }

    default String dateLiteral(LocalDate date) {
        return "DATE '" + date + "'";
    }

    /** {@code TIMESTAMP 'yyyy-MM-dd HH:mm:ss[.SSS]'} — see {@link #timeLiteral} for why not toString(). */
    default String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + plainTimestamp(dateTime) + "'";
    }

    /**
     * Renders {@code instant} as this dialect's instant literal, for an {@code INSTANT} ({@code timestamptz})
     * column compared against a literal (docs/TEMPORAL.md). The default presents it as the model-zone
     * wall-clock value (correct where the session zone is pinned to the model zone, or where the INSTANT
     * column surfaces naive); dialects whose instant columns need an explicit offset (e.g. SQL Server
     * {@code DATETIMEOFFSET}) override it.
     */
    default String instantLiteral(java.time.Instant instant, java.time.ZoneId modelZone) {
        return timestampLiteral(java.time.LocalDateTime.ofInstant(instant, modelZone));
    }

    /**
     * How far this engine can represent a duration as a <em>value</em> — not as an operand of date
     * arithmetic, where no interval is ever built.
     *
     * <p>Declared rather than discovered, so the violation is positioned and appears in the
     * documentation, instead of the database rejecting the finished statement. See
     * {@link ai.koryki.iql.validate.DurationValueValidator}.
     */
    enum IntervalSupport {
        /** One interval type spanning all units — DuckDB, PostgreSQL, Snowflake. */
        FULL,
        /** {@code YEAR TO MONTH} and {@code DAY TO SECOND}, with no type spanning both — Oracle, Trino. */
        SPLIT,
        /** No interval type; {@code INTERVAL} exists only inside date arithmetic — MariaDB, SQL Server, SQLite. */
        NONE
    }

    /** @see IntervalSupport */
    default IntervalSupport intervalSupport() {
        return IntervalSupport.FULL;
    }

    /**
     * A duration's components with year, quarter and month folded into one MONTH step, the day and
     * clock components left as they are.
     *
     * <p>A duration is two quantities, not a list of units: a calendar amount in months, and a
     * day-and-clock amount. Only the first can clamp, and it clamps <em>once</em> — {@code 1y1mo}
     * is thirteen months, not a year followed by a month. The two cannot be merged because a month
     * has no fixed number of days, which is exactly the split {@code java.time} draws between
     * {@code Period} and {@code Duration}: {@code Period.of(1, 1, 0).toTotalMonths()} is 13, and
     * {@code LocalDate.of(2024, 2, 29).plus(Period.of(1, 1, 0))} is 2025-03-29 — where applying the
     * year and the month separately would clamp on February and land on 2025-03-28.
     *
     * <p>Dialects with a combined interval literal get this for free, because one interval carries
     * a single month count. Those that must chain one addition per component (MariaDB, SQL Server,
     * Trino) would otherwise clamp at every intermediate step and answer differently from the rest;
     * folding here keeps the semantics in one place instead of three.
     *
     * <p>Components arrive largest-unit-first ({@code DurationValidator} enforces it), so the
     * folded month step goes in front and the day/clock steps keep their order behind it.
     */
    static List<Duration.Component> calendarFolded(Duration duration) {
        int months = 0;
        List<Duration.Component> rest = new java.util.ArrayList<>();
        for (Duration.Component c : duration.getComponents()) {
            switch (c.unit()) {
                case YEAR    -> months += c.value() * 12;
                case QUARTAL -> months += c.value() * 3;
                case MONTH   -> months += c.value();
                default      -> rest.add(c);
            }
        }
        if (months != 0) {
            rest.add(0, new Duration.Component(months, Duration.Unit.MONTH));
        }
        return rest;
    }

    default String durationLiteral(Duration duration) {
        return duration.getComponents().stream()
                .map(c -> {
                    String unit = switch (c.unit()) {
                        case MILLISECOND -> "MILLISECOND";
                        case SECOND      -> "SECOND";
                        case MINUTE      -> "MINUTE";
                        case HOUR        -> "HOUR";
                        case DAY         -> "DAY";
                        case WEEK        -> "WEEK";
                        case MONTH       -> "MONTH";
                        case QUARTAL     -> "QUARTER";
                        case YEAR        -> "YEAR";
                    };
                    return "INTERVAL '" + c.value() + "' " + unit;
                })
                .collect(Collectors.joining(" + "));
    }

    java.time.format.DateTimeFormatter PLAIN_TIMESTAMP =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(java.util.Locale.ROOT);
    java.time.format.DateTimeFormatter PLAIN_TIMESTAMP_MS =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withLocale(java.util.Locale.ROOT);
    java.time.format.DateTimeFormatter PLAIN_TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(java.util.Locale.ROOT);
    java.time.format.DateTimeFormatter PLAIN_TIME_MS =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withLocale(java.util.Locale.ROOT);

    /**
     * {@code yyyy-MM-dd HH:mm:ss}, with {@code .SSS} appended only when the value carries
     * milliseconds — the space-separated form nearly every engine wants for a timestamp literal.
     *
     * <p>Each dialect used to hold its own {@code ofPattern("yyyy-MM-dd HH:mm:ss")}, which has no
     * fractional part, so a literal written with milliseconds lost them on the way to SQL:
     * {@code "1996-12-31 17:00:00.500"} arrived as {@code TIMESTAMP '1996-12-31 17:00:00'}, half a
     * second early and without a word. The grammar admits them ({@code TIMESTAMP_STRING} ends in an
     * optional {@code '.' DIGIT DIGIT DIGIT}) and both mappers parse them, so the value was there
     * and only the rendering dropped it.
     *
     * <p>Exactly three digits or none, matching what the notation can express — never {@code .5},
     * which the lexer could not read back. Anything finer than a millisecond is not writable and is
     * truncated here.
     */
    static String plainTimestamp(LocalDateTime dateTime) {
        return dateTime.format(dateTime.getNano() != 0 ? PLAIN_TIMESTAMP_MS : PLAIN_TIMESTAMP);
    }

    /** {@code HH:mm:ss} with {@code .SSS} only when present — see {@link #plainTimestamp}. */
    static String plainTime(LocalTime time) {
        return time.format(time.getNano() != 0 ? PLAIN_TIME_MS : PLAIN_TIME);
    }

    /**
     * One combined verbose INTERVAL literal — {@code INTERVAL '1 year 2 month 3 day'} — for
     * dialects that accept all components in a single literal (DuckDB/Postgres use {@code " "},
     * Snowflake {@code ", "}). Weeks fold to days, quarters to months.
     */
    static String combinedInterval(Duration duration, String separator) {
        return combinedInterval(duration.getComponents(), separator);
    }

    /**
     * As above, from an explicit component list — for dialects that must fold the calendar units
     * first (see {@link #calendarFolded}). Not every engine reads a combined literal as one
     * quantity: DuckDB and PostgreSQL take {@code INTERVAL '1 year 1 month'} as thirteen months,
     * but Snowflake applies its comma-separated components one after another and therefore clamps
     * in between, which is measurable — {@code 2024-02-29 + 1y1mo} came back as 2025-03-28 there
     * and as 2025-03-29 everywhere else.
     */
    static String combinedInterval(List<Duration.Component> components, String separator) {
        String parts = components.stream()
                .map(c -> switch (c.unit()) {
                    case YEAR        -> c.value() + " year";
                    case QUARTAL     -> (c.value() * 3) + " month";
                    case MONTH       -> c.value() + " month";
                    case WEEK        -> (c.value() * 7) + " day";
                    case DAY         -> c.value() + " day";
                    case HOUR        -> c.value() + " hour";
                    case MINUTE      -> c.value() + " minute";
                    case SECOND      -> c.value() + " second";
                    case MILLISECOND -> c.value() + " millisecond";
                })
                .collect(Collectors.joining(separator));
        return "INTERVAL '" + parts + "'";
    }

    /**
     * SQL standard: INTERSECT binds tighter than UNION/EXCEPT. SQLite deviates — all
     * compound operators share one precedence level, associate left, and parenthesized
     * compound operands are a syntax error — so its renderer must emit left-deep chains
     * flat instead of parenthesizing lower-precedence children.
     */
    default boolean uniformSetOperatorPrecedence() {
        return false;
    }

    /**
     * Final form of a text literal; {@code quoted} arrives already SQL-quoted ({@code 'x'}).
     * SQL Server prefixes non-ASCII literals with {@code N} — a bare {@code '...'} takes the
     * session database's (possibly single-byte) collation, which truncates or mis-counts on
     * the way into UTF-8 collated columns.
     */
    default String textLiteral(String quoted) {
        return quoted;
    }

    default String mapSetOperator(String operator) {
        return operator;
    }

    default String recursive(boolean recursive) {
        return recursive ? "RECURSIVE " : "";
    }

    /** Text inserted right after the GROUP BY keyword when ROLLUP is requested. */
    default String rollupPrefix() {
        return " ROLLUP (";
    }

    /** Text appended after the grouping columns when ROLLUP is requested. */
    default String rollupSuffix() {
        return ")";
    }

    /**
     * Renders the row-limit clause (a full line incl. indentation and line separator).
     * Default is ANSI {@code FETCH FIRST n ROWS ONLY}; {@code hasOrderBy} lets dialects
     * that require an ORDER BY for paging (e.g. T-SQL OFFSET/FETCH) react accordingly.
     */
    default String limitClause(int limit, boolean hasOrderBy, int indent) {
        return Identifier.indent(indent) + "FETCH FIRST " + limit + " ROWS ONLY" + SqlRenderer.NL;
    }

    /**
     * Renders a column type for DDL (CREATE TABLE) — the write-side dual of
     * {@link TypeDescriptorParser}.
     *
     * <p>The default echoes the parsed physical type verbatim (a faithful round-trip
     * for the same dialect). Dialects override to map a canonical {@link TypeDescriptor}
     * (family + precision/scale + encoding) onto their own physical type — e.g.
     * TEXT-&gt;VARCHAR2 (Oracle), BOOLEAN-&gt;BIT and TIMESTAMP-&gt;DATETIME2 (T-SQL),
     * or the TIME_SECONDS_FROM_MIDNIGHT encoding -&gt; INTEGER.
     */
    default String renderType(TypeDescriptor type) {
        return type.getPhysicalTypeName();
    }

    default String renderFunction(SqlSelectRenderer renderer, Function function, int indent) {
        return null;
    }

    default String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * SQL converting a wall-clock(zone) column from its declared storage zone to the model zone
     * (docs/TEMPORAL.md). Applied at <em>every</em> reference to such a column — bare, in arithmetic, in
     * a comparison — so the value is a model-zone wall-clock value before any operation. The conversion
     * must precede arithmetic: a stored wall-clock value is naive/local, so add-then-convert would be
     * wrong across a DST transition (a clock unit must not absorb the offset shift).
     *
     * <p>The default rejects: a dialect that has not wired named-zone conversion does not support
     * wall-clock(zone) storage. Wall-clock columns therefore appear only in fixtures that {@code ignore}
     * such dialects (e.g. SQLite, which has no time-zone database at all). See docs/TEMPORAL.md.
     */
    default String wallClockToModelZone(String columnSql,
                                        WallClockEncoding enc, java.time.ZoneId modelZone) {
        throw new ai.koryki.antlr.KorykiaiException(
                "wall-clock(zone) storage (" + enc.name() + ") is not supported by this dialect");
    }

    /**
     * The ANSI {@code AT TIME ZONE} two-step, for engines whose operator flips a naive timestamp to an
     * instant and back (DuckDB, PostgreSQL): read the stored value as declared-zone local, then render it
     * as model-zone local. A {@code DATE_WALLCLOCK} converts at start-of-day and is taken back to a date.
     */
    static String atTimeZoneToModelZone(String columnSql,
                                        WallClockEncoding enc, java.time.ZoneId modelZone) {
        String declared = enc.getZone().getId();
        String model = modelZone.getId();
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(((CAST(" + columnSql + " AS TIMESTAMP) AT TIME ZONE '" + declared
                    + "') AT TIME ZONE '" + model + "') AS DATE)";
        }
        return "((" + columnSql + " AT TIME ZONE '" + declared + "') AT TIME ZONE '" + model + "')";
    }

    /**
     * SQL shifting a TIMESTAMP value — read as a wall-clock in {@code fromZoneSql} — to its wall-clock in
     * {@code toZoneSql} (both already SQL-quoted, e.g. {@code 'UTC'}). Backs the explicit {@code at_zone()}
     * / {@code to_utc()} functions (docs/TEMPORAL.md). The default rejects — a dialect without named-zone
     * conversion does not support explicit zone crossing (the same engines that reject wall-clock(zone)).
     */
    default String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        throw new ai.koryki.antlr.KorykiaiException(
                "explicit zone conversion (at_zone / to_utc) is not supported by this dialect");
    }

    /** The ANSI {@code AT TIME ZONE} two-step shift, shared by DuckDB and PostgreSQL. */
    static String atTimeZoneShift(String valueSql, String fromZoneSql, String toZoneSql) {
        return "((" + valueSql + " AT TIME ZONE " + fromZoneSql + ") AT TIME ZONE " + toZoneSql + ")";
    }

    default String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        String diff = renderTemporalDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        return TimeEncodings
                .secondsArithmetic(renderer, leftSql, leftType, operator, right, indent)
                .orElseGet(() -> materializeTemporalLeft(leftSql, leftType)
                        + " " + operator + " " + parenthesizeDuration(renderer, operator, right, indent));
    }

    /**
     * Materialize an integer-encoded temporal column to a real DATE/TIMESTAMP before a duration is
     * applied: a {@code DATE_FROM_EPOCH_DAY} becomes a DATE, an {@code EPOCH:<unit>} timestamp becomes a
     * TIMESTAMP (via {@link #epochToTimestamp}). Other columns pass through unchanged. Dialects that
     * override {@link #renderEncodedArithmetic} call this (or their own equivalent) for the same reason.
     */
    default String materializeTemporalLeft(String leftSql, TypeDescriptor leftType) {
        if (leftType != null
                && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            return "(DATE '1970-01-01' + " + leftSql + ")";
        }
        return materializeEpochTimestampLeft(leftSql, leftType);
    }

    /**
     * Materialize an {@code EPOCH:<unit>} timestamp column to a TIMESTAMP via {@link #epochToTimestamp}
     * (else pass through). The dialects that override {@link #renderEncodedArithmetic} call this for the
     * epoch case — they keep their own DATE_FROM_EPOCH_DAY handling.
     */
    default String materializeEpochTimestampLeft(String leftSql, TypeDescriptor leftType) {
        if (leftType != null && leftType.getTypeEncoding() instanceof EpochTypeEncoding e) {
            return epochToTimestamp(leftSql, e.getUnit());
        }
        return leftSql;
    }

    /**
     * Inverse of {@link #timestampToEpochSeconds}: an epoch count (in {@code unit}) → a TIMESTAMP, so an
     * EPOCH-encoded column can be shifted by a duration. Default is DuckDB {@code make_timestamp}
     * (microseconds); dialects without it override.
     */
    default String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        String micros = switch (unit) {
            case MILLIS -> "(" + expr + " * 1000)";
            case MICROS -> expr;
            case NANOS  -> "(" + expr + " / 1000)";
            default     -> "(" + expr + " * 1000000)";   // SECONDS
        };
        return "make_timestamp(" + micros + ")";
    }

    /** An epoch count in {@code unit} → an integer-seconds expression — a helper for the seconds-based
     *  {@link #epochToTimestamp} overrides (whole-second epochs; sub-second is not preserved). */
    static String secondsFromEpoch(String expr, java.time.temporal.ChronoUnit unit) {
        return switch (unit) {
            case MILLIS -> "(" + expr + " / 1000)";
            case MICROS -> "(" + expr + " / 1000000)";
            case NANOS  -> "(" + expr + " / 1000000000)";
            default     -> expr;   // SECONDS
        };
    }

    /**
     * TIMESTAMP − TIMESTAMP → the exact elapsed span as integer epoch-seconds (decoded as a fixed
     * INTERVAL:SECONDS, so a span over 24h renders 50:00:00, never calendar days); {@code null} when the
     * operands are not a timestamp difference. Each side reduces to epoch-seconds per its own encoding,
     * which also reconciles mixed encodings: the same instant stored as INSTANT, EPOCH:SECONDS or
     * EPOCH:MILLIS yields the same seconds, so their difference is zero.
     *
     * <p>Shared so the semantics stay uniform across dialects: dialects that override
     * {@link #renderEncodedArithmetic} call this first, and only vary {@link #epochSeconds}.
     */
    default String renderTemporalDiff(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        if (!"-".equals(operator)) {
            return null;
        }
        if (isTimestamp(leftType) && isTimestamp(rightType)) {
            return epochSeconds(leftSql, leftType) + " - " + epochSeconds(renderer.toSql(right, indent), rightType);
        }
        if (isDate(leftType) && isDate(rightType)) {
            return dateDiffDays(materializeTemporalLeft(leftSql, leftType),
                    materializeTemporalLeft(renderer.toSql(right, indent), rightType));
        }
        return null;
    }

    /**
     * Numeric division, where two things need normalising before the engines agree.
     *
     * <p><b>Integer division.</b> {@code ReturnTypes.DECIMAL_DIVIDE} says the result is decimal, and
     * measured, {@code 7 / 2} honoured that on Oracle, MariaDB and DuckDB but truncated to {@code 3}
     * on PostgreSQL, SQL Server, Trino and SQLite. The dividend is therefore cast when <em>both</em>
     * operands are integers — an already-decimal operand needs nothing, so the cast stays out of the
     * SQL in the common case.
     *
     * <p><b>Division by zero.</b> Left alone the engines answer three different ways: an error on
     * PostgreSQL, SQL Server, Oracle and Trino, NULL on MariaDB and SQLite, and {@code Infinity} on
     * DuckDB. A {@code NULLIF(divisor, 0)} makes it NULL everywhere (measured on all seven reachable
     * engines, and it composes into SUM). The guard is skipped when the divisor is a non-zero
     * literal, where it could not fire; a literal {@code / 0} never reaches rendering because
     * {@code FunctionValidator} rejects it outright.
     */
    default String renderDivision(String leftSql, TypeDescriptor leftType,
            String rightSql, TypeDescriptor rightType, boolean guardZero) {
        String dividend = isInteger(leftType) && isInteger(rightType) ? castToDecimal(leftSql) : leftSql;
        return dividend + " / " + divisor(rightSql, guardZero);
    }

    /** The divisor of a division, guarded against zero unless it is a non-zero literal. */
    default String divisor(String rightSql, boolean guardZero) {
        return guardZero ? "NULLIF(" + rightSql + ", 0)" : rightSql;
    }

    /**
     * Cast an integer dividend so that division yields a decimal rather than truncating. The default
     * is a no-op, for the engines whose {@code /} already promotes; the four that truncate override.
     */
    default String castToDecimal(String sql) {
        return sql;
    }

    private static boolean isInteger(TypeDescriptor t) {
        return t != null && CoreTypeFamily.INTEGER.equals(t.getTypeFamily());
    }

    /**
     * DATE − DATE as a count of whole days — {@code left - right}, the one reading docs/TEMPORAL.md
     * allows ("differences come out in whole days ... never in months or years").
     *
     * <p>Plain subtraction is right on PostgreSQL, Oracle and DuckDB and wrong elsewhere, which is
     * why this is a hook rather than inline SQL. Measured over the 809 shipped northwind orders,
     * the bare operator was wrong on 238 rows on MariaDB — {@code DATE - DATE} there is numeric
     * yyyymmdd subtraction, so it agrees within a month and drifts across one — and on all 809 on
     * SQLite, where a TEXT date coerces to its leading integer and every difference collapses to 0.
     * Both failed silently; neither engine raises anything.
     */
    default String dateDiffDays(String leftSql, String rightSql) {
        return leftSql + " - " + rightSql;
    }

    private static boolean isTimestamp(TypeDescriptor t) {
        return t != null && CoreTypeFamily.TIMESTAMP.equals(t.getTypeFamily());
    }

    private static boolean isDate(TypeDescriptor t) {
        return t != null && CoreTypeFamily.DATE.equals(t.getTypeFamily());
    }

    /**
     * Reduce a TIMESTAMP-family expression to integer epoch-seconds, honoring its storage encoding so
     * that a difference is exact regardless of how each operand happens to be stored: an EPOCH:SECONDS
     * column is already seconds, EPOCH:MILLIS is divided by 1000, and an INSTANT / wall-clock TIMESTAMP
     * goes through {@code EXTRACT(EPOCH ...)}. Dialects without {@code EXTRACT(EPOCH ...)} override the
     * timestamp branch via {@link #timestampToEpochSeconds}.
     */
    default String epochSeconds(String expr, TypeDescriptor type) {
        var enc = type != null ? type.getTypeEncoding() : null;
        if (enc instanceof EpochTypeEncoding e) {
            return switch (e.getUnit()) {
                case MILLIS -> "(" + expr + " / 1000)";
                case MICROS -> "(" + expr + " / 1000000)";
                case NANOS  -> "(" + expr + " / 1000000000)";
                default     -> expr;   // SECONDS: already epoch-seconds
            };
        }
        return timestampToEpochSeconds(unwrapOuterParens(expr), isInstant(enc));
    }

    // Strip one matching outer layer of parentheses so that function-call wrappers
    // (UNIX_TIMESTAMP, EXTRACT EPOCH) don't produce double parens around already-
    // parenthesized KQL sub-expressions like (c.ts - 3h20min30s).
    private static String unwrapOuterParens(String expr) {
        if (expr.length() < 2 || expr.charAt(0) != '(') return expr;
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') depth++;
            else if (expr.charAt(i) == ')' && --depth == 0) {
                return i == expr.length() - 1 ? expr.substring(1, expr.length() - 1) : expr;
            }
        }
        return expr;
    }

    private static boolean isInstant(TypeEncoding enc) {
        return CoreTypeEncoding.INSTANT.equals(enc);
    }

    /**
     * A real timestamp/datetime expression → integer epoch-seconds. {@code instant} is {@code true} for a
     * zone-aware (INSTANT) operand and {@code false} for a wall-clock TIMESTAMP; dialects whose epoch
     * conversion differs for the two (e.g. an offset/text suffix needing normalization) use the flag.
     * Default is the SQL-standard {@code EXTRACT(EPOCH ...)} (duckdb/postgres); others override.
     */
    default String timestampToEpochSeconds(String expr, boolean instant) {
        String wrapped = expr.startsWith("(") ? expr : "(" + expr + ")";
        return "CAST(EXTRACT(EPOCH FROM " + wrapped + ") AS BIGINT)";
    }

    /**
     * Combine two numeric expressions into the {@code "a;b"} text pair that {@code calendar_distance}
     * emits (decoded to a calendar Interval in Java). Default uses ANSI {@code ||} + CAST; dialects whose
     * concatenation or integer-to-text cast differ (MySQL/T-SQL {@code CONCAT}, Oracle {@code TO_CHAR})
     * override.
     */
    default String pairText(String a, String b) {
        return "(CAST(" + a + " AS VARCHAR) || ';' || CAST(" + b + " AS VARCHAR))";
    }


    /**
     * Subtracting a multi-component duration must parenthesize it so the {@code -} negates the
     * whole amount, not just the first term ({@code d - (a + b)}, not {@code d - a + b}). A
     * single component or addition needs no parens.
     */
    private static String parenthesizeDuration(SqlSelectRenderer renderer, String operator, Expression right, int indent) {
        String sql = renderer.toSql(right, indent);
        // Only an additive sum (e.g. a dialect that renders to_days(1) + to_hours(1)) needs wrapping;
        // a single native INTERVAL literal is already atomic.
        boolean additiveSum = right.getDuration() != null && sql.contains(" + ");
        return "-".equals(operator) && additiveSum ? "(" + sql + ")" : sql;
    }

    /**
     * Render a TIME-encoded column as integer seconds-of-day, for TIME ± duration arithmetic
     * (computed in the seconds domain, then floor-mod-decoded to a LocalTime at the read boundary,
     * which is how midnight wraps). Only the {@link TimeEncodings#secondsConvertible
     * convertible} encodings reach here.
     *
     * <p>The default handles {@code TIME_FROM_INTEGER} (HHMMSS packed integer) and
     * {@code TIME_FROM_STRING} ('HH:MM:SS' text) with portable {@code CAST}/{@code MOD}/{@code EXTRACT};
     * a seconds-from-midnight column is already the seconds value. Dialects whose integer division,
     * modulo or time-cast differ (e.g. no {@code EXTRACT(EPOCH ...)}) override this hook.
     */
    default String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (enc == null) {
            return columnSql;
        }
        // One definition, shared with comparison-operand reconciliation: EncodingLattice is where
        // "what does this encoded TIME mean in seconds" lives. TIME_SECONDS_FROM_MIDNIGHT converts
        // to itself, i.e. is returned unchanged.
        return EncodingLattice.convertSql(columnSql, enc, CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT);
    }

    /**
     * A boolean expression standing alone as a predicate — {@code FILTER p.discontinued}.
     *
     * <p>Most engines accept a boolean column bare in a WHERE clause; SQL Server does not, because
     * BIT is a value type rather than a predicate. An <em>encoded</em> boolean is never bare on any
     * engine: the column physically holds 0/1 or 'Y'/'N', so the predicate is derived from the
     * encoding and is portable arithmetic or text comparison.
     */
    default String booleanPredicate(String sql, TypeDescriptor type) {
        TypeEncoding enc = type != null ? type.getTypeEncoding() : null;
        if (CoreTypeEncoding.BOOLEAN_FROM_INTEGER.equals(enc)) {
            return sql + " <> 0";
        }
        if (CoreTypeEncoding.BOOLEAN_FROM_TEXT.equals(enc)) {
            return sql + " = 'Y'";
        }
        return sql;
    }

    default String mapOperator(String op) {
        if ("ISNULL".equalsIgnoreCase(op)) return "IS NULL";
        return op;
    }

    default String renderComparison(SqlSelectRenderer renderer,
            Expression left, TypeDescriptor leftType,
            String op, List<Expression> right, int indent) {
        return renderComparison(renderer, left, leftType, op, right, indent, false);
    }

    /**
     * As {@link #renderComparison}, but {@code negated} asks for the operator's own negative form —
     * {@code IS NOT NULL}, {@code NOT IN}, {@code NOT LIKE}, {@code NOT BETWEEN} — instead of
     * wrapping the positive form in {@code NOT (…)}. Returns {@code null} when the operator has no
     * such form, leaving the caller to keep the structural negation.
     *
     * <p>Worth folding because the negative forms are the idiomatic SQL and because some optimisers
     * key anti-join and index decisions off them rather than off {@code NOT (x IN …)}. The two are
     * equivalent in three-valued logic, so this is a rendering choice, not a semantic one.
     */
    default String renderComparison(SqlSelectRenderer renderer,
            Expression left, TypeDescriptor leftType,
            String op, List<Expression> right, int indent, boolean negated) {
        String leftSql = renderer.toSql(left, indent);

        // Catalog-driven: resolve the operator by its surface text and render
        // through its (possibly dialect-overridden) template. Operands still
        // flow through operand(...) so comparison encoding reconciliation applies.
        // Argument-family constraints (e.g. LIKE requires TEXT) are enforced by
        // FunctionValidator, not here.
        // Two encoded columns compared with each other: bring both to their lossless common
        // encoding before the operator sees them (see encodingReconciliation).
        ConditionalReconciler.Result reconciled = encodingReconciliation(renderer, left, right);

        SqlTemplate template = operatorTemplate(renderer, op, right, negated);
        if (template != null) {
            List<String> operands = new ArrayList<>();
            operands.add(convert(renderer, reconciled, 0, leftSql));
            for (int i = 0; i < right.size(); i++) {
                operands.add(convert(renderer, reconciled, i + 1, operand(renderer, right.get(i), leftType, indent)));
            }
            return template.fill(operands);
        }
        if (negated) {
            return null;   // no folded form for this operator — caller keeps NOT (…)
        }
        leftSql = convert(renderer, reconciled, 0, leftSql);

        // Fallback for operators not in the catalog (e.g. a custom `op=ID` from
        // the grammar): the legacy structural rendering.
        String mappedOp = mapOperator(op);
        List<String> rightSql = new ArrayList<>(right.size());
        for (int i = 0; i < right.size(); i++) {
            rightSql.add(convert(renderer, reconciled, i + 1, operand(renderer, right.get(i), leftType, indent)));
        }
        if (SqlSelectRenderer.isInterval(op)) {
            return leftSql + " " + mappedOp + " " + rightSql.get(0) + " AND " + rightSql.get(1);
        } else if (SqlSelectRenderer.isSet(op)) {
            return leftSql + " " + mappedOp + " (" + String.join(", ", rightSql) + ")";
        } else if (right.isEmpty()) {
            return leftSql + " " + mappedOp;
        } else {
            return leftSql + " " + mappedOp + " " + String.join(" ", rightSql);
        }
    }

    /**
     * The lossless common encoding for a comparison whose operands are <em>columns stored under
     * different encodings</em> — e.g. an EPOCH:SECONDS column compared with an EPOCH:MILLIS one,
     * which meet on millis because seconds→millis is exact and the reverse is not.
     *
     * <p>Deliberately narrow. Literal operands are left alone, because encoding a literal to match
     * a column is already handled — and handled better, at the literal's own precision — by
     * {@link #renderComparisonOperand} and its dialect overrides. Applying the lattice there too
     * would convert twice. So this returns {@code null} (no conversion) unless at least two
     * operands are non-literal and carry a genuine, non-NATIVE encoding, and those encodings
     * actually differ.
     *
     * <p>Note that this is the only path that can convert the <em>left</em> operand: the per-operand
     * hook only ever sees right-hand operands, so on its own it can only coerce right into left's
     * encoding, never pick the lossless direction.
     */
    private ConditionalReconciler.Result encodingReconciliation(
            SqlSelectRenderer renderer, Expression left, List<Expression> right) {
        List<Expression> all = new ArrayList<>(right.size() + 1);
        all.add(left);
        all.addAll(right);

        List<TypeDescriptor> types = new ArrayList<>(all.size());
        int encoded = 0;
        Set<TypeEncoding> distinct = new LinkedHashSet<>();
        for (Expression e : all) {
            TypeDescriptor t = isLiteral(e) ? null : resolveOrNull(renderer, e);
            types.add(t);
            TypeEncoding enc = t == null ? null : t.getTypeEncoding();
            if (enc != null && !(enc instanceof NativeEncoding)) {
                encoded++;
                distinct.add(enc);
            }
        }
        if (encoded < 2 || distinct.size() < 2) {
            return null;                       // nothing to reconcile, or not our case
        }
        try {
            return ConditionalReconciler.reconcile(types);
        } catch (ConditionalReconciler.ReconcileException unreconcilable) {
            return null;                       // reported by FunctionValidator; render unchanged
        }
    }

    /** Operand {@code i}'s SQL, wrapped in its reconciliation conversion when there is one. */
    private static String convert(SqlSelectRenderer renderer, ConditionalReconciler.Result reconciled,
            int i, String sql) {
        return reconciled == null ? sql : ConditionalReconciler.convert(renderer, reconciled, i, sql);
    }

    /** A written-out value rather than something the schema types — see {@link #encodingReconciliation}. */
    private static boolean isLiteral(Expression e) {
        return e.getText() != null || e.getNumber() != null || e.getDuration() != null
                || e.getLocalDate() != null || e.getLocalTime() != null || e.getLocalDateTime() != null
                || e.isNull();
    }

    private static TypeDescriptor resolveOrNull(SqlSelectRenderer renderer, Expression e) {
        try {
            return renderer.resolveType(e);
        } catch (RuntimeException unresolved) {
            return null;
        }
    }

    /**
     * The template to render {@code op} with: the catalog's, or — when {@code negated} — the
     * operator's negative form. Null means "no template", which for the positive case falls back to
     * the legacy structural rendering and for the negative case means the negation cannot be folded.
     *
     * <p>Also the one place that knows a set operand may be a subquery. {@code IN} is declared as
     * {@code {0} IN ({1*})} for a value list, but a subquery operand renders with parentheses of its
     * own, so the declared pair would produce {@code IN ((SELECT …))}. A single subquery operand
     * therefore uses the paren-free variant.
     */
    private SqlTemplate operatorTemplate(SqlSelectRenderer renderer, String op,
            List<Expression> right, boolean negated) {
        boolean subquerySet = SqlSelectRenderer.isSet(op)
                && right.size() == 1 && right.get(0).getSelect() != null;
        if (negated) {
            String t = negatedOperatorTemplate(op);
            if (t == null) {
                return null;
            }
            return new SqlTemplate(subquerySet ? "{0} NOT IN {1}" : t);
        }
        if (subquerySet) {
            return new SqlTemplate("{0} IN {1}");
        }
        FunctionDefinition operator = operatorDefinition(renderer, op);
        return operator != null ? operator.getTemplate() : null;
    }

    /**
     * The negative form of an operator whose negation folds into a keyword, or null if it has none.
     * Symbol comparisons are deliberately absent: {@code NOT (a = b)} could render as {@code a <> b},
     * but that reads as a different query rather than the same one negated, and the structural form
     * is already clear.
     */
    default String negatedOperatorTemplate(String op) {
        return switch (op == null ? "" : op.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "DISTINCT" -> "{0} IS NOT DISTINCT FROM {1}";
            case "ISNULL"  -> "{0} IS NOT NULL";
            case "IN"      -> "{0} NOT IN ({1*})";
            case "LIKE"    -> "{0} NOT LIKE {1}";
            case "BETWEEN" -> "{0} NOT BETWEEN {1} AND {2}";
            default        -> null;
        };
    }

    /**
     * The operator definition for {@code op} (resolved by surface text, e.g.
     * {@code "="}, {@code "BETWEEN"}), or {@code null} for an unregistered or
     * custom operator. Only operator-fixity entries with a template qualify.
     */
    private FunctionDefinition operatorDefinition(SqlSelectRenderer renderer, String op) {
        for (FunctionDefinition d : renderer.getFunctionRenderer().overloads(op)) {
            if (d.getFixity() != Fixity.PREFIX && d.getTemplate() != null) {
                return d;
            }
        }
        return null;
    }

    /**
     * Resolves the operand's own type once and hands both sides to {@link #renderComparisonOperand}.
     * The type is a best-effort hint for encoding reconciliation, so an operand that can't be typed
     * (subquery, identity, …) passes {@code null} rather than failing the render.
     */
    private String operand(SqlSelectRenderer renderer, Expression e, TypeDescriptor leftType, int indent) {
        TypeDescriptor rightType;
        try {
            rightType = renderer.resolveType(e);
        } catch (RuntimeException unresolved) {
            rightType = null;
        }
        return renderComparisonOperand(renderer, e, leftType, rightType, indent);
    }

    /**
     * One operand of a comparison, given the types of <em>both</em> sides. The
     * ANSI default just renders the expression; dialects whose schemas may carry
     * encoded columns opt in via {@link TimeEncodings} —
     * having {@code rightType} lets them reconcile two operands stored under
     * different encodings (e.g. TIME_FROM_INTEGER vs TIME_FROM_STRING), not just
     * encode a literal to match the left.
     */
    default String renderComparisonOperand(SqlSelectRenderer renderer,
            Expression expression, TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        // INTERVAL column vs duration literal: render the duration in the column's encoding
        // (numeric count / ISO string), so both operands share one physical representation.
        java.util.Optional<String> interval =
                IntervalEncodings.durationOperand(leftType, expression);
        if (interval.isPresent()) {
            return interval.get();
        }
        // EPOCH / epoch-day column vs a date/timestamp literal: render the literal as the matching
        // integer count, so the bare (index-friendly) column compares against an integer, not a literal.
        java.util.Optional<String> epoch =
                EpochEncodings.literalOperand(leftType, expression, renderer.getModelZone());
        if (epoch.isPresent()) {
            return epoch.get();
        }
        // INSTANT (timestamptz) column vs a date/timestamp literal: render the literal as the matching
        // absolute instant (taken in the model zone), so the comparison does not lean on the engine's
        // implicit coercion of a bare naive string (which fails on SQL Server / Trino).
        java.util.Optional<java.time.Instant> instant =
                InstantEncodings.literalInstant(leftType, expression, renderer.getModelZone());
        if (instant.isPresent()) {
            return instantLiteral(instant.get(), renderer.getModelZone());
        }
        return renderer.toSql(expression, indent);
    }
}
