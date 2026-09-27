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
package ai.koryki.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.kql.KQLTranspiler;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The schema a catalog's tables live in ({@link Schema#getSchemaPrefix}), as the renderer writes
 * it.
 *
 * <p>What is being protected is the difference between a base table and a block. Both stand in a
 * FROM clause, and only one of them is in a schema: {@code sales.customers} is a table, {@code
 * sales.b} would be a name that exists nowhere. The blocks are what a text rewrite of the finished
 * SQL would get wrong, which is why the prefix is applied while rendering.
 */
class SchemaPrefixTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final String DB = "/ai/koryki/databases/northwind";
    private static final String MODEL = DB + "/model";

    private static final String JOIN =
            "FIND customers c, c orders o FETCH c.company_name, o.order_id";

    /**
     * A resolver over its own copy of the Northwind catalog: {@code Schema} is a mutable bean, and
     * the shared catalog must not pick up a prefix that a test set.
     */
    private static LinkResolver resolver(String prefix) {
        Schema db = CatalogLoader.db(DB);
        db.setSchemaPrefix(prefix);
        return new LinkResolver(
                Locale.ENGLISH, db, CatalogLoader.model(MODEL, Locale.ENGLISH), true);
    }

    private static String sql(LinkResolver resolver, String kql) {
        SqlDialect dialect = DuckdbBaseDialect.INSTANCE;
        return KQLTranspiler.builder(kql, resolver)
                .functions(dialect.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(dialect, UTC));
    }

    private static int occurrences(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }

    @Test
    void withoutAPrefixTheTableNameIsBare() {
        String sql = sql(resolver(null), JOIN);

        assertTrue(sql.contains("customers c"), sql);
        assertFalse(sql.contains(".customers"), sql);
        assertFalse(sql.contains(".orders"), sql);
    }

    /**
     * A blank prefix is no prefix. Rendering it would put an empty quoted name in front of every
     * table -- {@code "".customers} -- and a catalog that says {@code "schemaPrefix": ""} plainly
     * means it has none.
     */
    @Test
    void aBlankPrefixIsTheSameAsNone() {
        String bare = sql(resolver(null), JOIN);

        assertEquals(bare, sql(resolver(""), JOIN));
        assertEquals(bare, sql(resolver("   "), JOIN));
    }

    @Test
    void thePrefixQualifiesTheTableInFrom() {
        String sql = sql(resolver("sales"), "FIND customers c FETCH c.company_name");

        assertTrue(sql.contains("sales.customers c"), sql);
    }

    @Test
    void thePrefixQualifiesEveryJoinedTable() {
        String sql = sql(resolver("sales"), JOIN);

        assertTrue(sql.contains("sales.customers c"), sql);
        assertTrue(sql.contains("sales.orders o"), sql);
    }

    /** The EXISTS sub-select is rendered by a select renderer of its own. */
    @Test
    void thePrefixReachesTheTablesInsideAnExistsSubselect() {
        String sql =
                sql(
                        resolver("sales"),
                        "FIND customers c FILTER NOT EXISTS (c orders o) FETCH c.company_name");

        assertTrue(sql.contains("sales.customers c"), sql);
        assertTrue(sql.contains("sales.orders o"), sql);
    }

    /**
     * The point of doing this in the renderer. The block {@code b} is defined by the statement's
     * own WITH clause; qualifying it would make the statement read a table that is not there.
     */
    @Test
    void aBlockIsNeverQualified() {
        String sql =
                sql(
                        resolver("sales"),
                        "WITH b AS (FIND customers c FETCH c.company_name) FIND b x FETCH"
                                + " x.company_name");

        assertTrue(sql.contains("sales.customers c"), "the table inside the block: " + sql);
        assertFalse(sql.contains("sales.b"), "the block itself: " + sql);
        assertTrue(Pattern.compile("FROM\\s+b x").matcher(sql).find(), sql);
    }

    @Test
    void aPrefixThatNeedsQuotingIsQuotedOnItsOwn() {
        String sql = sql(resolver("My Schema"), "FIND customers c FETCH c.company_name");

        assertTrue(sql.contains("\"My Schema\".customers c"), sql);
    }

    /**
     * One name, not a path: a dot inside the value is part of the name, and is quoted with it. The
     * same contract as {@code Entity.getTable}, and for the same reason -- splitting it would mean
     * deciding which dialect reads {@code a.b} as one part and which as two.
     */
    @Test
    void aDotInThePrefixIsPartOfTheNameAndNotAPath() {
        String sql = sql(resolver("a.b"), "FIND customers c FETCH c.company_name");

        assertTrue(sql.contains("\"a.b\".customers c"), sql);
        assertEquals(1, occurrences(sql, "\"a.b\""), sql);
    }
}
