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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors and lambdas on Oracle, from KQL through the whole transpiler on its catalog
 * of the complex bed. No connection is needed; what the SQL returns is the business of {@link
 * ComplexOracleEngineTest}.
 */
class OracleSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(OracleDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // an element: a scalar subquery over TABLE(), numbered by ROWNUM
                "t.tags@2|(SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 2)",
                // from the end: the size is COUNT(*), CARDINALITY is for nested tables only
                "t.tags@-1|E1.RN = (SELECT COUNT(*) FROM TABLE(t.tags)) - 1 + 1",
                // a range is a collection again, of a generic type, and NULL for a NULL list
                "t.tags@1:2|CASE WHEN t.tags IS NULL THEN NULL ELSE CAST(MULTISET(SELECT E.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E WHERE E.RN >= 1 AND E.RN <= 2 ORDER BY E.RN) AS SYS.ODCIVARCHAR2LIST) END",
                "t.vec@2:|AS SYS.ODCINUMBERLIST",
                // attributes of object types by dot notation, quoted when not plain
                "t.dims@w|t.dims.w",
                "t.dims@-1|t.dims.h",
                "t.address@geo@lat|t.address.geo.lat",
                "t.odd@'ODD NAME'|t.odd.\"Odd Name\"",
                // the steps after an element are folded into its subquery
                "t.items@1@sku|(SELECT E1.V.sku FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1)",
                "t.items@1@opts@2|(SELECT (SELECT E2.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(E1.V.opts) Z) E2 WHERE E2.RN = 2) FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1)",
                // JSON: one path, counted from 0, "last" for the end, every step guarded by the
                // type of what it is applied to (a lax path would unwrap lists and wrap objects)
                "t.doc@a@b@1|CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.b.type()') = 'array' THEN JSON_QUERY(t.doc, '$.a.b[0]' RETURNING JSON) END",
                "t.doc@-1|JSON_QUERY(t.doc, '$[last]' RETURNING JSON)",
                "t.doc@-3|JSON_QUERY(t.doc, '$[last-2]' RETURNING JSON)",
                "t.doc@'a b'|JSON_QUERY(t.doc, '$.\"a b\"' RETURNING JSON)",
            })
    void theShapeOfTheSelection(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // the element is X and its position I of a derived table named for the depth
                "list_transform(t.scores, x -> x * 2)|CAST(MULTISET(SELECT LA0.X * 2 FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.scores) Z) LA0 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST)",
                "list_transform(t.scores, (x, i) -> x * i)|SELECT LA0.X * LA0.I FROM",
                "list_filter(t.tags, x -> x <> 'green')|SELECT LA0.X FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.tags) Z) LA0 WHERE LA0.X <> 'green' ORDER BY LA0.I) AS SYS.ODCIVARCHAR2LIST",
                // an object element: VALUE(), and a step on the parameter by dot notation
                "list_transform(t.items, it -> it@price)|SELECT LA0.X.price FROM (SELECT VALUE(Z) X, ROWNUM I FROM TABLE(t.items) Z) LA0",
                // a selection on the result of a lambda: TABLE() over the cast
                "list_transform(t.scores, x -> x + 1)@1|FROM TABLE(CASE WHEN t.scores IS NULL",
            })
    void theShapeOfTheLambda(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_filter(t.items, it -> it@qty > 1)|cannot build a list of STRUCT",
                "t.items@1:2|cannot build a list of STRUCT",
                "list_transform(t.items, it -> it@opts)|cannot build a list of ARRAY",
            })
    void whatOracleCannotBuildIsRefused(String expression, String message) {
        KorykiaiException e = assertThrows(KorykiaiException.class, () -> sql(expression));

        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void reduceIsDeclaredUnsupportedWithWhatToDoInstead() {
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> sql("list_reduce(t.scores, (a, b) -> a + b)"));

        assertTrue(
                String.valueOf(e.getMessage()).contains("no fold over a collection"),
                e.getMessage());
    }

    @Test
    void aQuoteInAJsonKeyStaysInsideItsLiteral() {
        String actual = sql("t.attrs@'it\\'s'");

        assertTrue(actual.contains("'$.\"it''s\"'"), actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideTheLiteralOfItsGuardToo() {
        String actual = sql("t.attrs@'it\\'s'@x");

        assertTrue(actual.contains("'$.\"it''s\".type()'"), actual);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "to_integer(t.doc@n)|THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER)",
                "to_text(t.doc)|JSON_VALUE(t.doc, '$')",
            })
    void aJsonValueIsCastAsTheScalarItHolds(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "list_size(t.tags)|CASE WHEN t.tags IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.tags)) END",
                "list_contains(t.tags, 'red')|((SELECT COUNT(*) FROM TABLE(t.tags) WHERE COLUMN_VALUE = 'red') > 0)",
                "list_sum(t.scores)|(SELECT SUM(COLUMN_VALUE) FROM TABLE(t.scores))",
                "list_avg(t.scores)|CAST(AVG(COLUMN_VALUE) AS BINARY_DOUBLE)",
            })
    void aListAnswersAboutItself(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
