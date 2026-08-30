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

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name rules, asked directly.
 *
 * <p>{@link HostileIdentifierTest} in {@code :tools} asks them through a rendered statement, which
 * is the right level for "does this position get quoted". It is the wrong level for the rules
 * themselves: several of them are reachable from lookup keys rather than from SQL, and one of them
 * only misbehaves under a default locale no rendering test would think to set.
 */
class IdentifierTest {

    @Test
    void aPlainLowercaseNameNeedsNothing() {
        assertFalse(Identifier.needsQuoting("order_date"));
        assertFalse(Identifier.needsQuoting("betrag"));
        assertFalse(Identifier.needsQuoting("_private"));
        assertFalse(Identifier.needsQuoting("x1"));
    }

    @Test
    void anythingOutsideTheBarePatternNeedsQuoting() {
        assertTrue(Identifier.needsQuoting("Umsatz 2026"), "space");
        assertTrue(Identifier.needsQuoting("order-date"), "hyphen");
        assertTrue(Identifier.needsQuoting("betrag €"), "non-ascii");
        assertTrue(Identifier.needsQuoting("2026"), "leading digit");
        assertTrue(Identifier.needsQuoting(""), "empty");
        assertTrue(Identifier.needsQuoting(null), "null");
    }

    /**
     * Mixed case needs quoting because bare it would be folded, and the two engines fold opposite
     * ways: {@code Betrag} reaches {@code BETRAG} on Oracle and {@code betrag} on PostgreSQL.
     */
    @Test
    void aNameThatIsNotAllLowerCaseNeedsQuoting() {
        assertTrue(Identifier.needsQuoting("Betrag"));
        assertTrue(Identifier.needsQuoting("SID"));
        assertFalse(Identifier.needsQuoting("sid"));
    }

    /**
     * Keywords are the dialect's business, not this method's.
     *
     * <p>The check used to live here against one shared list, which could only ever be wrong in one
     * of two directions - see {@link SqlDialect#isReserved}.
     */
    @Test
    void keywordsAreNotJudgedBySyntax() {
        assertFalse(Identifier.needsQuoting("order"), "a keyword is still syntactically plain");
        assertTrue(Identifier.isStandardReserved("order"));
        assertTrue(Identifier.isStandardReserved("ORDER"), "asked case-insensitively");
        assertFalse(Identifier.isStandardReserved("betrag"));
        assertFalse(Identifier.isStandardReserved("date"), "Oracle's, not the standard's");
    }

    /**
     * A name that merely contains a quote keeps it.
     *
     * <p>The strip used to take the two ends independently, so {@code He said "hi"} came back as
     * {@code He said "hi} - a different column, and nothing to say so. Both ends are one pair or
     * they are nothing.
     */
    @Test
    void strippingRedundantQuotesDoesNotTruncateAName() {
        assertEquals("He said \"hi\"", Identifier.bare(Identifier.quoted, "He said \"hi\""));
        assertEquals("trailing\"", Identifier.bare(Identifier.quoted, "trailing\""));
        assertEquals("\"leading", Identifier.bare(Identifier.quoted, "\"leading"));
        assertEquals("\"", Identifier.bare(Identifier.quoted, "\""), "one character is not a pair");
        assertEquals("wrapped", Identifier.bare(Identifier.quoted, "\"wrapped\""), "a real pair goes");
    }

    /**
     * Case folding does not depend on the JVM's default locale.
     *
     * <p>Turkish is the case that breaks it: {@code "CITY".toLowerCase()} is {@code "cıty"} with a
     * dotless i. That reaches further than rendering — {@link LinkResolver#findRelation} and
     * {@link SqlSelectRenderer} fold entity names this way to build <em>lookup keys</em>, so under
     * a Turkish default the key stopped matching the catalog and the relation was simply not
     * found. The rest of the codebase already passes {@link Locale#ROOT}; this file did not.
     */
    @Test
    void foldingIsIndependentOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            // Proof the locale took effect, so the assertions below are not vacuous: this is the
            // dotless i, and it is what the unqualified call inside Identifier used to produce.
            assertEquals("cıty", "CITY".toLowerCase());

            assertEquals("city", Identifier.normal(Identifier.lowercase, "CITY"));
            assertEquals("city", Identifier.bare(Identifier.lowercase, "CITY"));
            assertEquals("CITY", Identifier.normal(Identifier.normal, "city"));
            assertTrue(Identifier.isStandardReserved("IN"));
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * An all-digit name is quoted; an empty one is left empty.
     *
     * <p>The pattern was {@code \d*}, which matches the empty string as well, so a name reduced to
     * nothing came back as a bare pair of quote characters — and these callers are lookup keys,
     * where that pair matches nothing at all.
     */
    @Test
    void anEmptyNameIsNotTurnedIntoAPairOfQuotes() {
        assertEquals("", Identifier.normal(Identifier.lowercase, ""));
        assertEquals("\"2026\"", Identifier.normal(Identifier.lowercase, "2026"));
    }
}
