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
package ai.koryki.mssql.iql;

import ai.koryki.iql.SqlRenderer;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FormatMask;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.TimeEncodings;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class MssqlDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new MssqlDialect();

    private MssqlDialect() {
    }

    /** SQL Server {@code AT TIME ZONE} uses Windows zone names, not IANA; map the names we support. */
    private static final java.util.Map<String, String> WINDOWS_ZONE = java.util.Map.ofEntries(
            java.util.Map.entry("UTC", "UTC"),
            java.util.Map.entry("Etc/UTC", "UTC"),
            java.util.Map.entry("America/New_York", "Eastern Standard Time"),
            java.util.Map.entry("America/Chicago", "Central Standard Time"),
            java.util.Map.entry("America/Denver", "Mountain Standard Time"),
            java.util.Map.entry("America/Los_Angeles", "Pacific Standard Time"),
            java.util.Map.entry("Europe/London", "GMT Standard Time"),
            java.util.Map.entry("Europe/Berlin", "W. Europe Standard Time"),
            java.util.Map.entry("Europe/Paris", "Romance Standard Time"),
            java.util.Map.entry("Asia/Tokyo", "Tokyo Standard Time"),
            java.util.Map.entry("Asia/Kolkata", "India Standard Time"),
            java.util.Map.entry("Australia/Sydney", "AUS Eastern Standard Time"));

    private static String windowsZone(java.time.ZoneId zone) {
        String win = WINDOWS_ZONE.get(zone.getId());
        if (win == null) {
            throw new ai.koryki.antlr.KorykiaiException("no Windows time-zone name mapped for '"
                    + zone.getId() + "' (SQL Server AT TIME ZONE requires Windows names)");
        }
        return win;
    }

    /** Wall-clock(zone) → model zone: {@code AT TIME ZONE} (Windows names) reads naive as declared, then shifts. */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + windowsZone(enc.getZone()) + "'";
        String model = "'" + windowsZone(modelZone) + "'";
        String shifted = "(CAST(" + columnSql + " AS datetime2) AT TIME ZONE " + decl + ") AT TIME ZONE " + model;
        String target = CoreTypeFamily.DATE.equals(enc.family()) ? "DATE" : "datetime2";
        return "CAST((" + shifted + ") AS " + target + ")";
    }

    /** at_zone / to_utc: the zone args arrive SQL-quoted as IANA names; map each to its Windows name. */
    /**
     * T-SQL reserves these on top of the standard's.
     *
     * <p>{@code key}, {@code top}, {@code percent} and {@code identity} are the ones an ordinary
     * schema hits. The list is SQL Server's own reserved set, not the much wider ODBC one it also
     * documents -- those are accepted bare.
     */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "backup", "break", "browse", "bulk", "checkpoint", "clustered", "coalesce", "compute",
            "contains", "containstable", "continue", "convert", "current_timestamp", "cursor",
            "database", "dbcc", "deallocate", "declare", "delete", "deny", "disk", "distributed",
            "double", "drop", "dump", "errlvl", "escape", "exec", "execute", "exit", "external",
            "file", "fillfactor", "freetext", "freetexttable", "function", "goto", "holdlock",
            "identity", "identity_insert", "identitycol", "index", "insert", "key", "kill",
            "lineno", "load", "merge", "national", "nocheck", "nonclustered", "nullif", "of",
            "off", "offsets", "open", "opendatasource", "openquery", "openrowset", "openxml",
            "option", "percent", "pivot", "plan", "precision", "print", "proc", "procedure",
            "public", "raiserror", "read", "readtext", "reconfigure", "replication", "restore",
            "restrict", "return", "revert", "revoke", "rollback", "rowcount", "rowguidcol", "rule",
            "save", "schema", "securityaudit", "semantickeyphrasetable",
            "semanticsimilaritydetailstable", "semanticsimilaritytable", "session_user", "set",
            "setuser", "shutdown", "statistics", "system_user", "textsize", "top", "tran",
            "transaction", "trigger", "truncate", "try_convert", "tsequal", "unpivot", "update",
            "updatetext", "use", "varying", "view", "waitfor", "within", "writetext");

    @Override
    public boolean isReserved(String name) {
        return SqlDialect.super.isReserved(name)
                || RESERVED.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Brackets.
     *
     * <p>SQL Server only honours double quotes with {@code QUOTED_IDENTIFIER ON}; brackets work
     * regardless of the setting. A closing bracket inside a name is escaped by doubling it.
     */
    @Override
    public String quote(String identifier) {
        return '[' + identifier.replace("]", "]]") + ']';
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        String from = "'" + windowsZone(java.time.ZoneId.of(unquote(fromZoneSql))) + "'";
        String to = "'" + windowsZone(java.time.ZoneId.of(unquote(toZoneSql))) + "'";
        return "CAST(((CAST(" + valueSql + " AS datetime2) AT TIME ZONE " + from + ") AT TIME ZONE " + to
                + ") AS datetime2)";
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("'") && s.endsWith("'") ? s.substring(1, s.length() - 1) : s;
    }

    /**
     * Non-ASCII literals as NVARCHAR ({@code N'...'}): a bare literal materializes in the
     * session database's collation, and converting e.g. Latin1 {@code 'München'} (implicit
     * varchar(7)) into a UTF-8 collated column truncates to {@code 'Münche'}; TRANSLATE
     * similarly mis-counts UTF-8 argument lengths. ASCII literals are collation-invariant
     * and stay plain, so transpile-time literal inspection (date_trunc units, to_char
     * masks) keeps working.
     */
    @Override
    public String textLiteral(String quoted) {
        return quoted.chars().anyMatch(ch -> ch > 127) ? "N" + quoted : quoted;
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.override("quarter",   "DATEPART(quarter, {0})");
        registry.override("week",      "DATEPART(iso_week, {0})");
        // DATEPART(weekday,...) follows SET DATEFIRST; 1900-01-01 was a Monday, so this is stable.
        registry.override("dayofweek", "((DATEDIFF(day, '19000101', {0}) % 7) + 1)");
        registry.override("dayofyear", "DATEPART(dayofyear, {0})");
        registry.windowUnsupported("count_distinct");   // COUNT(DISTINCT ...) cannot be a window function here
        // Measured: T-SQL rejects both COUNT(DISTINCT (a,b)) and COUNT(DISTINCT a,b).
        registry.unsupportedVariadic("count_distinct", ai.koryki.iql.functions.catalog.AggregateFunctions.COUNT_DISTINCT_HINT);
        // T-SQL's STRING_AGG has no OVER form. The other four dialects that share this limit all
        // declared it; SQL Server was the one that did not, so its half of the restriction lived
        // only in a hand-written ignore= marker on the fixture.
        // T-SQL spells the sort as WITHIN GROUP, not inline in the aggregate.
        registry.override("string_agg", 3, "STRING_AGG({0}, {1}) WITHIN GROUP (ORDER BY {2})");
        registry.windowUnsupported("string_agg");
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR(MAX))");
        registry.override("ceil", "CEILING({*})");
        // T-SQL's ROUND demands two to three arguments; the one-argument form was invalid.
        registry.override("round", 1, "ROUND({0}, 0)");
        // T-SQL's SUBSTRING demands THREE arguments; the two-argument form produced invalid SQL
        // ("The substring function requires 3 argument(s)"), measured on string_edges. Without a
        // length it means "to the end" -- LEN(s) is always enough for that.
        registry.register(new FunctionDefinition(registry.overloads("substring").get(0)) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                var args = function.getArguments();
                String s = renderer.toSql(args.get(0), indent);
                String start = renderer.toSql(args.get(1), indent);
                String len = args.size() > 2 ? renderer.toSql(args.get(2), indent) : "LEN(" + s + ")";
                return "SUBSTRING(" + s + ", " + start + ", " + len + ")";
            }
        });
        registry.override("char_length", "LEN({*})");
        // T-SQL LOG(x) is the natural log, and LOG(x, base) takes its arguments the other way
        // round from PostgreSQL/Oracle/MariaDB/SQLite/Trino — measured, log(2, 8) returns 0.333
        // here against 3.0 everywhere else. Both spellings are corrected rather than documented.
        registry.override("ln",    "LOG({0})");
        registry.override("log",   "LOG({1}, {0})");
        // random() is documented as 0.0 <= x < 1.0 and went to every engine verbatim; measured,
        // SQL Server answered "'random' is not a recognized built-in function name". Its RAND()
        // is seeded once per statement and would hand every row the SAME value, which is not a
        // random column at all; seeding from NEWID() draws once per row.
        registry.override("random", "RAND(CHECKSUM(NEWID()))");
        registry.override("trunc", 1, "ROUND({0}, 0, 1)");
        registry.override("trunc", 2, "ROUND({0}, {1}, 1)");
        // complete days inside the interval (24-hour periods), not midnights crossed — measured, the two readings split the engines five to three. DATEDIFF_BIG, not DATEDIFF: a span in seconds overflows a 32-bit count after 68 years.
        registry.override("days_between", "(DATEDIFF_BIG(second, {0}, {1}) / 86400)");
        registry.override("months_between", "(DATEDIFF(month, {0}, {1}) - CASE WHEN DAY({1}) < DAY({0}) THEN 1 ELSE 0 END)");
        registry.override("month_end",   "EOMONTH({0})");
        registry.override("week_begin",  "DATEADD(day, -(DATEDIFF(day, 0, {0}) % 7), CAST({0} AS DATE))");
        registry.override("week_end",    "DATEADD(day, 6 - (DATEDIFF(day, 0, {0}) % 7), CAST({0} AS DATE))");
        registry.override("quarter_end", "EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, {0}), 0), 2)");
        registry.override("year_end",    "DATEFROMPARTS(YEAR({0}), 12, 31)");

        // string functions (T-SQL spellings)
        // btrim(s) and btrim(s, characters) need different forms; an optional argument is ONE
        // definition with an arity range, and an arity-specific template would replace both. T-SQL
        // puts the character set before the FROM. Before, the second argument was silently dropped.
        for (String name : java.util.List.of("trim")) {
            registry.register(new FunctionDefinition(registry.overloads(name).get(0)) {
                @Override
                protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                    var args = function.getArguments();
                    String s = renderer.toSql(args.get(0), indent);
                    if (args.size() == 1) {
                        return "TRIM(" + s + ")";
                    }
                    return "TRIM(" + renderer.toSql(args.get(1), indent) + " FROM " + s + ")";
                }
            });
        }
        registry.override("chr",          "CHAR({0})");
        registry.override("repeat",       "REPLICATE({0}, {1})");
        // '+' with an explicit text CAST: CONCAT would swallow a NULL value (must yield NULL),
        // and bare '+' on a numeric operand would add instead of concatenate. An over-long
        // input truncates to its FIRST n chars (PostgreSQL lpad/rpad semantics), so lpad
        // needs the CASE — its RIGHT() would otherwise keep the tail.
        registry.override("lpad",
                "CASE WHEN LEN({0}) >= {1} THEN LEFT({0}, {1}) "
                + "ELSE RIGHT(REPLICATE({2}, {1}) + CAST({0} AS VARCHAR(MAX)), {1}) END");
        registry.override("rpad",         "LEFT(CAST({0} AS VARCHAR(MAX)) + REPLICATE({2}, {1}), {1})");
        registry.override("md5",          "LOWER(CONVERT(VARCHAR(32), HASHBYTES('MD5', {0}), 2))");
        registry.override("overlay",      "STUFF({0}, {2}, {3}, {1})");
        registry.override("position",     "CHARINDEX({0}, {1})");   // position(substring, string)
        // T-SQL has no boolean values in the select list: value context renders a BIT via
        // CASE, predicate context (WHERE/HAVING/ON) the bare comparison. Copy-based overlay
        // keeps the canonical signature and documentation.
        registry.register(new FunctionDefinition(registry.overloads("starts_with").get(0)) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "CAST(CASE WHEN " + predicateSql(renderer, function, indent) + " THEN 1 ELSE 0 END AS BIT)";
            }

            @Override
            public String renderPredicate(SqlSelectRenderer renderer, Function function, int indent) {
                return predicateSql(renderer, function, indent);
            }

            private String predicateSql(SqlSelectRenderer renderer, Function function, int indent) {
                String s = renderer.toSql(function.getArguments().get(0), indent);
                String p = renderer.toSql(function.getArguments().get(1), indent);
                return "(LEFT(" + s + ", LEN(" + p + ")) = " + p + ")";
            }
        });
        // int→hex without the CLR-backed FORMAT() (absent on Azure SQL Edge):
        // VARBINARY hex dump with the leading zeros trimmed
        registry.override("to_hex",
                "UPPER(CASE WHEN {0} = 0 THEN '0' ELSE SUBSTRING("
                + "CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST({0} AS BIGINT)), 2), "
                + "PATINDEX('%[^0]%', CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST({0} AS BIGINT)), 2)), 16) END)");
        registry.override("octet_length", "DATALENGTH({0})");
        registry.override("bit_length",   "(DATALENGTH({0}) * 8)");
        // NULLIF matches the divide-by-zero guard; the parens around {1} matter too — the old
        // "({0} % {1})" rendered mod(a, b - c) as (a % b - c), i.e. ((a % b) - c).
        // Parenthesise both operands, not only the right one: mod(a - 8, 7) became
        // (a - 8 % NULLIF((7),0)), and T-SQL computes that as a - (8 % 7). Measured 10247 instead
        // of 6. SqlTemplate never parenthesises by itself, so the template has to.
        registry.override("mod",          "(({0}) % NULLIF(({1}), 0))");
        registry.override("power",        "POWER(CAST({0} AS FLOAT), {1})");   // T-SQL POWER keeps the 1st arg's type: DECIMAL input truncates

        // numeric / temporal casts — T-SQL type names ("timestamp" is rowversion; no BOOLEAN/DOUBLE)
        // All three widths need the wrapper, not only INTEGER: T-SQL's bare CAST truncates
        // (1.7 -> 1) while the other dialects round. Measured on to_integer_rounding.
        registry.override("to_integer",   "CAST(ROUND({0}, 0) AS INT)");   // bare CAST truncates
        registry.override("to_bigint",    "CAST(ROUND({0}, 0) AS BIGINT)");
        registry.override("to_smallint",  "CAST(ROUND({0}, 0) AS SMALLINT)");
        // T-SQL has no CURRENT_DATE; today() produced invalid SQL.
        registry.override("today",        "CAST(GETDATE() AS DATE)");
        registry.override("to_boolean",   "CAST({0} AS BIT)");
        registry.override("to_double",    "CAST({0} AS FLOAT)");
        registry.override("to_timestamp", "CAST({0} AS DATETIME2)");

        // datetime parts & construction
        registry.override("hour",   "DATEPART(HOUR, {0})");
        registry.override("minute", "DATEPART(MINUTE, {0})");
        registry.override("second", "DATEPART(SECOND, {0})");
        registry.override("make_date",      "DATEFROMPARTS({0}, {1}, {2})");
        registry.override("make_time",      "TIMEFROMPARTS({0}, {1}, {2}, 0, 0)");
        registry.override("make_timestamp", "DATETIME2FROMPARTS({0}, {1}, {2}, {3}, {4}, {5}, 0, 0)");
        registry.override("day_add",   "DATEADD(DAY, {1}, {0})");
        registry.override("month_add", "DATEADD(MONTH, {1}, {0})");
        registry.override("year_add",  "DATEADD(YEAR, {1}, {0})");
        // DATEDIFF(YEAR) counts boundary crossings, not full years — subtract the partial year
        registry.override("years_between",
                "(DATEDIFF(YEAR, {0}, {1}) - CASE WHEN DATEADD(YEAR, DATEDIFF(YEAR, {0}, {1}), {0}) > {1} THEN 1 ELSE 0 END)");

        // period boundaries — the canonical *_begin templates emit a literal date_trunc(...) call,
        // so they need their own T-SQL rendering (classic DATEADD/DATEDIFF; DATETRUNC needs 2022+)
        registry.override("minute_begin",  "DATEADD(MINUTE, DATEDIFF(MINUTE, 0, {0}), 0)");
        registry.override("hour_begin",    "DATEADD(HOUR, DATEDIFF(HOUR, 0, {0}), 0)");
        registry.override("day_begin",     "CAST(CAST({0} AS DATE) AS DATETIME2)");
        registry.override("month_begin",   "DATEADD(MONTH, DATEDIFF(MONTH, 0, {0}), 0)");
        registry.override("quarter_begin", "DATEADD(QUARTER, DATEDIFF(QUARTER, 0, {0}), 0)");
        registry.override("year_begin",    "DATEADD(YEAR, DATEDIFF(YEAR, 0, {0}), 0)");

        // date_trunc itself (invoked directly)
        registry.register(new FunctionDefinition("date_trunc", ReturnTypes.ARG1) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String unit = renderer.toSql(function.getArguments().get(0), indent).replace("'", "");
                String d = renderer.toSql(function.getArguments().get(1), indent);
                return switch (unit) {
                    // DATEDIFF returns int, so the anchor matters: minutes since 1900 is ~66 M
                    // in 2026 and fits, seconds would not -- which is why there is no second here.
                    case "minute"  -> "DATEADD(MINUTE, DATEDIFF(MINUTE, 0, " + d + "), 0)";
                    case "hour"    -> "DATEADD(HOUR, DATEDIFF(HOUR, 0, " + d + "), 0)";
                    case "day"     -> "CAST(CAST(" + d + " AS DATE) AS DATETIME2)";
                    case "month"   -> "DATEADD(MONTH, DATEDIFF(MONTH, 0, " + d + "), 0)";
                    case "quarter" -> "DATEADD(QUARTER, DATEDIFF(QUARTER, 0, " + d + "), 0)";
                    case "year"    -> "DATEADD(YEAR, DATEDIFF(YEAR, 0, " + d + "), 0)";
                    default -> throw new UnsupportedOperationException(
                            "date_trunc unit not supported by SQL Server: " + unit);
                };
            }
        }.args(FunctionArg.arg("unit", CoreTypeFamily.TEXT), FunctionArg.arg("value", Families.TEMPORAL)));

        // to_char compiled into CONCAT of DATEPART/DATENAME pieces — pure T-SQL; the natural
        // FORMAT(value, mask) is CLR-backed and unavailable on Azure SQL Edge. The mask must
        // be a literal (it is compiled at transpile time).
        registry.register(new FunctionDefinition("to_char", ReturnTypes.TEXT) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String v = renderer.toSql(function.getArguments().get(0), indent);
                String mask = renderer.toSql(function.getArguments().get(1), indent);
                return compileMask(v, mask);
            }
        }.args(FunctionArg.arg("value", Families.ANY), FunctionArg.arg("format", CoreTypeFamily.TEXT)));

        // STRING_SPLIT's ordinal parameter needs SQL Server 2022 / compat level 130+
        registry.override("split_part",
                "(SELECT value FROM STRING_SPLIT({0}, {1}, 1) WHERE ordinal = {2})");

        // SQL Server (pre-2025) has no regular expressions; no initcap or format-mask to_number
        for (String fn : java.util.List.of("regexp_like", "regexp_count", "regexp_replace",
                "regexp_substr", "initcap", "to_number",
                // T-SQL PARSE/CONVERT take a culture or a numeric style code, not a format mask
                "parse_date", "parse_time", "parse_timestamp")) {
            registry.unsupported(fn);
        }
        return registry;
    }

    /**
     * Compile a literal format mask into a CONCAT of T-SQL date-part expressions. Azure SQL Edge has
     * no {@code FORMAT}, so the mask cannot be handed to the engine as a format string — but the scan
     * itself (longest match over the canonical tokens, quoted literals verbatim) is
     * {@link FormatMask#scan}, shared with every dialect that merely translates.
     */
    private static String compileMask(String v, String rendered) {
        if (!FormatMask.isLiteral(rendered)) {
            throw new ai.koryki.antlr.KorykiaiException(
                    "to_char format must be a string literal on SQL Server (compiled at transpile time)");
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        FormatMask.scan(FormatMask.body(rendered), FormatMask.TOKENS,
                token -> parts.add(maskTokenSql(token, v)),
                literal -> parts.add("'" + literal.replace("'", "''") + "'"));
        if (parts.isEmpty()) {
            return "''";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return "CONCAT(" + String.join(", ", parts) + ")";
    }

    // note: DATENAME(MONTH/WEEKDAY) name tokens follow the session language
    private static String maskTokenSql(String token, String v) {
        return switch (token) {
            case "YYYY"       -> "CAST(YEAR(" + v + ") AS VARCHAR(4))";
            case "YY"         -> "RIGHT(CAST(YEAR(" + v + ") AS VARCHAR(4)), 2)";
            case "MM"         -> pad2("MONTH(" + v + ")");
            case "DD"         -> pad2("DAY(" + v + ")");
            case "HH24"       -> pad2("DATEPART(HOUR, " + v + ")");
            case "HH12", "HH" -> pad2("((DATEPART(HOUR, " + v + ") + 11) % 12 + 1)");
            case "MI"         -> pad2("DATEPART(MINUTE, " + v + ")");
            case "SS"         -> pad2("DATEPART(SECOND, " + v + ")");
            case "MON"        -> "LEFT(DATENAME(MONTH, " + v + "), 3)";
            case "MONTH"      -> "DATENAME(MONTH, " + v + ")";
            case "DAY"        -> "DATENAME(WEEKDAY, " + v + ")";
            case "DY"         -> "LEFT(DATENAME(WEEKDAY, " + v + "), 3)";
            case "AM", "PM"   -> "CASE WHEN DATEPART(HOUR, " + v + ") < 12 THEN 'AM' ELSE 'PM' END";
            default -> throw new IllegalStateException(token);
        };
    }

    private static String pad2(String numSql) {
        return "RIGHT('0' + CAST(" + numSql + " AS VARCHAR(2)), 2)";
    }

    // T-SQL has no ANSI typed literals (DATE '...', TIME '...', TIMESTAMP '...').
    // Emit explicit CASTs of ISO-8601 strings, which T-SQL parses unambiguously.
    @Override
    public String dateLiteral(LocalDate date) {
        return "CAST('" + date + "' AS DATE)";
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "CAST('" + time + "' AS TIME)";
    }


    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        // T separator as before; only the fraction is new (SqlDialect.plainTimestamp).
        return "CAST('" + SqlDialect.plainTimestamp(dateTime).replace(' ', 'T') + "' AS DATETIME2)";
    }

    private static final java.time.format.DateTimeFormatter DATETIMEOFFSET_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx");

    /** SQL Server INSTANT columns are {@code DATETIMEOFFSET}; render the literal with an explicit offset. */
    @Override
    public String instantLiteral(java.time.Instant instant, java.time.ZoneId modelZone) {
        return "CAST('" + instant.atZone(modelZone).format(DATETIMEOFFSET_FMT) + "' AS datetimeoffset)";
    }

    // T-SQL has no standalone FETCH FIRST. Paging is OFFSET/FETCH, which requires an
    // ORDER BY — synthesize a dummy one (ORDER BY (SELECT NULL)) when the query has none.
    @Override
    public String limitClause(int limit, boolean hasOrderBy, int indent) {
        StringBuilder b = new StringBuilder();
        if (!hasOrderBy) {
            b.append(Identifier.indent(indent)).append("ORDER BY (SELECT NULL)").append(SqlRenderer.NL);
        }
        b.append(Identifier.indent(indent))
                .append("OFFSET 0 ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
                .append(SqlRenderer.NL);
        return b.toString();
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * T-SQL date/time arithmetic. SQL Server has no INTERVAL literal, so each duration component is
     * a nested {@code DATEADD(unit, ±n, base)} (DATEADD clamps month-end like the model and promotes
     * a DATE to DATETIME once a clock unit is added). A DATE_FROM_EPOCH_DAY column becomes its date
     * via {@code DATEADD(DAY, <col>, CAST('1970-01-01' AS DATE))}.
     */
    /** T-SQL has no date subtraction operator at all. */
    @Override
    public String dateDiffDays(String leftSql, String rightSql) {
        return "DATEDIFF(DAY, " + rightSql + ", " + leftSql + ")";
    }

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTemporalDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        java.util.Optional<String> time = TimeEncodings.secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        String base = leftSql;
        if (leftType != null && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            base = "DATEADD(DAY, " + leftSql + ", " + dateLiteral(LocalDate.of(1970, 1, 1)) + ")";
        }
        base = materializeEpochTimestampLeft(base, leftType);
        Duration dur = right.getDuration();
        if (dur != null) {
            // SQL Server refuses a clock-unit DATEADD on a DATE; promote the date base to DATETIME2
            // first when the duration carries any clock component.
            boolean date = leftType != null && CoreTypeFamily.DATE.equals(leftType.getTypeFamily());
            boolean hasClock = dur.getComponents().stream().anyMatch(c -> switch (c.unit()) {
                case HOUR, MINUTE, SECOND, MILLISECOND -> true;
                default -> false;
            });
            if (date && hasClock) {
                base = "CAST(" + base + " AS DATETIME2)";
            }
            boolean minus = "-".equals(operator);
            String expr = base;
            // Calendar units are one step, not one per unit -- see SqlDialect.calendarFolded.
            for (Duration.Component c : SqlDialect.calendarFolded(dur)) {
                long n = minus ? -c.value() : c.value();
                expr = "DATEADD(" + dateaddUnit(c) + ", " + n + ", " + expr + ")";
            }
            return expr;
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    /**
     * T-SQL has no {@code EXTRACT(EPOCH ...)}; {@code DATEDIFF_BIG(SECOND, '1970-01-01', ...)} gives
     * epoch-seconds. An INSTANT is a DATETIMEOFFSET — normalize it to a UTC wall-clock DATETIME2 first
     * so the epoch is the true instant regardless of the stored offset.
     */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        // SWITCHOFFSET(.., 0) normalizes a DATETIMEOFFSET to UTC (preserving the instant) before the
        // CAST drops the offset — built-in, unlike AT TIME ZONE which needs CLR on some instances.
        String dt = instant ? "CAST(SWITCHOFFSET(" + expr + ", 0) AS DATETIME2)" : expr;
        return "DATEDIFF_BIG(SECOND, '1970-01-01', " + dt + ")";
    }

    /** T-SQL has no {@code make_timestamp}; {@code DATEADD(SECOND, seconds, '1970-01-01')} → DATETIME (UTC). */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "DATEADD(SECOND, " + SqlDialect.secondsFromEpoch(expr, unit) + ", '1970-01-01')";
    }

    /** T-SQL uses {@code CONCAT} ({@code ||} is not concatenation); it casts the numbers to text implicitly. */
    @Override
    public String pairText(String a, String b) {
        return "CONCAT(" + a + ", ';', " + b + ")";
    }

    /** Map a duration component to its T-SQL DATEADD datepart keyword. */
    private static String dateaddUnit(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR        -> "YEAR";
            case QUARTAL     -> "QUARTER";
            case MONTH       -> "MONTH";
            case WEEK        -> "WEEK";
            case DAY         -> "DAY";
            case HOUR        -> "HOUR";
            case MINUTE      -> "MINUTE";
            case SECOND      -> "SECOND";
            case MILLISECOND -> "MILLISECOND";
        };
    }


    /**
     * SQL Server has no {@code MOD} function (the operator is {@code %}) and no
     * {@code EXTRACT(EPOCH …)}; {@code DATEDIFF} against midnight gives the seconds of a TIME.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)) {
            return "CAST(FLOOR(" + columnSql + " / 10000.0) AS INT) * 3600"
                 + " + (CAST(FLOOR(" + columnSql + " / 100.0) AS INT) % 100) * 60"
                 + " + (" + columnSql + " % 100)";
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            return "DATEDIFF(SECOND, '00:00:00', CAST(" + columnSql + " AS TIME))";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }


    /**
     * BIT is a value, not a predicate: T-SQL rejects {@code WHERE p.discontinued}. Encoded booleans
     * already come back as a comparison from the default, so only the native case needs the {@code = 1}.
     */
    @Override
    public String booleanPredicate(String sql, TypeDescriptor type) {
        String base = SqlDialect.super.booleanPredicate(sql, type);
        return base.equals(sql) ? sql + " = 1" : base;
    }


    /** T-SQL division truncates when both operands are integers. */
    @Override
    public String castToDecimal(String sql) {
        return "CAST(" + sql + " AS float)";
    }


    /** SQL Server has no interval type; spans live in DATEADD/DATEDIFF. */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.NONE;
    }
}
