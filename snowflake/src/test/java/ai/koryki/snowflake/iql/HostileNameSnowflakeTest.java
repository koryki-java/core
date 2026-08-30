package ai.koryki.snowflake.iql;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.jdbc.ListResult;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.snowflake.SnowflakeDatabase;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table Snowflake can only hold under quotes, queried through the engine.
 *
 * <p>Everything else about identifier quoting is checked against rendered text. This one is the
 * only place the database itself answers, and it is the only place it can: northwind has no
 * hostile name, so none of its 593 fixtures ever sends Snowflake a quoted identifier. Without this
 * test, "Oracle and Snowflake need the upper-cased form" is an argument, not a fact.
 *
 * <p>The table is created and dropped by the test. It is named so that nobody mistakes it for
 * data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SnowflakeUnavailable
class HostileNameSnowflakeTest {

    /** The physical name. A space and a digit-leading second word - unquotable it is not. */
    private static final String TABLE = "koryki test Umsatz 2026";

    private static Connection connection;

    @BeforeAll
    void createTheTable() throws Exception {
        connection = NorthwindSnowflake.connection();
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE OR REPLACE TABLE \"" + TABLE + "\" "
                    + "(\"Order Date\" DATE, \"order\" VARCHAR, \"Betrag\" NUMBER(10,2))");
            s.execute("INSERT INTO \"" + TABLE + "\" VALUES "
                    + "('2026-03-01', 'A-1', 19.99), ('2026-03-02', 'A-2', 5.50)");
        }
    }

    @AfterAll
    void dropTheTable() throws Exception {
        if (connection != null) {
            try (Statement s = connection.createStatement()) {
                s.execute("DROP TABLE IF EXISTS \"" + TABLE + "\"");
            }
            connection.close();
        }
    }

    /**
     * What Snowflake actually stored.
     *
     * <p>The premise the whole question rests on. An unquoted name is folded up, which is why
     * northwind's {@code products} is stored {@code PRODUCTS}. But a name like this one cannot be
     * written unquoted at all - the statement would not parse - so it is stored exactly as typed,
     * mixed case and all. Which means upper-casing it when rendering would look for something else.
     */
    @Test
    void snowflakeStoresAQuotedNameVerbatim() throws Exception {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_name = '" + TABLE + "'")) {
            assertTrue(rs.next(), "the table should be there under its literal name");
            assertEquals(TABLE, rs.getString(1),
                    "a quoted name is stored as written - not folded to upper case");
        }
    }

    /** The engine, end to end, against the real table. */
    @Test
    void theEngineCanQueryIt() throws Exception {

        SnowflakeDatabase<ListResult<HeaderInfo>> database =
                new SnowflakeDatabase<>("hostile", NorthwindSnowflake.connection());

        // The module's own renderer, which wires SnowflakeDialect for us.
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine = EngineBuilder
                .headers(database, catalog(), new SqlQueryRenderer(ZoneId.of("UTC")))
                .build();

        ListResult<HeaderInfo> result = engine.executeKQL(
                "FIND umsatz u FETCH u.order_date, u.bestellnummer, u.betrag", ListResult::new);

        assertEquals(2, result.getRows().size(),
                "the rendered SQL did not reach the table:\n" + engine.toSql(
                        "FIND umsatz u FETCH u.order_date, u.bestellnummer, u.betrag"));
    }

    /** The catalog as an introspection of that table would produce it: physical names verbatim. */
    private static LinkResolver catalog() {

        Table table = new Table(TABLE);
        table.setColumns(new ArrayList<>(List.of(
                column("Order Date", "DATE", "DATE"),
                column("order", "TEXT", "VARCHAR"),
                column("Betrag", "DECIMAL", "DECIMAL(10,2)"))));

        Schema schema = new Schema("hostile");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());

        Entity entity = new Entity("umsatz");
        entity.setTable(TABLE);
        entity.setAttributes(new ArrayList<>(List.of(
                attribute("order_date", "Order Date"),
                attribute("bestellnummer", "order"),
                attribute("betrag", "Betrag"))));

        Model model = new Model("hostile");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());

        return new LinkResolver(Locale.ENGLISH, schema, model, true);
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
}
