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
package ai.koryki.postgresql.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.SchemaCopy;
import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.postgresql.PostgreSQLUnavailable;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.postgresql.iql.SqlQueryRenderer;
import ai.koryki.postgresql.northwind.NorthwindPostgresql;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Whether an index can serve the SQL the transpiler writes for a filter on the complex bed.
 *
 * <p>The other suites show what SQL is written and what it returns. Neither says whether the
 * database could answer it from an index, and a filter that can only be answered by reading every
 * row is a different thing at a million rows than at three. Here the planner is asked: {@code
 * EXPLAIN} of the generated SQL, with sequential scans switched off so that a table of three rows
 * does not decide for the scan on cost alone. If an index <em>can</em> serve the predicate, the
 * plan names it.
 *
 * <p>Two kinds of index sit on {@code check_complex} (see {@code data_check_complex.sql} in the
 * northwind repository):
 *
 * <ul>
 *   <li><b>btree on an expression</b> -- {@code ((attrs ->> 'color'))}, {@code (((doc ->>
 *       'n')::integer))}. It serves a comparison of exactly that expression, and the transpiler
 *       writes exactly that for a selection that is compared.
 *   <li><b>GIN</b> -- serves containment ({@code @>}). The transpiler writes {@code @>} for a
 *       membership in a native array, so {@code tags} is served. For jsonb it writes {@code ->>},
 *       which GIN does not serve; those cases are asserted too, as the limit they are: if the
 *       dialect ever writes containment for a map key, they are the ones that change.
 * </ul>
 */
@PostgreSQLUnavailable
class ComplexIndexPlanTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db("/ai/koryki/postgresql/databases/complex"),
                    CatalogLoader.model(
                            "/ai/koryki/postgresql/databases/complex/model", Locale.ENGLISH),
                    true);

    /**
     * The catalog told about the GIN indexes of the bed (and not about the btree ones): the
     * comparison of a key is then asked as containment, which is what these indexes answer.
     */
    private static final LinkResolver WITH_GIN = withIndexes(false);

    /** The catalog told about all five indexes: a btree on the key wins over the GIN. */
    private static final LinkResolver WITH_ALL = withIndexes(true);

    private static LinkResolver withIndexes(boolean btree) {
        Schema db =
                SchemaCopy.deepCopy(CatalogLoader.db("/ai/koryki/postgresql/databases/complex"));
        List<Index> indexes = new ArrayList<>();
        indexes.add(
                new Index(
                        "idx_check_complex_tags_gin",
                        "gin",
                        List.of(new IndexKey("tags", null, null))));
        indexes.add(
                new Index(
                        "idx_check_complex_attrs_gin",
                        "gin",
                        List.of(new IndexKey("attrs", null, null))));
        if (btree) {
            indexes.add(
                    new Index(
                            "idx_check_complex_attrs_color",
                            "btree",
                            List.of(new IndexKey("attrs", List.of("color"), "TEXT"))));
        }
        db.getTable("check_complex").orElseThrow().setIndexes(indexes);
        return new LinkResolver(
                Locale.ENGLISH,
                db,
                CatalogLoader.model(
                        "/ai/koryki/postgresql/databases/complex/model", Locale.ENGLISH),
                true);
    }

    private static final Set<String> INDEXES =
            Set.of(
                    "idx_check_complex_attrs_color",
                    "idx_check_complex_doc_n",
                    "idx_check_complex_tags_gin",
                    "idx_check_complex_attrs_gin",
                    "idx_check_complex_doc_gin");

    private static Connection connection;

    @BeforeAll
    static void connectAndCheckTheBed() throws SQLException {
        connection = NorthwindPostgresql.connection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
        }
        Set<String> present = new TreeSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT indexname FROM pg_indexes WHERE tablename = 'check_complex'")) {
            while (rows.next()) {
                present.add(rows.getString(1));
            }
        }
        Set<String> missing = new TreeSet<>(INDEXES);
        missing.removeAll(present);
        assertTrue(
                missing.isEmpty(),
                "check_complex has no index "
                        + missing
                        + ": the bed predates them; recreate it with ./gradlew"
                        + " :postgresql:createDatabase in the northwind repository");
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static String sql(String filter) {
        return sql(RESOLVER, filter);
    }

    private static String sql(LinkResolver resolver, String filter) {
        return KQLTranspiler.builder(
                        "FIND check_complex t FILTER " + filter + " FETCH t.nr", resolver)
                .functions(PostgreSqlDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
    }

    private static String plan(String filter) throws SQLException {
        return explain(sql(filter));
    }

    private static String plan(LinkResolver resolver, String filter) throws SQLException {
        return explain(sql(resolver, filter));
    }

    private static String explain(String sql) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("EXPLAIN " + sql)) {
            while (rows.next()) {
                lines.add(rows.getString(1));
            }
        }
        return String.join("\n", lines);
    }

    private static List<Long> rows(LinkResolver resolver, String filter) throws SQLException {
        List<Long> nr = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql(resolver, filter) + " ORDER BY 1")) {
            while (rows.next()) {
                nr.add(rows.getLong(1));
            }
        }
        return nr;
    }

    @ParameterizedTest(name = "{0} is served by {1}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                // a selection compared with a literal: the btree on that very expression
                "t.attrs@color = 'red'|idx_check_complex_attrs_color",
                // the cast has to be the index's: ->> is text, the index is on the integer
                "to_integer(t.doc@n) > 0|idx_check_complex_doc_n",
                "t.doc@n > 0|idx_check_complex_doc_n",
                // membership in a native array is containment: GIN
                "'red' IN t.tags|idx_check_complex_tags_gin",
            })
    void anIndexServesThePredicate(String filter, String index) throws SQLException {
        String plan = plan(filter);

        assertTrue(
                plan.contains(index), () -> index + " not in the plan of " + filter + ":\n" + plan);
    }

    @ParameterizedTest(name = "{0} is not served by {1}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                // GIN does not serve ->> = 'x': a map key compared is not containment
                "t.attrs@size = 'L'|idx_check_complex_attrs_gin",
                "t.attrs@color = 'red'|idx_check_complex_attrs_gin",
                "to_text(t.doc@n) = '1'|idx_check_complex_doc_gin",
                // another cast is another expression: the text of n is not the integer of n
                "to_text(t.doc@n) = '1'|idx_check_complex_doc_n",
                // a key the index is not on
                "t.attrs@size = 'L'|idx_check_complex_attrs_color",
            })
    void anIndexDoesNotServeThePredicate(String filter, String index) throws SQLException {
        String plan = plan(filter);

        assertFalse(
                plan.contains(index), () -> index + " is in the plan of " + filter + ":\n" + plan);
    }

    @Test
    void aKeyComparedIsAskedAsContainmentAndServedByTheGin() throws SQLException {
        String sql = sql(WITH_GIN, "t.attrs@color = 'red'");
        String plan = explain(sql);

        assertTrue(sql.contains("@>"), sql);
        assertTrue(plan.contains("idx_check_complex_attrs_gin"), plan);
    }

    @Test
    void severalKeysAreOneLookupInTheGin() throws SQLException {
        String sql = sql(WITH_GIN, "t.attrs@color = 'red' AND t.attrs@size = 'L'");
        String plan = explain(sql);

        assertEquals(1, sql.split("@>", -1).length - 1, sql);
        assertEquals(1, plan.split("idx_check_complex_attrs_gin", -1).length - 1, plan);
    }

    @Test
    void aBtreeOnTheKeyIsLeftToTheBtree() throws SQLException {
        String sql = sql(WITH_ALL, "t.attrs@color = 'red'");
        String plan = explain(sql);

        assertFalse(sql.contains("@>"), sql);
        assertTrue(plan.contains("idx_check_complex_attrs_color"), plan);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "t.attrs@color = 'red'",
                "t.attrs@color = 'red' AND t.attrs@size = 'L'",
                "t.attrs@color = 'red' AND t.attrs@size = 'S'",
                "t.attrs@color = 'red' OR t.attrs@size = 'X'",
                "t.attrs@color = 'blue'",
                "t.attrs@k = 'x'",
                "t.attrs@color = 'red' AND t.attrs@color = 'blue'",
                "NOT (t.attrs@color = 'red')",
                "NOT (t.attrs@color = 'red' AND t.attrs@size = 'L')",
                "t.attrs@color <> 'red'",
                "t.nr > 0 AND t.attrs@size = 'L'",
            })
    void theContainmentFormAnswersWhatTheComparisonAnswers(String filter) throws SQLException {
        // the plain catalog writes ->>, the one that knows the GIN writes @>: same rows, row by row
        assertEquals(rows(RESOLVER, filter), rows(WITH_GIN, filter), filter);
    }

    /**
     * Where the two forms part, written down as the contract it is: the catalog declares the values
     * of the map as text, and that is what makes the rewrite sound. A number in the data is not a
     * text, and {@code ->> 'k' = '1'} finds it where containment of the string does not.
     */
    @Test
    void theFormsPartWhereTheDataBreaksTheDeclaredType() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TEMP TABLE probe AS SELECT * FROM (VALUES"
                            + " (1, '{\"k\": \"1\"}'::jsonb),"
                            + " (2, '{\"k\": 1}'::jsonb),"
                            + " (3, '{\"k\": [\"1\"]}'::jsonb),"
                            + " (4, '{\"k\": null}'::jsonb),"
                            + " (5, '{}'::jsonb),"
                            + " (6, NULL),"
                            + " (7, '[\"1\"]'::jsonb),"
                            + " (8, '{\"k\": \"\"}'::jsonb)) AS v(nr, attrs)");
            List<Integer> extracted = new ArrayList<>();
            List<Integer> contained = new ArrayList<>();
            try (ResultSet rows =
                    statement.executeQuery(
                            "SELECT nr FROM probe WHERE (attrs ->> 'k') = '1' ORDER BY nr")) {
                while (rows.next()) {
                    extracted.add(rows.getInt(1));
                }
            }
            try (ResultSet rows =
                    statement.executeQuery(
                            "SELECT nr FROM probe WHERE attrs @> CAST('{\"k\":\"1\"}' AS JSONB) ORDER BY nr")) {
                while (rows.next()) {
                    contained.add(rows.getInt(1));
                }
            }
            // a string "1" is found by both; the number 1 only by the extracted text
            assertEquals(List.of(1, 2), extracted);
            assertEquals(List.of(1), contained);

            // the unknown: under NOT a missing key is unknown with ->> and true with containment
            List<Integer> notExtracted = new ArrayList<>();
            List<Integer> notContained = new ArrayList<>();
            try (ResultSet rows =
                    statement.executeQuery(
                            "SELECT nr FROM probe WHERE NOT ((attrs ->> 'k') = '1') ORDER BY nr")) {
                while (rows.next()) {
                    notExtracted.add(rows.getInt(1));
                }
            }
            try (ResultSet rows =
                    statement.executeQuery(
                            "SELECT nr FROM probe WHERE NOT (attrs @> CAST('{\"k\":\"1\"}' AS JSONB)) ORDER BY nr")) {
                while (rows.next()) {
                    notContained.add(rows.getInt(1));
                }
            }
            assertEquals(List.of(3, 8), notExtracted);
            // row 6 has no attrs at all: NULL @> x is unknown too, so only a missing key differs
            assertEquals(List.of(2, 3, 4, 5, 7, 8), notContained);
        }
    }

    @Test
    void theBedHasTheFiveIndexes() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM pg_indexes WHERE tablename = 'check_complex'"
                                        + " AND indexname LIKE 'idx\\_check\\_complex\\_%'")) {
            rows.next();
            assertEquals(INDEXES.size(), rows.getLong(1));
        }
    }
}
