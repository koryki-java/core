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
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.GrammarException;
import ai.koryki.antlr.Interval;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A syntax error says where it is, in the text the user wrote, and what was probably meant. */
class KqlSyntaxErrorTest {

    private static List<Interval> panic(String kql) throws IOException {
        KQLReader reader = new KQLReader(kql);
        reader.getQuery();
        return reader.getPanic();
    }

    @Test
    void parserErrorCarriesLineAndColumnNotTokenIndices() throws IOException {
        // FETCH starts at column 28 (1-based) of line 2
        Interval i =
                panic("FIND customers c\nFILTER c.country BETWEEN 1 FETCH c.company_name").get(0);

        assertTrue(i.hasPosition());
        assertEquals(2, i.getLine());
        assertEquals(28, i.getCol());
        assertTrue(i.toString().startsWith("line 2:28 "), i.toString());
    }

    @Test
    void lowerCaseKeywordIsNamed() throws IOException {
        Interval i = panic("find customers c FETCH c.company_name").get(0);

        assertEquals(1, i.getLine());
        assertEquals(1, i.getCol());
        assertTrue(i.getMsg().contains("keywords are upper-case: use FIND"), i.getMsg());
        assertEquals(List.of("FIND"), i.getDidYouMean());
    }

    @Test
    void expectedKeywordsAreListedWithoutTokenNames() throws IOException {
        Interval i = panic("find customers c FETCH c.company_name").get(0);

        assertTrue(i.getExpected().contains("FIND"), i.getExpected().toString());
        assertFalse(i.getExpected().stream().anyMatch(n -> n.contains("SELECTOR")));
    }

    @Test
    void mistypedKeywordIsSuggestedWithColumnOneBased() throws IOException {
        Interval i = panic("FIND customers c FILTR c.country = 'x' FETCH c.company_name").get(0);

        assertEquals(1, i.getLine());
        assertEquals(18, i.getCol());
        assertTrue(i.getMsg().contains("did you mean FILTER?"), i.getMsg());
        assertEquals(List.of("FILTER"), i.getDidYouMean());
    }

    @Test
    void aMistypedWordIsReportedOnceNotOncePerLetter() throws IOException {
        List<Interval> panic = panic("FIND customers c FILTR c.country = 'x' FETCH c.company_name");

        assertEquals(1, panic.size(), panic.toString());
    }

    @Test
    void everyUnrecognizedInputIsReportedAndParserEchoesAreDropped() throws IOException {
        List<Interval> panic = panic("FIND customers c FILTR c.country = 'x' FETC c.company_name");

        assertEquals(2, panic.size(), panic.toString());
        assertEquals(List.of("FILTER"), panic.get(0).getDidYouMean());
        assertEquals(List.of("FETCH"), panic.get(1).getDidYouMean());
    }

    @Test
    void abortModeStillThrowsOnTheFirstLexerError() {
        GrammarException e =
                assertThrows(
                        GrammarException.class,
                        () ->
                                new KQLReader("FIND customers c FILTR c.country = 'x'", true)
                                        .getQuery());

        assertEquals(18, e.getPos());
        assertEquals(List.of("FILTER"), e.getDidYouMean());
    }

    @Test
    void messagesUseTheWritersWordsNotTheGrammars() throws IOException {
        String msg =
                panic("FIND customers c FILTER c.country BETWEEN 1 FETCH c.company_name")
                        .get(0)
                        .getMsg();

        assertTrue(msg.startsWith("unexpected 'FETCH'"), msg);
        assertFalse(msg.contains("SELECTOR_"), msg);
        assertFalse(msg.contains("mismatched input"), msg);
        assertTrue(msg.contains("AND"), msg);
    }

    @Test
    void missingTokenSaysWhereItBelongs() throws IOException {
        String msg =
                panic("FIND customers c FILTER (c.country = 'x' FETCH c.company_name")
                        .get(0)
                        .getMsg();

        assertEquals("missing ')' before 'FETCH'", msg);
    }

    @Test
    void ruleStackIsNotInTheAbortMessage() {
        GrammarException e =
                assertThrows(
                        GrammarException.class,
                        () -> new KQLReader("find customers c", true).getQuery());

        assertFalse(e.getMessage().contains("rule stack"), e.getMessage());
        assertFalse(e.getRuleStack().isEmpty());
    }

    @Test
    void aLowerCaseIdentifierIsNotMistakenForAKeyword() throws IOException {
        // "orders" is no keyword typo; nothing is suggested
        List<Interval> panic = panic("FIND customers c orders FETCH c.company_name");

        assertTrue(panic.stream().allMatch(i -> i.getDidYouMean().isEmpty()));
    }
}
