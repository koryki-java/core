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
package ai.koryki.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A nested value from the read boundary to its text: the decoder turns whatever the driver hands
 * over (a {@code java.sql.Array}, a {@code java.sql.Struct}, a Map, an object array) into plain
 * lists and maps typed leaf by leaf, and {@link LocaleFormat} writes those as JSON.
 */
class NestedValueTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};
    private static final CoreDecoder DECODER = new CoreDecoder(ZoneId.of("UTC"));
    private static final LocaleFormat FORMAT = new LocaleFormat(null);

    private static TypeDescriptor type(String family, String dialectType) {
        Column c = new Column("c");
        c.setTypeFamily(family);
        c.setDialectType(dialectType);
        return PARSER.parse(c);
    }

    private static ColumnInfo info(TypeDescriptor td) {
        return new ColumnInfo() {
            @Override
            public void setHeader(String header) {}

            @Override
            public TypeDescriptor getTypeDescriptor() {
                return td;
            }
        };
    }

    private static Object decode(Object v, TypeDescriptor td) {
        return DECODER.decode(v, info(td));
    }

    private static String text(Object v, TypeDescriptor td) {
        return FORMAT.format(decode(v, td), td);
    }

    /** A java.sql.Array over the given elements, as a driver would hand it over. */
    private static java.sql.Array sqlArray(Object... elements) {
        return (java.sql.Array)
                Proxy.newProxyInstance(
                        NestedValueTest.class.getClassLoader(),
                        new Class<?>[] {java.sql.Array.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getArray")) {
                                return elements;
                            }
                            throw new SQLException("not needed: " + method.getName());
                        });
    }

    private static java.sql.Struct sqlStruct(Object... attributes) {
        return (java.sql.Struct)
                Proxy.newProxyInstance(
                        NestedValueTest.class.getClassLoader(),
                        new Class<?>[] {java.sql.Struct.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getAttributes")) {
                                return attributes;
                            }
                            throw new SQLException("not needed: " + method.getName());
                        });
    }

    // ---- decoding -----------------------------------------------------------------------------

    @Test
    void anSqlArrayBecomesAList() {
        Object v = decode(sqlArray(1, 2, null), type("ARRAY", "INTEGER[]"));

        assertEquals(Arrays.asList(1, 2, null), v);
    }

    @Test
    void anSqlStructBecomesAMapKeyedByFieldName() {
        TypeDescriptor td = type("STRUCT", "STRUCT(w DOUBLE, \"Odd Name\" VARCHAR)");

        Object v = decode(sqlStruct(1.5, "x"), td);

        assertEquals(Map.of("w", 1.5, "Odd Name", "x"), v);
        assertEquals(List.of("w", "Odd Name"), new ArrayList<>(((Map<?, ?>) v).keySet()));
    }

    @Test
    void aMapKeepsItsOrderAndDecodesKeysAndValues() {
        Map<Object, Object> driver = new LinkedHashMap<>();
        driver.put(2, java.sql.Date.valueOf("2026-05-17"));
        driver.put(1, null);

        Object v = decode(driver, type("MAP", "MAP(INTEGER, DATE)"));

        assertEquals(List.of(2, 1), new ArrayList<>(((Map<?, ?>) v).keySet()));
        assertEquals(LocalDate.of(2026, 5, 17), ((Map<?, ?>) v).get(2));
    }

    @Test
    void leavesAreDecodedByTheirOwnTypeAtAnyDepth() {
        TypeDescriptor td =
                type("ARRAY", "STRUCT(ts TIMESTAMP, price DECIMAL(10,2), tags VARCHAR[])[]");
        Object driver =
                sqlArray(
                        sqlStruct(
                                java.sql.Timestamp.valueOf("2026-05-17 14:30:45"),
                                new BigDecimal("9.99"),
                                sqlArray("x")));

        Object v = decode(driver, td);

        Map<?, ?> first = (Map<?, ?>) ((List<?>) v).get(0);
        assertEquals(LocalDateTime.of(2026, 5, 17, 14, 30, 45), first.get("ts"));
        assertEquals(new BigDecimal("9.99"), first.get("price"));
        assertEquals(List.of("x"), first.get("tags"));
    }

    @Test
    void primitiveArraysAndObjectArraysAreAccepted() {
        assertEquals(List.of(1, 2), decode(new int[] {1, 2}, type("ARRAY", "INTEGER[]")));
        assertEquals(List.of("a"), decode(new Object[] {"a"}, type("ARRAY", "VARCHAR[]")));
    }

    @Test
    void theDecodedContainersCannotBeChanged() {
        Object v = decode(sqlArray(1), type("ARRAY", "INTEGER[]"));

        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) v).add(2));
    }

    @Test
    void aStructWithTheWrongNumberOfFieldsIsRefused() {
        TypeDescriptor td = type("STRUCT", "STRUCT(a INTEGER, b INTEGER)");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> decode(sqlStruct(1), td));

        assertTrue(e.getMessage().contains("STRUCT(a:INTEGER,b:INTEGER)"), e.getMessage());
    }

    @Test
    void aValueOfTheWrongShapeIsRefusedWithItsClass() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> decode("text", type("MAP", "MAP(VARCHAR, INTEGER)")));

        assertTrue(e.getMessage().contains("java.lang.String"), e.getMessage());
    }

    @Test
    void nullStaysNull() {
        assertEquals(null, decode(null, type("ARRAY", "INTEGER[]")));
    }

    // ---- JSON ---------------------------------------------------------------------------------

    @Test
    void aListIsAJsonArray() {
        assertEquals(
                "[\"red\",\"green\"]", text(sqlArray("red", "green"), type("ARRAY", "VARCHAR[]")));
        assertEquals("[]", text(sqlArray(), type("ARRAY", "VARCHAR[]")));
        assertEquals("[1,null,3]", text(sqlArray(1, null, 3), type("ARRAY", "INTEGER[]")));
    }

    @Test
    void aStructIsAJsonObjectInFieldOrder() {
        TypeDescriptor td = type("STRUCT", "STRUCT(w DOUBLE, \"Odd Name\" INTEGER, b BOOLEAN)");

        assertEquals("{\"w\":1.5,\"Odd Name\":2,\"b\":true}", text(sqlStruct(1.5, 2, true), td));
    }

    @Test
    void aMapIsAJsonObjectWithKeysAsText() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(1, "one");
        m.put(2, null);

        assertEquals("{\"1\":\"one\",\"2\":null}", text(m, type("MAP", "MAP(INTEGER, VARCHAR)")));
    }

    @Test
    void anElementThatIsTheWordNullIsNotNull() {
        // the whole point of leaving toString(): [null] and ["null"] were the same text
        assertEquals("[null]", text(sqlArray((Object) null), type("ARRAY", "VARCHAR[]")));
        assertEquals("[\"null\"]", text(sqlArray("null"), type("ARRAY", "VARCHAR[]")));
    }

    @Test
    void textIsEscapedAsJsonRequires() {
        String all = "a\"b\\c\nd\te\u0001f";

        assertEquals(
                "[\"a\\\"b\\\\c\\nd\\te\\u0001f\"]",
                text(sqlArray(all), type("ARRAY", "VARCHAR[]")));
    }

    @Test
    void numbersAreNeverLocaleFormatted() {
        TypeDescriptor td = type("ARRAY", "DECIMAL(10,2)[]");
        LocaleFormat german = new LocaleFormat(Locale.GERMANY);

        Object v = decode(sqlArray(new BigDecimal("1234.5"), new BigDecimal("0.1")), td);

        assertEquals("[1234.50,0.10]", german.format(v, td));
    }

    @Test
    void aDecimalKeepsItsScaleInsideAContainer() {
        assertEquals(
                "[24.50]",
                text(sqlArray(new BigDecimal("24.5")), type("ARRAY", "DECIMAL(10,2)[]")));
    }

    @Test
    void nanAndInfinityAreStringsBecauseJsonHasNoSuchNumber() {
        assertEquals(
                "[\"NaN\",\"Infinity\",1.5]",
                text(
                        sqlArray(Double.NaN, Double.POSITIVE_INFINITY, 1.5),
                        type("ARRAY", "DOUBLE[]")));
    }

    @Test
    void temporalLeavesAreIsoStrings() {
        TypeDescriptor td = type("STRUCT", "STRUCT(ts TIMESTAMP, day DATE)");

        assertEquals(
                "{\"ts\":\"2026-05-17T14:30:45\",\"day\":\"2026-05-17\"}",
                text(
                        sqlStruct(
                                java.sql.Timestamp.valueOf("2026-05-17 14:30:45"),
                                java.sql.Date.valueOf("2026-05-17")),
                        td));
    }

    @Test
    void aJsonLeafIsEmbeddedAsItIs() {
        TypeDescriptor td = type("STRUCT", "STRUCT(doc JSON, n INTEGER)");

        assertEquals("{\"doc\":{\"a\":[1]},\"n\":1}", text(sqlStruct("{\"a\":[1]}", 1), td));
    }

    @Test
    void aContainerThatWasNotDecodedIsAStringNotAGuess() {
        TypeDescriptor td = type("ARRAY", "INTEGER[]");

        // no column type at the read boundary: the raw driver object reaches the formatter
        assertEquals(
                "\"driver text\"",
                FORMAT.format(
                        new Object() {
                            @Override
                            public String toString() {
                                return "driver text";
                            }
                        },
                        td));
    }

    @Test
    void aNullContainerIsAnEmptyCell() {
        assertEquals("", FORMAT.format(null, type("ARRAY", "INTEGER[]")));
    }
}
