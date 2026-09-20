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
package ai.koryki.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The second injection surface named in {@code SECURITY.md}: the catalog. {@code db.json} and
 * {@code model.json} are ordinary files that an application loads from its own resources — and an
 * application that generates or accepts them (from a scaffolding run, a tenant upload, a database
 * introspection nobody sanitised) is handing this library attacker-controlled identifiers.
 *
 * <p>A physical table or column name is not a value, so it cannot be a literal and cannot be a bind
 * parameter. It is written into the statement as an identifier, and the only defence available is
 * the dialect's quoting — {@code "…"} with an embedded quote doubled, backticks on MariaDB,
 * brackets on SQL Server. These tests hold the catalog to that: a name carrying a quote, a
 * semicolon and a comment marker must come out as a name, in every position the renderer puts one.
 *
 * <p>The positions matter individually, and the CTE column list says why. {@code
 * SqlQueryRenderer.toHeader} is documented as "the only identifier position that never reached
 * {@code normal}" — it once emitted a physical column name raw while the same name inside the block
 * body was quoted correctly. One position that forgets to quote is all an injection needs, so the
 * cases below walk FROM, the projection, the WHERE and the {@code WITH b (…)} list.
 */
public class CatalogIdentifierInjectionTest {

    /** Breaks out of {@code FROM "…"} the moment the quoting stops doubling the quote. */
    private static final String EVIL_TABLE = "customers\"; DROP TABLE orders; --";

    /** Breaks out of a projection into a second output column, and out of a WHERE into a filter. */
    private static final String EVIL_COLUMN = "company_name\", (SELECT 1) AS \"leaked";

    private static final String NORTHWIND_DB = "/ai/koryki/databases/northwind";
    private static final String NORTHWIND_MODEL = "/ai/koryki/databases/northwind/model";

    private static LinkResolver resolver;

    /**
     * A copy of the real Northwind catalog with the {@code customers} table and its {@code
     * company_name} column renamed to something a database would only ever hold if it had been
     * created quoted — which is exactly the name an attacker would choose.
     *
     * <p>Both sides are renamed. The physical {@link Schema} is what the resolver validates
     * against, so a {@link Model} pointing at a table {@code db.json} does not have is rejected as
     * an unknown table and never reaches a renderer — a real defence, but not the one under test
     * here.
     */
    @BeforeAll
    static void hostileCatalog() {
        Schema db = CatalogLoader.db(NORTHWIND_DB);
        Model model = CatalogLoader.model(NORTHWIND_MODEL, Locale.ENGLISH);

        for (Table t : db.getTables()) {
            if ("customers".equals(t.getName())) {
                t.setName(EVIL_TABLE);
                for (Column c : t.getColumns()) {
                    if ("company_name".equals(c.getName())) {
                        c.setName(EVIL_COLUMN);
                    }
                }
            }
        }
        for (Entity e : model.getEntities()) {
            if ("customers".equals(e.getName())) {
                e.setTable(EVIL_TABLE);
                for (Attribute a : e.getAttributes()) {
                    if ("company_name".equals(a.getName())) {
                        a.setColumn(EVIL_COLUMN);
                    }
                }
            }
        }
        resolver = new LinkResolver(Locale.ENGLISH, db, model, true);
    }

    @Test
    void aHostileTableAndColumnNameStayIdentifiersInEveryPosition() {
        String sql =
                Kql.sql(
                        resolver,
                        "FIND customers c FILTER c.company_name = 'x' FETCH c.company_name name");

        SqlSkeleton.assertContained(sql, "DROP", "SELECT 1", "leaked", ";", "--");
        assertTrue(sql.contains("\"customers\"\"; DROP TABLE orders; --\""), sql);
    }

    /**
     * The CTE column list — {@code WITH b (…) AS (…)} — takes a physical column name straight from
     * the catalog and is the position this project has already got wrong once.
     */
    @Test
    void theCteColumnListQuotesItsNamesToo() {
        String sql =
                Kql.sql(
                        resolver,
                        "WITH b AS (FIND customers c FETCH c.company_name) FIND b x FETCH"
                                + " x.company_name");

        SqlSkeleton.assertContained(sql, "DROP", "SELECT 1", "leaked");
    }

    /**
     * The mechanism, stated on its own so a dialect that overrides {@link SqlDialect#quote} has a
     * rule to meet rather than a golden to match. Doubling is the one escape all eight engines
     * share; a backslash is not an escape inside a quoted identifier anywhere.
     */
    @Test
    void quotingDoublesTheDelimiterAndNeverEscapesIt() {
        SqlDialect ansi = new SqlDialect() {};

        assertEquals("\"plain\"", ansi.quote("plain"));
        assertEquals("\"a\"\"b\"", ansi.quote("a\"b"));
        assertEquals("\"x\"\"; DROP TABLE t; --\"", ansi.quote("x\"; DROP TABLE t; --"));
    }

    /**
     * What decides that a name is quoted at all. Anything outside {@code [a-z_][a-z0-9_]*} has to
     * be — which is every name that could carry a payload, since a payload needs a character this
     * class does not contain.
     */
    @Test
    void anythingThatCouldCarryAPayloadNeedsQuoting() {
        assertTrue(Identifier.needsQuoting(EVIL_TABLE));
        assertTrue(Identifier.needsQuoting(EVIL_COLUMN));
        assertTrue(Identifier.needsQuoting("drop table orders; --"));
        assertTrue(Identifier.needsQuoting("a'b"));
        assertTrue(Identifier.needsQuoting(""));
        assertTrue(Identifier.needsQuoting(null));

        // the ordinary catalog name, which must stay bare or thousands of goldens move
        assertFalse(Identifier.needsQuoting("company_name"));
    }

    /**
     * A reserved word is a name the engine will not take bare. Not an injection on its own — it
     * cannot introduce a clause of the attacker's choosing — but it is the same decision, made by
     * the same method, and a dialect that stops quoting reserved words is one step from not quoting
     * the rest.
     */
    @Test
    void aReservedWordIsQuoted() {
        SqlDialect ansi = new SqlDialect() {};

        assertEquals("\"select\"", ansi.renderIdentifier(Identifier.lowercase, "select"));
        assertEquals("\"union\"", ansi.renderIdentifier(Identifier.lowercase, "union"));
        assertEquals("company_name", ansi.renderIdentifier(Identifier.lowercase, "company_name"));
    }
}
