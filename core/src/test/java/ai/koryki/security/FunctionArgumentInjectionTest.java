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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.functions.SqlTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The third injection surface, and the one that actually leaked: a function argument that the
 * dialect reads rather than renders.
 *
 * <p>Almost every function in the catalog is declared as a {@link SqlTemplate} — {@code
 * "strpos({0}, {1})"} — and a template only ever splices an <em>already rendered</em> operand into
 * a fixed string. An operand that is a string literal is a quoted, escaped string literal by the
 * time the template sees it, so there is nothing to get wrong.
 *
 * <p>The exceptions are the handful of functions whose dialect rendering <em>inspects</em> an
 * argument: it takes the rendered literal back apart, reads the text inside, and builds SQL from
 * it. That unquoting is the dangerous moment, because whatever comes out is no longer inside a
 * literal — and if it is then written into the statement as-is, every character of an author's KQL
 * string is SQL.
 *
 * <p>That is precisely what {@code to_interval(n, unit)} did on the DuckDB dialect family. A unit
 * the switch did not recognise fell through to {@code "INTERVAL (" + value + ") " + bare}, with
 * {@code bare} the unquoted body of the author's string — so a KQL string literal was an opening
 * into the SELECT list, and a payload written there ran. The fix is a shape check on the unit, and
 * these tests are what keeps it.
 *
 * <p>The format-mask functions do the same unquoting and are safe, which is worth pinning rather
 * than assuming: {@code FormatMask.translate} puts the quotes back and passes unrecognised
 * characters — the doubled quotes of an escaped literal among them — through untouched.
 */
public class FunctionArgumentInjectionTest {

    @Test
    void aKnownIntervalUnitStillRenders() {
        assertTrue(
                Kql.sql("FIND orders o FETCH to_interval(30, 'DAY') due").contains("to_days(30)"));
        assertTrue(
                Kql.sql("FIND orders o FETCH to_interval(3, 'MONTHS') due")
                        .contains("to_months(3)"));
    }

    /**
     * The pass-through the switch deliberately keeps: DuckDB knows units this list does not
     * (DECADE, CENTURY), and a word cannot leave the position the renderer put it in. Stated as a
     * test so the fix below is read as "reject what is not a word", not "reject what is not in the
     * list" — the narrower rule would be a behaviour change dressed as a security fix.
     */
    @Test
    void anUnknownButWordShapedUnitStillPassesThrough() {
        assertTrue(
                Kql.sql("FIND orders o FETCH to_interval(2, 'DECADE') d")
                        .contains("INTERVAL (2) DECADE"));
    }

    /**
     * The regression, with the payload that actually ran rather than one that merely broke the
     * syntax. Every character of the unit reached the statement unquoted, so the KQL
     *
     * <pre>
     * FIND orders o FETCH to_interval(1, 'DAY, (SELECT company_name FROM customers LIMIT 1) AS leaked')
     * </pre>
     *
     * rendered — and DuckDB ran —
     *
     * <pre>
     * SELECT
     *   INTERVAL (1) DAY, (SELECT COMPANY_NAME FROM CUSTOMERS LIMIT 1) AS LEAKED
     * FROM
     *  orders o
     * </pre>
     *
     * an answer with a column the query never asked for, read from a table it never named.
     */
    @Test
    void anIntervalUnitCarryingSqlIsRefused() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                Kql.sql(
                                        "FIND orders o FETCH to_interval(1, 'DAY, (SELECT"
                                                + " company_name FROM customers LIMIT 1) AS leaked')"));
        assertTrue(
                e.getMessage() != null && e.getMessage().contains("to_interval"),
                "the refusal should name the function: " + e.getMessage());
    }

    /** The same hole reached from a filter rather than a projection. */
    @Test
    void anIntervalUnitCarryingSqlIsRefusedInAFilterToo() {
        assertThrows(
                KorykiaiException.class,
                () ->
                        Kql.sql(
                                "FIND orders o FILTER o.order_date > to_date('2020-01-01') +"
                                        + " to_interval(1, 'DAY) FROM orders WHERE 1=1 --') FETCH"
                                        + " o.order_id"));
    }

    /** A quote is not a word character either — the shorter payload has to go the same way. */
    @Test
    void anIntervalUnitCarryingAQuoteIsRefused() {
        assertThrows(
                KorykiaiException.class,
                () -> Kql.sql("FIND orders o FETCH to_interval(1, 'DAY\\'') d"));
    }

    /**
     * A format mask is unquoted, rewritten token by token and quoted again. An escaped quote inside
     * it stays one escaped quote: the mask is data on the way in and data on the way out.
     */
    @Test
    void aFormatMaskCarryingAQuoteStaysOneLiteral() {
        String sql =
                Kql.sql(
                        "FIND orders o FETCH to_char(o.order_date, 'YYYY\\' || (SELECT 1) ||"
                                + " \\'MM') x");

        SqlSkeleton.assertContained(sql, "SELECT 1", "||");
        assertTrue(sql.contains("strftime("), sql);
    }

    /**
     * A template is parsed once into segments and then filled; the filled-in text is never
     * re-scanned. So an operand that happens to look like a placeholder is an operand.
     *
     * <p>Worth a test of its own because the obvious implementation — a chain of {@code
     * String.replace("{0}", …)} — has exactly this bug, and it is a real one: a KQL literal {@code
     * '{1}'} would otherwise be replaced by the value of another argument.
     */
    @Test
    void aFilledOperandIsNotRescannedForPlaceholders() {
        SqlTemplate t = new SqlTemplate("{0} = {1}");

        assertEquals("'{1}' = 'x'", t.fill(List.of("'{1}'", "'x'")));
        assertEquals("'{0}{1}' = 'x'", t.fill(List.of("'{0}{1}'", "'x'")));
    }

    /**
     * The grammar's escape hatch: an operator with no catalog entry is written into the SQL
     * verbatim. What keeps that safe is not the renderer but the lexer — a custom operator is an
     * {@code ID}, i.e. {@code [a-z_][a-z0-9_]*}, and no payload fits in that alphabet.
     */
    @Test
    void aCustomOperatorIsBoundedByTheLexersIdentifierClass() {
        // a genuine foreign operator passes through, lower case and one word
        assertTrue(
                Kql.sql("FIND customers c FILTER c.company_name simil 'x' FETCH c.city")
                        .contains("simil"));

        // anything that is not that word class is not an operator, it is a syntax error
        assertThrows(
                KorykiaiException.class,
                () -> Kql.sql("FIND customers c FILTER c.company_name ; DROP TABLE orders; --"));
    }
}
