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
package ai.koryki.snowflake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeSyntax;
import ai.koryki.jdbc.ColumnInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The JSON text the Snowflake driver returns for containers, read along the column's type. */
class SnowflakeDecoderTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static TypeDescriptor type(String family, String dialectType) {
        return TypeSyntax.looksComplex(dialectType)
                ? TypeSyntax.parse(dialectType, PARSER::parseLeaf)
                : PARSER.parse("column 'c'", family, null, dialectType);
    }

    private final SnowflakeDecoder decoder = new SnowflakeDecoder(ZoneId.of("UTC"));

    private static ColumnInfo info(TypeDescriptor type) {
        return new ColumnInfo() {
            @Override
            public void setHeader(String header) {}

            @Override
            public TypeDescriptor getTypeDescriptor() {
                return type;
            }
        };
    }

    private Object decode(String text, TypeDescriptor type) {
        return decoder.decode(text, info(type));
    }

    @Test
    void aListKeepsItsNullsAndItsOrder() {
        TypeDescriptor type = type("ARRAY", "INTEGER[]");

        assertEquals(
                java.util.Arrays.asList(null, 5L, null),
                decode("[\n  null,\n  5,\n  null\n]", type));
    }

    @Test
    void aStructIsReadByFieldNameInTheOrderOfTheType() {
        TypeDescriptor type = type("STRUCT", "STRUCT(w DOUBLE, h DOUBLE)");

        Object value = decode("{\n  \"h\": 2.5,\n  \"w\": 1.5\n}", type);

        assertEquals(List.of("w", "h"), List.copyOf(((Map<?, ?>) value).keySet()));
        assertEquals(1.5, ((Map<?, ?>) value).get("w"));
    }

    @Test
    void aMissingFieldIsNull() {
        TypeDescriptor type = type("STRUCT", "STRUCT(a INTEGER)");

        assertNull(((Map<?, ?>) decode("{}", type)).get("a"));
    }

    @Test
    void aMapWithIntegerKeysGetsIntegerKeys() {
        TypeDescriptor type = type("MAP", "MAP(INTEGER, VARCHAR)");

        assertEquals(
                Map.of(1L, "one", 2L, "two"), decode("{\"1\": \"one\", \"2\": \"two\"}", type));
    }

    @Test
    void temporalAndDecimalLeavesAreTheirOwnValues() {
        TypeDescriptor type = type("ARRAY", "STRUCT(d DATE, p DECIMAL(10,2), t TIMESTAMP)[]");

        Object value =
                decode(
                        "[{\"d\": \"2026-05-17\", \"p\": 9.99, \"t\": \"2026-05-17 14:30:45.000\"}]",
                        type);

        Map<?, ?> row = (Map<?, ?>) ((List<?>) value).get(0);
        assertEquals(LocalDate.of(2026, 5, 17), row.get("d"));
        assertEquals(new BigDecimal("9.99"), row.get("p"));
        assertEquals(LocalDateTime.of(2026, 5, 17, 14, 30, 45), row.get("t"));
    }

    @Test
    void aJsonValueIsOnlyCompacted() {
        assertEquals(
                "{\"a\":{\"b\":[5,6]},\"n\":1}",
                decode(
                        "{\n  \"a\": {\n    \"b\": [\n      5,\n      6\n    ]\n  },\n  \"n\": 1\n}",
                        type("JSON", "JSON")));
    }
}
