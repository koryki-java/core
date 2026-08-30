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
package ai.koryki.oracle.iql;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.LinkResolver;
import ai.koryki.jdbc.ListResult;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.oracle.OracleDatabase;
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.northwind.NorthwindOracle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a mixed-case name is actually stored as, and what the catalog therefore has to say.
 *
 * <p>{@code HostileNameSnowflakeTest} settled the easy half: a name that <em>cannot</em> be written
 * bare is stored exactly as typed, so it must be rendered unfolded. That argument turns on the name
 * being unwritable without quotes, and it does not carry to a name that is merely mixed case.
 * {@code CREATE TABLE Betrag} parses perfectly well here - and stores {@code BETRAG}.
 *
 * <p>So {@code Betrag} is two different tables depending on how it was created, and nothing in the
 * rendered SQL can tell them apart. Nothing introspects a database here either; the catalogs are
 * hand-maintained JSON. That leaves a contract rather than a deduction, stated on
 * {@link Entity#getTable()} and {@link Attribute#getColumn()}: the value is the exact stored
 * spelling. This test is the contract's evidence - it creates the ambiguous case both ways and
 * shows which spelling each one leaves behind.
 *
 * <p>Both tables are created and dropped by the test, and named so nobody mistakes them for data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OracleUnavailable
class MixedCaseNameOracleTest {

    /** Created WITHOUT quotes. Oracle folds it, so this is not the name it ends up under. */
    private static final String WRITTEN_BARE = "koryki_test_Betrag";

    /** The name the one above is actually stored as. */
    private static final String STORED_BARE = "KORYKI_TEST_BETRAG";

    /** Created WITH quotes, and therefore stored as typed. */
    private static final String WRITTEN_QUOTED = "koryki_test_Umsatz";

    private static Connection connection;

    @BeforeAll
    void createBothTables() throws Exception {
        connection = NorthwindOracle.connection();
        drop();
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE " + WRITTEN_BARE + " (menge NUMBER(10,2))");
            s.execute("INSERT INTO " + WRITTEN_BARE + " VALUES (19.99)");
            s.execute("CREATE TABLE \"" + WRITTEN_QUOTED + "\" (\"Menge\" NUMBER(10,2))");
            s.execute("INSERT INTO \"" + WRITTEN_QUOTED + "\" VALUES (5.50)");
        }
    }

    @AfterAll
    void dropBothTables() throws Exception {
        if (connection != null) {
            drop();
            connection.close();
        }
    }

    private void drop() {
        for (String ddl : List.of("DROP TABLE " + WRITTEN_BARE,
                "DROP TABLE \"" + WRITTEN_QUOTED + "\"")) {
            try (Statement s = connection.createStatement()) {
                s.execute(ddl);
            } catch (SQLException ignored) {
                // ORA-00942 on the first run; there is nothing to clean up yet.
            }
        }
    }

    /**
     * The premise, from the database rather than from an argument.
     *
     * <p>A mixed-case name written bare is folded up and a quoted one is not, so the two tables
     * created above sit in the catalog under spellings that share no case at all. This is the whole
     * reason the contract has to be stated: the renderer sees one string and cannot know which of
     * these two histories produced it.
     */
    @Test
    void oracleFoldsABareNameAndKeepsAQuotedOne() throws Exception {
        assertTrue(exists(STORED_BARE),
                "a bare mixed-case name should be stored folded up, as " + STORED_BARE);
        assertTrue(exists(WRITTEN_QUOTED),
                "a quoted name should be stored exactly as typed");
    }

    /**
     * A catalog holding the stored spelling reaches the table; that is the contract working.
     *
     * <p>{@code KORYKI_TEST_BETRAG} is not lower case, so it is quoted - and because the renderer
     * leaves a quoted name unfolded, {@code "KORYKI_TEST_BETRAG"} is what Oracle is asked for, which
     * is what Oracle stored.
     */
    @Test
    void theStoredSpellingIsWhatResolves() throws Exception {
        assertEquals(1, rows(catalog(STORED_BARE, "menge", "MENGE")),
                "the catalog carrying the stored spelling should reach the table");
    }

    /**
     * The same table under the spelling somebody would naturally write is not found.
     *
     * <p>Kept as a test rather than a remark, because it is the failure the contract exists to
     * prevent and it is completely silent from the query's side: {@code koryki_test_Betrag} is what
     * the CREATE statement said, it is a perfectly reasonable thing to put in a model, and it
     * addresses nothing.
     */
    @Test
    void theSpellingFromTheCreateStatementDoesNot() {
        assertTrue(fails(catalog(WRITTEN_BARE, "menge", "menge")),
                "a catalog holding the unfolded spelling should not reach the folded table");
    }

    /** And the quoted-created table answers to its own literal spelling, as Snowflake's did. */
    @Test
    void aQuotedCreatedNameAnswersToItsLiteralSpelling() throws Exception {
        assertEquals(1, rows(catalog(WRITTEN_QUOTED, "menge", "Menge")));
    }

    private boolean exists(String table) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT table_name FROM user_tables WHERE table_name = '" + table + "'")) {
            return rs.next();
        }
    }

    private int rows(LinkResolver resolver) throws Exception {
        return engine(resolver).executeKQL("FIND t x FETCH x.menge", ListResult::new)
                .getRows().size();
    }

    private boolean fails(LinkResolver resolver) {
        try {
            engine(resolver).executeKQL("FIND t x FETCH x.menge", ListResult::new);
            return false;
        } catch (Exception expected) {
            return true;
        }
    }

    private Engine<HeaderInfo, ListResult<HeaderInfo>> engine(LinkResolver resolver) throws Exception {
        OracleDatabase<ListResult<HeaderInfo>> database =
                new OracleDatabase<>("mixedcase", NorthwindOracle.connection());
        return EngineBuilder.headers(database, resolver, new SqlQueryRenderer(ZoneId.of("UTC")))
                .build();
    }

    /** One table, one column, named physically as the arguments say. */
    private static LinkResolver catalog(String physicalTable, String modelColumn, String physicalColumn) {

        Column column = new Column(physicalColumn);
        column.setTypeFamily("DECIMAL");
        column.setDialectType("DECIMAL(10,2)");

        Table table = new Table(physicalTable);
        table.setColumns(new ArrayList<>(List.of(column)));

        Schema schema = new Schema("mixedcase");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());

        Attribute attribute = new Attribute(modelColumn);
        attribute.setColumn(physicalColumn);

        Entity entity = new Entity("t");
        entity.setTable(physicalTable);
        entity.setAttributes(new ArrayList<>(List.of(attribute)));

        Model model = new Model("mixedcase");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());

        return new LinkResolver(Locale.ENGLISH, schema, model, true);
    }
}
