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

package ai.koryki.oracle.complex;

import ai.koryki.databases.cases.GeneratedLists.Item;
import ai.koryki.databases.cases.GeneratedLists.Row;
import ai.koryki.databases.cases.GeneratedListsCatalog;
import ai.koryki.databases.cases.GeneratedListsCatalog.Spelling;
import ai.koryki.databases.cases.GeneratedListsContract;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import ai.koryki.oracle.northwind.NorthwindOracle;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Lists made up from a seed, in tables of four shapes, on Oracle; see {@link
 * GeneratedListsContract}. This is where the fault of {@code docs/NESTED.md}, "Oracle: an element
 * of a VARRAY", is looked for: a position of a VARRAY answered with the element of another row, on
 * one shape of table and not on another. The shapes are the plain table, one with a JSON column
 * (which is the one the fault was not seen on), one whose list of structs is a nested table, and
 * one whose VARRAY is stored as a LOB. The types and tables are made here with names of their own
 * and dropped again.
 */
@OracleUnavailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedListsOracleEngineTest extends GeneratedListsContract {

    private static final Spelling SPELLING =
            new Spelling(
                    "VARCHAR2(100)",
                    "INTEGER",
                    "VARCHAR2(20)",
                    "ARRAY(VARCHAR2(100))",
                    "ARRAY(INTEGER)",
                    "ARRAY(ROW(sku VARCHAR2(20), qty INTEGER))",
                    "JSON");

    private static final Map<String, Boolean> SHAPES =
            GeneratedListsCatalog.shapes("gen_plain", "gen_json", "gen_nested", "gen_lob");

    private static final List<String> TABLES = List.copyOf(SHAPES.keySet());

    private static final List<String> TYPES =
            List.of(
                    "gen_item_tab",
                    "gen_item_list",
                    "gen_item",
                    "gen_str_big",
                    "gen_int_list",
                    "gen_str_list");

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;
    private NorthwindOracle<ListWithSqlResult<HeaderInfo>> database;

    private static String text(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private static <T> String list(List<T> list, String type, Function<T, String> element) {
        return list == null
                ? "NULL"
                : type + "(" + list.stream().map(element).collect(Collectors.joining(", ")) + ")";
    }

    private static String item(Item item) {
        return "gen_item("
                + text(item.sku())
                + ", "
                + (item.qty() == null ? "NULL" : item.qty())
                + ")";
    }

    private static void quietly(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException ignored) {
            // nothing of that name: what is dropped here may not be there
        }
    }

    private void drop(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES) {
                quietly(statement, "DROP TABLE " + table + " PURGE");
            }
            for (String type : TYPES) {
                quietly(statement, "DROP TYPE " + type + " FORCE");
            }
        }
    }

    @BeforeAll
    void load() throws SQLException {
        database = new NorthwindOracle<>();
        Connection connection = database.getConnection();
        drop(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TYPE gen_str_list AS VARRAY(100) OF VARCHAR2(100)");
            statement.execute("CREATE TYPE gen_int_list AS VARRAY(100) OF INTEGER");
            statement.execute("CREATE TYPE gen_str_big AS VARRAY(2000) OF VARCHAR2(100)");
            statement.execute("CREATE TYPE gen_item AS OBJECT (sku VARCHAR2(20), qty INTEGER)");
            statement.execute("CREATE TYPE gen_item_list AS VARRAY(100) OF gen_item");
            statement.execute("CREATE TYPE gen_item_tab AS TABLE OF gen_item");
            statement.execute(
                    "CREATE TABLE gen_plain (id INTEGER PRIMARY KEY, tags gen_str_list,"
                            + " nums gen_int_list, items gen_item_list)");
            statement.execute(
                    "CREATE TABLE gen_json (id INTEGER PRIMARY KEY, tags gen_str_list,"
                            + " nums gen_int_list, items gen_item_list, doc JSON)");
            statement.execute(
                    "CREATE TABLE gen_nested (id INTEGER PRIMARY KEY, tags gen_str_list,"
                            + " nums gen_int_list, items gen_item_tab)"
                            + " NESTED TABLE items STORE AS gen_nested_items_store");
            statement.execute(
                    "CREATE TABLE gen_lob (id INTEGER PRIMARY KEY, tags gen_str_big,"
                            + " nums gen_int_list, items gen_item_list)");
            for (Row row : data) {
                for (String table : TABLES) {
                    String tagsType = table.equals("gen_lob") ? "gen_str_big" : "gen_str_list";
                    String itemsType =
                            table.equals("gen_nested") ? "gen_item_tab" : "gen_item_list";
                    statement.execute(
                            "INSERT INTO "
                                    + table
                                    + " VALUES ("
                                    + row.id()
                                    + ", "
                                    + list(
                                            row.tags(),
                                            tagsType,
                                            GeneratedListsOracleEngineTest::text)
                                    + ", "
                                    + list(
                                            row.nums(),
                                            "gen_int_list",
                                            n -> n == null ? "NULL" : String.valueOf(n))
                                    + ", "
                                    + list(
                                            row.items(),
                                            itemsType,
                                            GeneratedListsOracleEngineTest::item)
                                    + (table.equals("gen_json")
                                            ? ", JSON('{\"n\": " + row.id() + "}')"
                                            : "")
                                    + ")");
                }
            }
        }
        LinkResolver resolver = GeneratedListsCatalog.resolver(SPELLING, SHAPES);
        engine =
                EngineBuilder.headers(
                                database, resolver, () -> new SqlQueryRenderer(ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }

    @AfterAll
    void unload() throws SQLException {
        drop(database.getConnection());
    }

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }

    @Override
    protected List<String> tables() {
        return TABLES;
    }
}
