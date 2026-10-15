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
package ai.koryki.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The text PostgreSQL writes for a composite value and for an array: what the driver hands over.
 */
class PostgresTextTest {

    @Test
    void aRecordIsItsFields() {
        assertEquals(Arrays.asList("1.5", "2.5"), PostgresText.record("(1.5,2.5)"));
    }

    @Test
    void quotedFieldsMayHoldCommasParenthesesAndBlanks() {
        assertEquals(
                Arrays.asList("Main St 1", "(52.52,13.405)", "{\"+49 30 1\",\"+49 30 2\"}"),
                PostgresText.record(
                        "(\"Main St 1\",\"(52.52,13.405)\",\"{\"\"+49 30 1\"\",\"\"+49 30 2\"\"}\")"));
    }

    @Test
    void anUnquotedEmptyFieldIsNullAndAQuotedOneIsAnEmptyString() {
        assertEquals(Arrays.asList("C-3", null, null, null), PostgresText.record("(C-3,,,)"));
        assertEquals(Arrays.asList("", "x"), PostgresText.record("(\"\",x)"));
    }

    @Test
    void aQuoteIsDoubledOrEscapedByABackslash() {
        assertEquals(Arrays.asList("say \"hi\""), PostgresText.record("(\"say \"\"hi\"\"\")"));
        assertEquals(Arrays.asList("say \"hi\""), PostgresText.record("(\"say \\\"hi\\\"\")"));
        assertEquals(Arrays.asList("a\\b"), PostgresText.record("(\"a\\\\b\")"));
    }

    @Test
    void aSingleFieldIsStillARecord() {
        assertEquals(Arrays.asList("1"), PostgresText.record("(1)"));
        assertEquals(Arrays.asList((String) null), PostgresText.record("()"));
    }

    @Test
    void anArrayIsItsElements() {
        assertEquals(List.of("x", "y"), PostgresText.array("{x,y}"));
        assertEquals(List.of(), PostgresText.array("{}"));
    }

    @Test
    void anUnquotedNullIsNullAndAQuotedOneIsText() {
        assertEquals(Arrays.asList("x", null, "z"), PostgresText.array("{x,NULL,z}"));
        assertEquals(List.of("NULL"), PostgresText.array("{\"NULL\"}"));
    }

    @Test
    void quotedElementsMayHoldCommasAndBraces() {
        assertEquals(
                List.of("+49 30 1", "a,b", "{c}"),
                PostgresText.array("{\"+49 30 1\",\"a,b\",\"{c}\"}"));
    }

    @Test
    void innerDimensionsAreLists() {
        assertEquals(
                List.of(List.of("1", "2"), List.of("3", "4")), PostgresText.array("{{1,2},{3,4}}"));
    }

    @Test
    void whatIsNoRecordOrArrayIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PostgresText.record("1.5,2.5"));
        assertThrows(IllegalArgumentException.class, () -> PostgresText.array("1,2"));
    }
}
