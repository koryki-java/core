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
package ai.koryki.catalog.schema.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A JSON column whose members the catalog declares: the tree they make, and what is refused. */
class ShapedJsonTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Column member(String name, String family) {
        Column c = new Column(name);
        c.setTypeFamily(family);
        return c;
    }

    private static Column object(String name, Column... fields) {
        Column c = member(name, "STRUCT");
        c.setFields(List.of(fields));
        return c;
    }

    private static Column list(String name, Column element) {
        Column c = member(name, "ARRAY");
        c.setElement(element);
        return c;
    }

    private static Column json(Column... fields) {
        Column c = new Column("doc");
        c.setTypeFamily("JSON");
        c.setDialectType("JSON");
        c.setFields(List.of(fields));
        return c;
    }

    @Test
    void membersMakeAStructAListAndAMap() {
        Column map = member("m", "MAP");
        map.setValue(member(null, "TEXT"));

        TypeDescriptor type =
                PARSER.parse(
                        json(
                                member("n", "INTEGER"),
                                object("a", list("b", member(null, "INTEGER"))),
                                map));

        assertEquals(
                "JSON(STRUCT(n:INTEGER,a:STRUCT(b:ARRAY(INTEGER)),m:MAP(TEXT,TEXT)))",
                type.portable());
    }

    @Test
    void theColumnStaysJsonAndWithoutMembersHasNoShape() {
        TypeDescriptor shaped = PARSER.parse(json(member("n", "INTEGER")));
        assertEquals("JSON", shaped.getTypeFamily().name());
        assertEquals("STRUCT(n:INTEGER)", shaped.shape().portable());

        Column plain = new Column("doc");
        plain.setTypeFamily("JSON");
        plain.setDialectType("JSON");
        assertNull(PARSER.parse(plain).shape());
        plain.setFields(List.of());
        assertNull(PARSER.parse(plain).shape());
    }

    @Test
    void anArrayOrMapAtTheTopIsTheShapeOfTheDocument() {
        Column array = new Column("doc");
        array.setTypeFamily("JSON");
        array.setElement(member(null, "INTEGER"));

        assertEquals("JSON(ARRAY(INTEGER))", PARSER.parse(array).portable());
    }

    @Test
    void theShapeTakesPartInEquality() {
        assertEquals(
                PARSER.parse(json(member("n", "INTEGER"))),
                PARSER.parse(json(member("n", "INTEGER"))));
        assertNotEquals(
                PARSER.parse(json(member("n", "INTEGER"))),
                PARSER.parse(json(member("n", "TEXT"))));
    }

    @Test
    void onlyJsonHasAShape() {
        assertThrows(
                IllegalStateException.class,
                () -> TypeDescriptor.TEXT.withShape(TypeDescriptor.TEXT));
    }

    @Test
    void aMemberIsAScalarOfAKnownFamily() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PARSER.parse(json(member("d", "DATE"))));
        assertTrue(
                e.getMessage().contains("BOOLEAN, INTEGER, FLOAT, DECIMAL or TEXT"),
                e.getMessage());
    }

    @Test
    void aJsonObjectHasNoKey() {
        Column c = new Column("doc");
        c.setTypeFamily("JSON");
        c.setKey(member(null, "TEXT"));

        assertThrows(IllegalArgumentException.class, () -> PARSER.parse(c));
    }
}
