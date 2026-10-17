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

package ai.koryki.duckdb;

import ai.koryki.databases.cases.GeneratedLists.Item;
import ai.koryki.databases.cases.GeneratedLists.Row;
import ai.koryki.databases.cases.GeneratedListsCatalog;
import ai.koryki.databases.cases.GeneratedListsCatalog.Spelling;
import ai.koryki.databases.cases.GeneratedListsContract;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Lists made up from a seed, in two tables of different shapes, on DuckDB; see {@link
 * GeneratedListsContract}. DuckDB is the engine the answers were first checked on, which is what
 * keeps the rows and the way they are answered honest before another engine is blamed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedListsDuckDBEngineTest extends GeneratedListsContract {

    private static final Spelling SPELLING =
            new Spelling(
                    "VARCHAR",
                    "INTEGER",
                    "VARCHAR",
                    "VARCHAR[]",
                    "INTEGER[]",
                    "STRUCT(sku VARCHAR, qty INTEGER)[]",
                    "JSON");

    private static final Map<String, Boolean> SHAPES =
            GeneratedListsCatalog.shapes("gen_plain", "gen_json");

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

    private static String text(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private static <T> String list(List<T> list, String type, Function<T, String> element) {
        return list == null
                ? "NULL"
                : "CAST(["
                        + list.stream().map(element).collect(Collectors.joining(", "))
                        + "] AS "
                        + type
                        + ")";
    }

    private static String item(Item item) {
        return "{'sku': "
                + text(item.sku())
                + ", 'qty': "
                + (item.qty() == null ? "NULL" : item.qty())
                + "}";
    }

    @BeforeAll
    void load() throws SQLException {
        var database = NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind();
        Connection connection = database.getConnection();
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, Boolean> shape : SHAPES.entrySet()) {
                String table = shape.getKey();
                statement.execute("DROP TABLE IF EXISTS " + table);
                statement.execute(
                        "CREATE TABLE "
                                + table
                                + " (id INTEGER PRIMARY KEY, tags VARCHAR[], nums INTEGER[], items "
                                + SPELLING.items()
                                + (shape.getValue() ? ", doc JSON" : "")
                                + ")");
                for (Row row : data) {
                    statement.execute(
                            "INSERT INTO "
                                    + table
                                    + " VALUES ("
                                    + row.id()
                                    + ", "
                                    + list(
                                            row.tags(),
                                            "VARCHAR[]",
                                            GeneratedListsDuckDBEngineTest::text)
                                    + ", "
                                    + list(
                                            row.nums(),
                                            "INTEGER[]",
                                            n -> n == null ? "NULL" : String.valueOf(n))
                                    + ", "
                                    + list(
                                            row.items(),
                                            SPELLING.items(),
                                            GeneratedListsDuckDBEngineTest::item)
                                    + (shape.getValue() ? ", '{\"n\": " + row.id() + "}'" : "")
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

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }

    @Override
    protected List<String> tables() {
        return GeneratedListsCatalog.names(SHAPES);
    }
}
