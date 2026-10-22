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
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.postgresql.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A comparison of one key of a map with a text, asked as containment when a GIN index is there to
 * answer it, and only then. No connection is needed; that the rewritten SQL returns what the
 * comparison returns, and that the index serves it, is {@link ComplexIndexPlanTest}.
 */
class ComplexContainmentTest {

    private static final String DB = "/ai/koryki/postgresql/databases/complex";

    private static Index gin(String column, String opclass) {
        Index index =
                new Index(
                        "idx_" + column + "_gin", "gin", List.of(new IndexKey(column, null, null)));
        index.setOpclass(opclass);
        return index;
    }

    private static Index btree(String column, String key) {
        return new Index(
                "idx_" + column + "_" + key,
                "btree",
                List.of(new IndexKey(column, List.of(key), "TEXT")));
    }

    private static LinkResolver resolver(Index... indexes) {
        Schema db = SchemaCopy.deepCopy(CatalogLoader.db(DB));
        if (indexes.length > 0) {
            db.getTable("check_complex")
                    .orElseThrow()
                    .setIndexes(new ArrayList<>(List.of(indexes)));
        }
        return new LinkResolver(
                Locale.ENGLISH, db, CatalogLoader.model(DB + "/model", Locale.ENGLISH), true);
    }

    private static String where(LinkResolver resolver, String filter) {
        String sql =
                KQLTranspiler.builder(
                                "FIND check_complex t FILTER " + filter + " FETCH t.nr", resolver)
                        .functions(PostgreSqlDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("WHERE")).replaceAll("\\s+", " ").strip();
    }

    @Test
    void aGinIndexMakesTheComparisonAContainment() {
        assertEquals(
                "WHERE t.attrs @> CAST('{\"color\":\"red\"}' AS JSONB)",
                where(resolver(gin("attrs", null)), "t.attrs@color = 'red'"));
    }

    @Test
    void bothOperatorClassesServeContainment() {
        assertTrue(
                where(resolver(gin("attrs", "jsonb_path_ops")), "t.attrs@color = 'red'")
                        .contains("@>"));
        assertTrue(
                where(resolver(gin("attrs", "jsonb_ops")), "t.attrs@color = 'red'").contains("@>"));
    }

    @Test
    void withoutIndexInformationTheSqlIsWhatItAlwaysWas() {
        assertEquals(
                "WHERE (t.attrs ->> 'color') = 'red'", where(resolver(), "t.attrs@color = 'red'"));
    }

    @Test
    void anIndexOnAnotherColumnChangesNothing() {
        assertFalse(where(resolver(gin("counts", null)), "t.attrs@color = 'red'").contains("@>"));
    }

    @Test
    void aBtreeOnTheKeyBeatsContainment() {
        String sql =
                where(
                        resolver(gin("attrs", null), btree("attrs", "color")),
                        "t.attrs@color = 'red'");

        assertFalse(sql.contains("@>"), sql);
        assertTrue(sql.contains("->> 'color'"), sql);
    }

    @Test
    void aBtreeOnAnotherKeyDoesNot() {
        assertTrue(
                where(resolver(gin("attrs", null), btree("attrs", "size")), "t.attrs@color = 'red'")
                        .contains("@>"));
    }

    @Test
    void aPartialIndexIsNotReliedOn() {
        Index partial = gin("attrs", null);
        partial.setWhere("nr > 1");

        assertFalse(where(resolver(partial), "t.attrs@color = 'red'").contains("@>"));
    }

    @Test
    void aBtreeIsNotAnAnswerToContainment() {
        Index plain = new Index("idx", "btree", List.of(new IndexKey("attrs", null, null)));

        assertFalse(where(resolver(plain), "t.attrs@color = 'red'").contains("@>"));
    }

    @Test
    void comparisonsOfOneColumnAreOneContainment() {
        String sql =
                where(resolver(gin("attrs", null)), "t.attrs@color = 'red' AND t.attrs@size = 'L'");

        assertEquals(1, sql.split("@>", -1).length - 1, sql);
        assertTrue(sql.contains("\"color\":\"red\"") && sql.contains("\"size\":\"L\""), sql);
        assertFalse(sql.contains("->>"), sql);
    }

    @Test
    void theSameComparisonTwiceIsOnce() {
        assertEquals(
                "WHERE t.attrs @> CAST('{\"color\":\"red\"}' AS JSONB)",
                where(
                        resolver(gin("attrs", null)),
                        "t.attrs@color = 'red' AND t.attrs@color = 'red'"));
    }

    @Test
    void aContradictionIsNotMerged() {
        String sql =
                where(
                        resolver(gin("attrs", null)),
                        "t.attrs@color = 'red' AND t.attrs@color = 'blue'");

        assertEquals(2, sql.split("@>", -1).length - 1, sql);
        assertTrue(sql.contains("\"red\"") && sql.contains("\"blue\""), sql);
    }

    @Test
    void otherConjunctsStayWhereTheyWere() {
        String sql =
                where(
                        resolver(gin("attrs", null)),
                        "t.nr > 0 AND t.attrs@color = 'red' AND t.attrs@size = 'L'");

        assertTrue(sql.contains("t.nr > 0"), sql);
        assertEquals(1, sql.split("@>", -1).length - 1, sql);
        assertTrue(sql.contains("\"color\":\"red\"") && sql.contains("\"size\":\"L\""), sql);
    }

    @Test
    void underANegationTheComparisonStays() {
        // NOT of a missing key is unknown with ->> and true with containment: not the same rows
        String sql = where(resolver(gin("attrs", null)), "NOT (t.attrs@color = 'red')");

        assertFalse(sql.contains("@>"), sql);
    }

    @Test
    void underANegationOfAnAndTheComparisonsStay() {
        String sql =
                where(resolver(gin("attrs", null)), "NOT (t.attrs@color = 'red' AND t.nr > 1)");

        assertFalse(sql.contains("@>"), sql);
    }

    @Test
    void inAnOrItIsStillAFilter() {
        String sql = where(resolver(gin("attrs", null)), "t.attrs@color = 'red' OR t.nr > 1");

        assertTrue(sql.contains("@> CAST('{\"color\":\"red\"}' AS JSONB)"), sql);
    }

    @Test
    void aQuoteAndABackslashAreEscapedTwice() {
        String sql = where(resolver(gin("attrs", null)), "t.attrs@color = 'it\\'s \"x\"'");

        assertTrue(sql.contains("{\"color\":\"it''s \\\"x\\\"\"}"), sql);
    }

    @Test
    void otherOperatorsStayOnTheExtractedValue() {
        String sql = where(resolver(gin("attrs", null)), "t.attrs@color <> 'red'");

        assertFalse(sql.contains("@>"), sql);
    }

    @Test
    void aMapOfNumbersIsNotRewritten() {
        assertFalse(where(resolver(gin("counts", null)), "t.counts@a = 1").contains("@>"));
    }

    @Test
    void aJsonColumnTheCatalogDoesNotDescribeIsNotRewritten() {
        assertFalse(
                where(resolver(gin("doc", "jsonb_path_ops")), "to_text(t.doc@n) = '1'")
                        .contains("@>"));
    }
}
