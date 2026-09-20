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

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The rendered SQL with every string literal and every quoted identifier emptied out — what is left
 * is the statement's <em>structure</em>, and that is the thing an injection has to reach.
 *
 * <p><b>Why a scanner and not {@code assertTrue(sql.contains("''"))}.</b> A containment assertion
 * says the escaping fired somewhere; it does not say the payload stayed put. The interesting
 * failure is the opposite one — the escaping fires <em>and</em> a second copy of the text leaks out
 * unquoted, or the literal is closed one character too early and the tail becomes SQL. Both leave
 * the payload visible in the skeleton, so one assertion over the skeleton covers what a dozen
 * {@code contains} calls would approximate.
 *
 * <p>It is also what makes the tests readable as a policy rather than as goldens: <em>no part of an
 * author-supplied string may appear outside a literal</em>. A renderer change that keeps the same
 * meaning does not touch these tests; one that lets a character out does.
 *
 * <p>The lexing rule is the one every engine here shares: a literal runs to its closing delimiter,
 * and a doubled delimiter inside is one escaped character rather than the end. Backslash escapes
 * are deliberately <em>not</em> honoured — no dialect in this project emits them, and reading them
 * would make the scanner agree with a renderer that wrote one instead of catching it.
 */
final class SqlSkeleton {

    private SqlSkeleton() {}

    /**
     * {@code sql} with the body of every {@code '…'} and {@code "…"} removed, the delimiters kept.
     *
     * @throws AssertionError if a literal or quoted identifier is never closed — which is itself
     *     the classic injection signature, since an unbalanced quote means the payload took the
     *     statement with it
     */
    static String of(String sql) {
        StringBuilder skeleton = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c != '\'' && c != '"') {
                skeleton.append(c);
                i++;
                continue;
            }
            int end = closing(sql, i, c);
            if (end < 0) {
                fail(
                        "unterminated "
                                + (c == '\'' ? "string literal" : "quoted identifier")
                                + " from offset "
                                + i
                                + " — the rendered SQL does not parse:\n"
                                + sql);
            }
            skeleton.append(c).append(c);
            i = end + 1;
        }
        return skeleton.toString();
    }

    /** Index of the delimiter that closes the one at {@code open}, or -1 if there is none. */
    private static int closing(String sql, int open, char delimiter) {
        int i = open + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == delimiter) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == delimiter) {
                    i += 2; // a doubled delimiter is one escaped character, not the end
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Asserts that none of {@code payload} reaches the statement's structure — i.e. every one of
     * them is still inside the literal or identifier the author wrote it in.
     */
    static void assertContained(String sql, String... payload) {
        String skeleton = of(sql);
        for (String p : payload) {
            if (skeleton.contains(p)) {
                fail(
                        "'"
                                + p
                                + "' escaped its literal — it stands in the statement itself:\n"
                                + skeleton
                                + "\n\nfull SQL:\n"
                                + sql);
            }
        }
    }
}
