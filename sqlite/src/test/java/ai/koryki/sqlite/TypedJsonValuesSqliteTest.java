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
package ai.koryki.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.sqlite.iql.SqliteDialect;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The members a catalog declares inside a JSON column, read as their types, <em>with values in
 * them</em>. The complex bed holds no text, boolean or decimal member, so those casts ran against
 * NULL only, which is how three of them went wrong unseen: a text kept the quotes of the JSON
 * string, a boolean went through a numeric cast, a decimal crashed the renderer.
 *
 * <p>An in-memory database with its own table, because the values are the point and the bed's
 * documents are shown by dozens of goldens.
 */
class TypedJsonValuesSqliteTest {

    private static Connection connection;
    private static LinkResolver resolver;

    private static Concept scalar(String family, String dialectType) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        return concept;
    }

    private static Concept container(String family, Object... keyAndEntry) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < keyAndEntry.length; i += 2) {
            Object value = keyAndEntry[i + 1];
            entries.put(
                    (String) keyAndEntry[i],
                    value instanceof Concept c ? Entry.of(c) : Entry.of((String) value));
        }
        concept.setEntries(entries);
        return concept;
    }

    @BeforeAll
    static void catalogAndData() throws SQLException {
        Concept docConcept =
                container(
                        "STRUCT",
                        "n",
                        "INTEGER",
                        "s",
                        "VARCHAR",
                        "flag",
                        "BOOLEAN",
                        "d",
                        "DECIMAL",
                        "a",
                        container("STRUCT", "b", container("ARRAY", "element", "INTEGER")));
        docConcept.setTypeEncoding("JSON");
        docConcept.setDialectType("TEXT");
        Column doc = new Column("doc");
        doc.setConcept(docConcept);
        Column id = new Column("id");
        id.setConcept(scalar("INTEGER", "INTEGER"));
        id.setPkPos(1);
        Table table = new Table("docs");
        table.setColumns(new ArrayList<>(List.of(id, doc)));
        Schema schema = new Schema("typed");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());

        Entity entity = new Entity("docs");
        entity.setTable("docs");
        entity.setAttributes(new ArrayList<>(List.of(new Attribute("id"), new Attribute("doc"))));
        Model model = new Model("typed");
        model.setEntities(new ArrayList<>(List.of(entity)));
        model.setLinks(new ArrayList<>());
        resolver = new LinkResolver(Locale.ENGLISH, schema, model, true);

        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE docs (id INTEGER, doc TEXT)");
            s.execute(
                    "INSERT INTO docs VALUES"
                            + " (1, '{\"n\": 3, \"s\": \"hello\", \"flag\": true, \"d\": 1.25,"
                            + " \"a\": {\"b\": [1, 2, 3]}}'),"
                            + " (2, '{\"n\": -4, \"s\": \"it''s \\\"quoted\\\"\", \"flag\": false,"
                            + " \"d\": 2.5}'),"
                            + " (3, NULL),"
                            + " (4, '{\"s\": null, \"flag\": null}'),"
                            + " (5, '{}')");
        }
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    /** The first column of every row of {@code kql}, as text, in the order the query gives. */
    private static List<String> run(String kql) throws SQLException {
        String sql =
                KQLTranspiler.builder(kql, resolver)
                        .functions(SqliteDialect.INSTANCE.getFunctionRenderer())
                        .dialect(SqliteDialect.INSTANCE)
                        .build()
                        .getSql(new SqlQueryRenderer(SqliteDialect.INSTANCE, ZoneId.of("UTC")));
        List<String> rows = new ArrayList<>();
        try (Statement s = connection.createStatement();
                ResultSet r = s.executeQuery(sql + " ORDER BY 1")) {
            while (r.next()) {
                rows.add(r.getString(1));
            }
        }
        return rows;
    }

    @Test
    void aTextIsTheTextWithoutTheQuotesOfTheJsonString() throws SQLException {
        assertEquals(
                java.util.Arrays.asList("hello", "it's \"quoted\"", null, null, null),
                sorted(run("FIND docs d FETCH d.doc@s")));
    }

    @Test
    void aTextTakesPartInTextFunctionsAndComparisons() throws SQLException {
        assertEquals(
                List.of("HELLO"), run("FIND docs d FILTER d.doc@s = 'hello' FETCH upper(d.doc@s)"));
        assertEquals(List.of("1"), run("FIND docs d FILTER d.doc@s LIKE 'h%' FETCH d.id"));
    }

    @Test
    void aBooleanIsTrueOrFalseAndNullWhereThereIsNone() throws SQLException {
        assertEquals(List.of("1"), run("FIND docs d FILTER d.doc@flag FETCH d.id"));
        assertEquals(List.of("2"), run("FIND docs d FILTER NOT d.doc@flag FETCH d.id"));
        assertEquals(
                List.of("1", "0"),
                run("FIND docs d FILTER d.id <= 2 FETCH d.doc@flag").stream()
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList());
    }

    @Test
    void aDecimalAddsUpAndTakesPartInArithmetic() throws SQLException {
        assertEquals(List.of("3.75"), run("FIND docs d FETCH sum(d.doc@d)"));
        assertEquals(List.of("2.25"), run("FIND docs d FILTER d.id = 1 FETCH d.doc@d + 1"));
    }

    @Test
    void anIntegerIsGroupedAndFilteredOn() throws SQLException {
        assertEquals(List.of("1"), run("FIND docs d FILTER d.doc@n > 0 FETCH d.id"));
        assertEquals(List.of("-4"), run("FIND docs d FILTER d.doc@n < 0 FETCH d.doc@n"));
        assertEquals(
                java.util.Arrays.asList("-4", "3", null),
                sorted(run("FIND docs d FETCH d.doc@n n, count(d.id) c")));
    }

    /**
     * The declaration is trusted, not checked, and SQLite does not object: the text "abc" read as
     * an INTEGER is 0, silently, where DuckDB fails the query. Pinned so that the day the casts
     * become strict (or forgiving) everywhere is a decision, not a surprise.
     */
    @Test
    void aValueOfAnotherTypeBecomesZeroWithoutAWord() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("INSERT INTO docs VALUES (6, '{\"n\": \"abc\"}')");
        }
        try {
            assertEquals(
                    java.util.Arrays.asList("-4", "0", "3", null, null, null),
                    sorted(run("FIND docs d FETCH d.doc@n")));
        } finally {
            try (Statement s = connection.createStatement()) {
                s.execute("DELETE FROM docs WHERE id = 6");
            }
        }
    }

    private static List<String> sorted(List<String> rows) {
        return rows.stream()
                .sorted(java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .toList();
    }
}
