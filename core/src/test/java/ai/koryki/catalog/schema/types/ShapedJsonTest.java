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
import ai.koryki.catalog.schema.NestedType;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A JSON column whose members the catalog declares: the tree they make, and what is refused. */
class ShapedJsonTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static TypeDescriptor json(String... pathsAndFamilies) {
        Column column = new Column("doc");
        column.setTypeFamily("JSON");
        column.setDialectType("JSON");
        java.util.List<NestedType> nested = new java.util.ArrayList<>();
        for (int i = 0; i < pathsAndFamilies.length; i += 2) {
            nested.add(new NestedType(pathsAndFamilies[i], pathsAndFamilies[i + 1], null));
        }
        column.setNested(nested);
        return PARSER.parse(column);
    }

    @Test
    void membersMakeAStructAListAndAMap() {
        assertEquals(
                "JSON(STRUCT(n:INTEGER,a:STRUCT(b:ARRAY(INTEGER)),m:MAP(TEXT,TEXT)))",
                json(".n", "INTEGER", ".a.b[]", "INTEGER", ".m{}", "TEXT").portable());
    }

    @Test
    void theColumnStaysJsonAndWithoutDeclarationsHasNoShape() {
        TypeDescriptor shaped = json(".n", "INTEGER");
        assertEquals("JSON", shaped.getTypeFamily().name());
        assertEquals("STRUCT(n:INTEGER)", shaped.shape().portable());

        Column plain = new Column("doc");
        plain.setTypeFamily("JSON");
        plain.setDialectType("JSON");
        assertNull(PARSER.parse(plain).shape());
        plain.setNested(List.of());
        assertNull(PARSER.parse(plain).shape());
    }

    @Test
    void theShapeTakesPartInEquality() {
        assertEquals(json(".n", "INTEGER"), json(".n", "INTEGER"));
        assertNotEquals(json(".n", "INTEGER"), json(".n", "TEXT"));
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
                assertThrows(IllegalArgumentException.class, () -> json(".d", "DATE"));
        assertTrue(
                e.getMessage().contains("BOOLEAN, INTEGER, FLOAT, DECIMAL or TEXT"),
                e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> json(".d", "ARRAY"));
    }

    @Test
    void aMemberCannotBeDeclaredTwiceOrAsTwoKinds() {
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> json(".n", "INTEGER", ".n", "TEXT"))
                        .getMessage()
                        .contains("declared twice"));
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> json(".a", "INTEGER", ".a.b", "INTEGER"))
                        .getMessage()
                        .contains("more than one"));
    }

    @Test
    void aJsonObjectHasNoKeyStep() {
        assertThrows(IllegalArgumentException.class, () -> json("<>", "TEXT"));
    }
}
