package ai.koryki.tools;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.mariadb.iql.MariadbDialect;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.sqlite.iql.SqliteDialect;
import ai.koryki.trino.iql.TrinoDialect;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Names SQL cannot take bare, rendered for every dialect.
 *
 * <p>The corpus cannot cover this. Its 2726 fixtures all query northwind, whose names are plain
 * lowercase words, so the quoting branch never runs there - which is exactly the objection to
 * quoting only where it is needed. This test is the answer to it: a catalog built in memory whose
 * table is called {@code Umsatz 2026}, whose columns are {@code Order Date} and {@code order}, and
 * an assertion per dialect on what comes out.
 *
 * <p>In memory rather than as fixtures because the alternative is DDL for eight engines and eight
 * more golden trees, to assert something that needs no database at all: whether the renderer writes
 * a quote, and which one.
 *
 * <p>The three names are not invented. A CSV is named after its file and file names have spaces; a
 * spreadsheet header is prose; and {@code order} is what a column of order numbers gets called by
 * everyone who has never had to quote one.
 */
class HostileIdentifierTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    /** Every dialect, with the quote characters it is supposed to write. */
    private static final Map<String, SqlDialect> DIALECTS = Map.of(
            "duckdb", DuckdbBaseDialect.INSTANCE,
            "sqlite", SqliteDialect.INSTANCE,
            "postgresql", PostgreSqlDialect.INSTANCE,
            "trino", TrinoDialect.INSTANCE,
            "oracle", OracleDialect.INSTANCE,
            "snowflake", SnowflakeDialect.INSTANCE,
            "mariadb", MariadbDialect.INSTANCE,
            "mssql", MssqlDialect.INSTANCE);

    /**
     * A catalog whose physical names are all things SQL refuses bare.
     *
     * <p>The model names beside them are what KQL addresses - lowercase, no spaces - which is the
     * split that makes the query writable at all.
     */
    private static LinkResolver hostileCatalog() {

        Column orderDate = column("Order Date", "DATE", "DATE");
        Column order = column("order", "TEXT", "VARCHAR");
        Column betrag = column("Betrag €", "DECIMAL", "DECIMAL(10,2)");

        Table table = new Table("Umsatz 2026");
        table.setColumns(new ArrayList<>(List.of(orderDate, order, betrag)));

        Schema schema = new Schema("hostile");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());

        Entity entity = new Entity("umsatz_2026");
        entity.setTable("Umsatz 2026");
        entity.setAttributes(new ArrayList<>(List.of(
                attribute("order_date", "Order Date"),
                attribute("bestellnummer", "order"),
                attribute("betrag", "Betrag €"))));

        Model model = new Model("hostile");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());

        return new LinkResolver(Locale.ENGLISH, schema, model, true);
    }

    /**
     * A one-table catalog: {@code table} physically, exposing each {@code model -> physical} pair.
     *
     * <p>The cases below differ only in which name they make hostile, so they share this rather
     * than each copying the six lines of schema assembly.
     */
    private static LinkResolver catalog(String table, java.util.LinkedHashMap<String, String> columns) {

        Table t = new Table(table);
        t.setColumns(new ArrayList<>(columns.values().stream()
                .map(c -> column(c, "TEXT", "VARCHAR")).toList()));

        Schema schema = new Schema("one");
        schema.setTables(new ArrayList<>(List.of(t)));
        schema.setRelations(new ArrayList<>());

        Entity entity = new Entity("t");
        entity.setTable(table);
        entity.setAttributes(new ArrayList<>(columns.entrySet().stream()
                .map(e -> attribute(e.getKey(), e.getValue())).toList()));

        Model model = new Model("one");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());

        return new LinkResolver(Locale.ENGLISH, schema, model, true);
    }

    private static String sql(SqlDialect dialect, LinkResolver resolver, String kql) {
        try {
            return KQLTranspiler
                    .builder(new ByteArrayInputStream(kql.getBytes(StandardCharsets.UTF_8)), resolver)
                    .build()
                    .getSql(new SqlQueryRenderer(dialect, UTC));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static java.util.LinkedHashMap<String, String> columns(String... pairs) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static Column column(String name, String family, String dialectType) {
        Column c = new Column(name);
        c.setTypeFamily(family);
        c.setDialectType(dialectType);
        return c;
    }

    private static Attribute attribute(String name, String column) {
        Attribute a = new Attribute(name);
        a.setColumn(column);
        return a;
    }

    /** Unchecked on purpose: the lambdas below iterate the dialects and cannot declare IOException. */
    private static String sql(SqlDialect dialect, String kql) {
        try {
            KQLTranspiler transpiler = KQLTranspiler
                    .builder(new ByteArrayInputStream(kql.getBytes(StandardCharsets.UTF_8)), hostileCatalog())
                    .build();
            return transpiler.getSql(new SqlQueryRenderer(dialect, UTC));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void aNameWithASpaceIsQuotedOnEveryDialect() {
        String kql = "FIND umsatz_2026 u FETCH u.order_date, u.betrag";

        DIALECTS.forEach((name, dialect) -> {
            String sql = sql(dialect, kql);
            String open = switch (name) {
                case "mariadb" -> "`";
                case "mssql" -> "[";
                default -> "\"";
            };
            String close = switch (name) {
                case "mariadb" -> "`";
                case "mssql" -> "]";
                default -> "\"";
            };
            // Verbatim on every dialect. The tempting rule - "Oracle and Snowflake fold up, so
            // write it upper-cased" - is wrong, and Snowflake rejected it in
            // HostileNameSnowflakeTest: folding applies to names written WITHOUT quotes, and a
            // name that needs quotes was never written without them.
            String table = "Umsatz 2026";

            assertTrue(sql.contains(open + table + close),
                    name + " should write " + open + table + close + " but rendered:\n" + sql);
            // The failure this guards against: a bare name with a space is a syntax error, and the
            // message the database returns names the second word, not the column.
            assertFalse(sql.matches("(?s).*\\bFROM Umsatz 2026\\b.*"), name + " left the table bare:\n" + sql);
        });
    }

    @Test
    void aKeywordIsQuotedAlthoughItLooksLikeAnOrdinaryName() {
        // The case that would otherwise reach production: `order` passes every pattern check
        // there is. Nothing about it looks like it needs quoting, and it stops the parser.
        String kql = "FIND umsatz_2026 u FETCH u.bestellnummer";

        DIALECTS.forEach((name, dialect) -> {
            String sql = sql(dialect, kql);
            String quoted = switch (name) {
                case "mariadb" -> "`order`";
                case "mssql" -> "[order]";
                default -> "\"order\"";
            };
            assertTrue(sql.contains(quoted),
                    name + " should write " + quoted + " but rendered:\n" + sql);
        });
    }

    @Test
    void eachPartOfAQualifiedNameIsJudgedOnItsOwn() {
        // u."order date": the alias needs no quotes and gets none, the column does and gets them.
        // What must never appear is "u.order date" - one identifier containing a dot, which
        // resolves to nothing and produces a column-not-found rather than a syntax error.
        String sql = sql(PostgreSqlDialect.INSTANCE, "FIND umsatz_2026 u FETCH u.order_date");

        assertTrue(sql.contains("u.\"Order Date\""), sql);
        assertFalse(sql.contains("\"u.Order Date\""), sql);
        assertFalse(sql.contains("\"u\""), "an alias that needs no quoting should not get any:\n" + sql);
    }

    @Test
    void anOrdinaryNameIsLeftAlone() throws java.io.IOException {
        // The other half of the promise, and the reason 2725 of 2726 goldens did not move: quoting
        // happens where it is needed and nowhere else.
        Column plain = column("betrag", "DECIMAL", "DECIMAL(10,2)");
        Table table = new Table("umsatz");
        table.setColumns(new ArrayList<>(List.of(plain)));
        Schema schema = new Schema("plain");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());

        Entity entity = new Entity("umsatz");
        entity.setTable("umsatz");
        entity.setAttributes(new ArrayList<>(List.of(attribute("betrag", "betrag"))));
        Model model = new Model("plain");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());

        KQLTranspiler transpiler = KQLTranspiler.builder(
                        new ByteArrayInputStream("FIND umsatz u FETCH u.betrag".getBytes(StandardCharsets.UTF_8)),
                        new LinkResolver(Locale.ENGLISH, schema, model, true))
                .build();

        DIALECTS.forEach((name, dialect) -> {
            String sql = transpiler.getSql(new SqlQueryRenderer(dialect, UTC));
            assertFalse(sql.contains("\"") || sql.contains("`") || sql.contains("["),
                    name + " quoted a name that needed none:\n" + sql);
        });
    }

    /**
     * A block's column list is quoted like every other identifier position.
     *
     * <p>The regression this exists for: {@code WITH b (...)} was the one position that never
     * reached the quoting helper, so it emitted whatever the catalog held. Here the block projects
     * a field with no label of its own, which makes its header the <em>physical</em> column - so
     * the list gets {@code order}, a word that stops the parser, while every reference to it
     * inside the block body was already being quoted correctly. That mismatch inside a single
     * statement is what made it hard to see.
     */
    @Test
    void aBlockColumnListIsQuotedLikeEveryOtherPosition() {
        String kql = """
                WITH b AS (
                 FIND umsatz_2026 u FETCH u.bestellnummer
                )
                FIND b x FETCH x.bestellnummer""";

        DIALECTS.forEach((name, dialect) -> {
            String sql = sql(dialect, hostileCatalog(), kql);
            String quoted = switch (name) {
                case "mariadb" -> "`order`";
                case "mssql" -> "[order]";
                default -> "\"order\"";
            };
            assertTrue(sql.contains("(" + quoted + ")"),
                    name + " left the block column list bare - expected (" + quoted + "):\n" + sql);
            assertFalse(sql.matches("(?s).*\\(\\s*order\\s*\\).*"),
                    name + " wrote a bare reserved word in the block column list:\n" + sql);
        });
    }

    /**
     * An output alias is quoted when it is a keyword.
     *
     * <p>Asserted here rather than left to the one golden that happens to cover it
     * ({@code duration_display.sql}, whose {@code AS "full"} moved when quoting arrived). A golden
     * records what the renderer did; this records what it is supposed to do.
     */
    @Test
    void anOutputAliasThatIsAKeywordIsQuoted() {
        LinkResolver r = catalog("umsatz", columns("betrag", "betrag"));

        DIALECTS.forEach((name, dialect) -> {
            String sql = sql(dialect, r, "FIND t x FETCH x.betrag full");
            String quoted = switch (name) {
                case "mariadb" -> "`full`";
                case "mssql" -> "[full]";
                default -> "\"full\"";
            };
            assertTrue(sql.contains("AS " + quoted),
                    name + " should write AS " + quoted + " but rendered:\n" + sql);
        });
    }

    /** GROUP BY and ORDER BY reach the column through the same path the SELECT list does. */
    @Test
    void groupByAndOrderByQuoteWhatTheSelectListQuotes() {
        String sql = sql(PostgreSqlDialect.INSTANCE, hostileCatalog(),
                "FIND umsatz_2026 u FETCH u.order_date ASC, count(u.betrag) n");

        assertTrue(sql.contains("GROUP BY"), sql);
        assertFalse(sql.matches("(?s).*GROUP BY[^\n]*\\bOrder Date\\b.*"),
                "GROUP BY left the name bare:\n" + sql);
        assertTrue(sql.contains("\"Order Date\""), sql);
    }

    /**
     * A quote character inside a name is escaped, not treated as the name's own delimiter.
     *
     * <p>The strip that removes redundant quotes used to take the two ends independently, so a
     * name that merely <em>contained</em> one lost its last character: {@code He said "hi"} became
     * {@code He said "hi} and rendered as a column nobody has - the wrong name, and no error to say
     * so. Only MariaDB and SQL Server, whose delimiters are not the double quote, were unaffected.
     */
    @Test
    void aNameContainingAQuoteIsEscapedNotTruncated() {
        String hostile = "He said \"hi\"";
        LinkResolver r = catalog("umsatz", columns("zitat", hostile));

        DIALECTS.forEach((name, dialect) -> {
            String sql = sql(dialect, r, "FIND t x FETCH x.zitat");
            String expected = switch (name) {
                case "mariadb" -> "`" + hostile + "`";
                case "mssql" -> "[" + hostile + "]";
                default -> "\"He said \"\"hi\"\"\"";
            };
            assertTrue(sql.contains(expected),
                    name + " should write " + expected + " but rendered:\n" + sql);
        });
    }

    /**
     * A word is reserved on the engines that reserve it, and nowhere else.
     *
     * <p>{@code date} is an ordinary column name on seven of the eight and a parser error on
     * Oracle. One shared list cannot express that: widening it quotes the name everywhere, which
     * on Oracle and Snowflake changes which column it resolves to, and leaving it out breaks
     * Oracle. Both catalogs in this project that use the name ({@code date} and {@code long}, in
     * covid19) went bare until the list moved onto the dialect.
     */
    @Test
    void aReservedWordIsJudgedByTheEngineThatReservesIt() {
        LinkResolver r = catalog("umsatz", columns("tag", "date"));

        assertTrue(sql(OracleDialect.INSTANCE, r, "FIND t x FETCH x.tag").contains("\"date\""),
                "Oracle reserves date and must quote it");
        assertFalse(sql(PostgreSqlDialect.INSTANCE, r, "FIND t x FETCH x.tag").contains("\"date\""),
                "PostgreSQL does not reserve date and must leave it bare");
        assertTrue(sql(MssqlDialect.INSTANCE, r, "FIND t x FETCH x.tag").contains("[key]")
                        || !sql(MssqlDialect.INSTANCE, r, "FIND t x FETCH x.tag").contains("[date]"),
                "SQL Server does not reserve date");
    }

    @Test
    void everyDialectIsCovered() {
        // A dialect added without a line here would be silently untested.
        assertEquals(8, DIALECTS.size());
    }
}
