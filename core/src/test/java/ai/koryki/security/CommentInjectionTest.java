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

import ai.koryki.antlr.Text;
import org.junit.jupiter.api.Test;

/**
 * The fourth surface, and the one that is easiest to overlook because it is not a value at all: the
 * query's own description.
 *
 * <p>A KQL query may open with a comment, and that comment becomes {@code Query.description}, which
 * {@code SqlQueryRenderer} writes back out as a leading {@code --} comment so the generated SQL
 * says what it came from. The text is the author's, and a block comment {@code /* … *}{@code /}
 * accepts any character the lexer's {@code .*?} matches.
 *
 * <p>A line comment ends at a line break — and SQL's idea of a line break is not just {@code \n}.
 * Prefixing every {@code \n} with {@code --} therefore left a bare carriage return sitting inside
 * the comment, and everything after it was SQL, in front of the generated query. Measured on
 * DuckDB: {@code --note\rSELECT 42 AS injected} answers one column, named {@code injected}.
 *
 * <p>The position is the interesting part. This injection lands <em>before</em> the query rather
 * than inside it, so it is not limited to bending a filter — it can put a whole statement there and
 * comment the real one out. Nothing about escaping values helps, because no value is involved.
 */
public class CommentInjectionTest {

    /** The payload: valid SQL, so any line of it that is not commented out is a statement. */
    private static final String INJECTED = "SELECT 42 AS injected";

    @Test
    void aCarriageReturnInsideADescriptionCannotEndTheSqlComment() {
        String sql =
                Kql.sql("/* note\r" + INJECTED + " -- */ FIND customers c FETCH c.customer_id");

        assertPayloadStaysCommented(sql);
        assertTrue(sql.contains("--note"), sql);
    }

    /** The ordinary case the CR one broke out of: a genuine multi-line description. */
    @Test
    void everyLineOfAMultiLineDescriptionIsCommented() {
        String sql =
                Kql.sql("/* first line\n" + INJECTED + " */ FIND customers c FETCH c.customer_id");

        assertPayloadStaysCommented(sql);
    }

    /** CRLF, the Windows spelling — one line break, not two, and still commented. */
    @Test
    void aCrlfIsOneLineBreakAndIsCommented() {
        String sql = Kql.sql("/* note\r\n" + INJECTED + " */ FIND customers c FETCH c.customer_id");

        assertPayloadStaysCommented(sql);
        assertFalse(sql.contains("\r"), "a bare CR survived into the SQL:\n" + sql);
    }

    /**
     * The rule itself, away from the transpiler. {@code \R} is every line boundary Java knows, so a
     * marker is written after each of them — which is what makes the cases above hold for
     * separators no test enumerates.
     */
    @Test
    void lineCommentMarksEveryKindOfLineBreak() {
        assertEquals("--a\n--b", Text.lineComment("--", "a\rb"));
        assertEquals("--a\n--b", Text.lineComment("--", "a\nb"));
        assertEquals("--a\n--b", Text.lineComment("--", "a\r\nb"));
        assertEquals("--a\n--b\n--c", Text.lineComment("--", "a\rb\nc"));
        assertEquals("--plain", Text.lineComment("--", "plain"));
        // IQL uses the same helper with its own marker
        assertEquals("//a\n//b", Text.lineComment("//", "a\rb"));
    }

    /**
     * The payload must appear, and every line it appears on must be a comment line.
     *
     * <p>Split on {@code \R} rather than on {@code \n}, because that is how an engine reads the
     * SQL: a line the generator thinks is one line but the engine reads as two is the whole bug.
     * Asserting the payload is present as well keeps the test from passing because a typo made it
     * look for something the SQL never had.
     */
    private static void assertPayloadStaysCommented(String sql) {
        boolean seen = false;
        for (String line : sql.split("\\R", -1)) {
            if (!line.contains(INJECTED)) {
                continue;
            }
            seen = true;
            assertTrue(
                    line.startsWith("--"),
                    "the description broke out of its comment — this line is SQL: '"
                            + line
                            + "'\n\nfull SQL:\n"
                            + sql);
        }
        assertTrue(seen, "the payload is not in the rendered SQL at all:\n" + sql);
    }
}
