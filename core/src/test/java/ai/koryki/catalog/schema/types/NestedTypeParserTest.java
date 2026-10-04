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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.NestedType;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The recursive part of {@link TypeDescriptorParser}: the nested spelling DuckDB prints, read into
 * a tree of {@link TypeDescriptor}s, to any depth.
 */
class NestedTypeParserTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static TypeDescriptor parse(String family, String dialectType) {
        Column column = new Column("c");
        column.setTypeFamily(family);
        column.setDialectType(dialectType);
        return PARSER.parse(column);
    }

    private static String portable(String family, String dialectType) {
        return parse(family, dialectType).portable();
    }

    // ---- every shape of the check_complex table ------------------------------------------------

    @ParameterizedTest(name = "{1} -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "ARRAY|VARCHAR[]|ARRAY(TEXT)",
                "ARRAY|INTEGER[]|ARRAY(INTEGER)",
                "ARRAY|DOUBLE[3]|ARRAY(FLOAT,3)",
                "ARRAY|DOUBLE[3][2]|ARRAY(ARRAY(FLOAT,3),2)",
                "STRUCT|STRUCT(w DOUBLE, h DOUBLE)|STRUCT(w:FLOAT,h:FLOAT)",
                "STRUCT|STRUCT(street VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE), phones VARCHAR[])"
                        + "|STRUCT(street:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT),phones:ARRAY(TEXT))",
                "MAP|MAP(VARCHAR, VARCHAR)|MAP(TEXT,TEXT)",
                "MAP|MAP(VARCHAR, INTEGER)|MAP(TEXT,INTEGER)",
                "MAP|MAP(INTEGER, VARCHAR)|MAP(INTEGER,TEXT)",
                "ARRAY|STRUCT(sku VARCHAR, qty INTEGER, price DECIMAL(10,2), opts VARCHAR[])[]"
                        + "|ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))",
                "ARRAY|STRUCT(ts TIMESTAMP, \"day\" DATE)[]"
                        + "|ARRAY(STRUCT(ts:TIMESTAMP,day:DATE))",
                "ARRAY|MAP(VARCHAR, STRUCT(a INTEGER[], b MAP(INTEGER, VARCHAR[])))[]"
                        + "|ARRAY(MAP(TEXT,STRUCT(a:ARRAY(INTEGER),b:MAP(INTEGER,ARRAY(TEXT)))))",
                "STRUCT|STRUCT(\"Odd Name\" INTEGER, x INTEGER)|STRUCT(Odd Name:INTEGER,x:INTEGER)",
            })
    void everyShapeOfTheTestBedParses(String family, String dialectType, String expected) {
        assertEquals(expected, portable(family, dialectType));
    }

    @Test
    void leavesKeepPrecisionAndScaleInsideANest() {
        TypeDescriptor items =
                parse("ARRAY", "STRUCT(sku VARCHAR(20), price DECIMAL(10,2))[]").element();

        TypeDescriptor price = items.field("price").orElseThrow();
        assertEquals(CoreTypeFamily.DECIMAL, price.getTypeFamily());
        assertEquals(10, price.getPrecision());
        assertEquals(2, price.getScale());
        assertEquals(20, items.field("sku").orElseThrow().getPrecision());
    }

    @Test
    void theOuterSizeIsTheLastBracket() {
        // DuckDB: DOUBLE[3][2] is an array of 2 elements, each a DOUBLE[3]
        TypeDescriptor matrix = parse("ARRAY", "DOUBLE[3][2]");

        assertEquals(2, matrix.length());
        assertEquals(3, matrix.element().length());
        assertEquals(CoreTypeFamily.FLOAT, matrix.element().element().getTypeFamily());
    }

    @Test
    void aListIsNotAFixedArray() {
        assertEquals(-1, parse("ARRAY", "INTEGER[]").length());
        assertEquals(3, parse("ARRAY", "INTEGER[3]").length());
    }

    @Test
    void accessorsWalkTheTree() {
        TypeDescriptor deep =
                parse("MAP", "MAP(VARCHAR, STRUCT(a INTEGER[], b MAP(INTEGER, VARCHAR[])))");

        assertEquals(CoreTypeFamily.TEXT, deep.key().getTypeFamily());
        TypeDescriptor struct = deep.value();
        assertEquals(List.of("a", "b"), struct.fieldNames());
        assertEquals(
                CoreTypeFamily.INTEGER, struct.field("a").orElseThrow().element().getTypeFamily());
        assertEquals(
                CoreTypeFamily.TEXT,
                struct.field("b").orElseThrow().value().element().getTypeFamily());
    }

    @Test
    void fieldLookupPrefersTheExactNameThenIgnoresCase() {
        TypeDescriptor s = parse("STRUCT", "STRUCT(\"Odd Name\" INTEGER, x INTEGER)");

        assertTrue(s.field("Odd Name").isPresent());
        assertTrue(s.field("odd name").isPresent(), "DuckDB field names are case-blind");
        assertTrue(s.field("missing").isEmpty());
    }

    @Test
    void accessorsRefuseTheWrongKind() {
        TypeDescriptor list = parse("ARRAY", "INTEGER[]");

        assertThrows(IllegalStateException.class, list::key);
        assertThrows(IllegalStateException.class, () -> list.field("x"));
        assertThrows(IllegalStateException.class, () -> TypeDescriptor.INTEGER.element());
    }

    @Test
    void multiWordAndSpacedSpellings() {
        assertEquals("ARRAY(FLOAT)", portable("ARRAY", "  DOUBLE PRECISION [ ]  "));
        assertEquals(
                "STRUCT(a:TIMESTAMP,b:INTEGER)",
                portable("STRUCT", "struct( a  timestamp with time zone ,b int )"));
    }

    @Test
    void trinoStyleSpellingsAreAccepted() {
        assertEquals("ARRAY(INTEGER)", portable("ARRAY", "ARRAY(INTEGER)"));
        assertEquals("ARRAY(INTEGER)", portable("ARRAY", "LIST(INTEGER)"));
        assertEquals("STRUCT(a:INTEGER)", portable("STRUCT", "ROW(a INTEGER)"));
        // Oracle's leaf names inside collections and object types
        assertEquals(
                "ARRAY(STRUCT(sku:TEXT,price:DECIMAL,w:FLOAT))",
                portable(
                        "ARRAY",
                        "ARRAY(ROW(sku VARCHAR2(20), price NUMBER(10,2), w BINARY_DOUBLE))"));
        // Snowflake's structured OBJECT and the spellings it prints
        assertEquals("STRUCT(a:INTEGER)", portable("STRUCT", "OBJECT(a INTEGER)"));
        assertEquals(
                "ARRAY(STRUCT(sku:TEXT,price:DECIMAL,ts:TIMESTAMP))",
                portable(
                        "ARRAY",
                        "ARRAY(OBJECT(sku VARCHAR, price NUMBER(10,2), ts TIMESTAMP_NTZ))"));
        assertEquals(
                "ARRAY(MAP(TEXT,STRUCT(a:ARRAY(INTEGER),b:MAP(INTEGER,ARRAY(TEXT)))))",
                portable(
                        "ARRAY",
                        "ARRAY(MAP(VARCHAR, OBJECT(a ARRAY(INTEGER), b MAP(INTEGER,"
                                + " ARRAY(VARCHAR)))))"));
    }

    // ---- round trip ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "INTEGER[]",
                "DOUBLE[3][2]",
                "STRUCT(w DOUBLE, h DOUBLE)",
                "MAP(VARCHAR, INTEGER)",
                "STRUCT(\"Odd Name\" INTEGER, \"a\"\"b\" VARCHAR)[]",
                "MAP(VARCHAR, STRUCT(a INTEGER[], b MAP(INTEGER, VARCHAR[])))[]",
            })
    void theSpellingOfADescriptorParsesToTheSameDescriptor(String dialectType) {
        TypeDescriptor first = parse(rootFamily(dialectType), dialectType);
        TypeDescriptor second = parse(first.getTypeFamily().name(), first.getPhysicalTypeName());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.getPhysicalTypeName(), second.getPhysicalTypeName());
    }

    @Test
    void randomTreesSurviveTheirOwnSpelling() {
        Random random = new Random(42);
        for (int i = 0; i < 300; i++) {
            TypeDescriptor tree = randomTree(random, 5);

            TypeDescriptor again = parse(tree.getTypeFamily().name(), tree.getPhysicalTypeName());

            assertEquals(tree, again, tree.getPhysicalTypeName());
            assertEquals(tree.hashCode(), again.hashCode());
            assertEquals(tree.portable(), again.portable());
        }
    }

    private static final List<TypeDescriptor> LEAVES =
            List.of(
                    TypeDescriptor.INTEGER,
                    TypeDescriptor.TEXT,
                    TypeDescriptor.DOUBLE,
                    TypeDescriptor.DATE,
                    TypeDescriptor.TIMESTAMP,
                    TypeDescriptor.BOOLEAN);

    private static TypeDescriptor randomTree(Random r, int depth) {
        // always a container at the root; below it, a leaf with growing probability
        return container(r, depth);
    }

    private static TypeDescriptor node(Random r, int depth) {
        if (depth <= 0 || r.nextInt(3) == 0) {
            return LEAVES.get(r.nextInt(LEAVES.size()));
        }
        return container(r, depth);
    }

    private static TypeDescriptor container(Random r, int depth) {
        return switch (r.nextInt(3)) {
            case 0 ->
                    TypeDescriptor.array(
                            node(r, depth - 1), r.nextBoolean() ? -1 : 1 + r.nextInt(4));
            case 1 -> TypeDescriptor.map(node(r, depth - 1), node(r, depth - 1));
            default -> {
                int n = 1 + r.nextInt(3);
                List<String> names = new java.util.ArrayList<>();
                List<TypeDescriptor> types = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) {
                    names.add(i % 2 == 0 ? "f" + i : "Odd \"" + i + "\"");
                    types.add(node(r, depth - 1));
                }
                yield TypeDescriptor.struct(names, types);
            }
        };
    }

    private static String rootFamily(String dialectType) {
        // the declared family of a root is whatever the spelling says
        String t = dialectType.stripLeading().toUpperCase();
        if (t.endsWith("]")) {
            return "ARRAY";
        }
        return t.startsWith("STRUCT") ? "STRUCT" : "MAP";
    }

    // ---- errors -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "STRUCT(a INTEGER",
                "STRUCT(a INTEGER))",
                "INTEGER[",
                "INTEGER[3",
                "INTEGER[0]",
                "STRUCT()",
                "STRUCT(INTEGER)",
                "STRUCT(a INTEGER, A VARCHAR)",
                "MAP(VARCHAR)",
                "MAP(VARCHAR, INTEGER, DATE)",
                "MAP VARCHAR",
                "STRUCT(\"a INTEGER)",
                "STRUCT(\"\" INTEGER)",
                "ARRAY(INTEGER, x)",
                "INTEGER[] junk",
            })
    void malformedSpellingsAreRejectedWithTheTypeAndAPosition(String dialectType) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("ARRAY", dialectType));

        assertTrue(e.getMessage().contains("column 'c'"), e.getMessage());
        assertTrue(e.getMessage().contains(dialectType), e.getMessage());
        assertTrue(e.getMessage().contains("position"), e.getMessage());
    }

    @Test
    void anUnknownLeafNamesTheTypeAndTheLeaf() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> parse("ARRAY", "STRUCT(a FROBNICATE)[]"));

        assertTrue(e.getMessage().contains("FROBNICATE"), e.getMessage());
        assertTrue(e.getMessage().contains("column 'c'"), e.getMessage());
    }

    @Test
    void theDeclaredFamilyMustBeTheFamilyOfTheRoot() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("MAP", "INTEGER[]"));

        assertTrue(e.getMessage().contains("declared MAP"), e.getMessage());
        assertTrue(e.getMessage().contains("ARRAY"), e.getMessage());
    }

    @Test
    void aFlatNameUnderAContainerFamilyIsAMismatchNotAFormatError() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("ARRAY", "VARCHAR"));

        assertTrue(e.getMessage().contains("declared ARRAY"), e.getMessage());
    }

    @Test
    void aNestedSpellingUnderAScalarFamilyIsAMismatchToo() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("TEXT", "VARCHAR[]"));

        assertTrue(e.getMessage().contains("declared TEXT"), e.getMessage());
    }

    @Test
    void aContainerCannotCarryAnEncoding() {
        Column column = new Column("c");
        column.setTypeFamily("ARRAY");
        column.setTypeEncoding("EPOCH:SECONDS");
        column.setDialectType("BIGINT[]");

        // an encoding binds to a scalar family, so it is refused before the type is read; the
        // leaf is where it belongs, declared under 'nested'
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> PARSER.parse(column));
        assertTrue(e.getMessage().contains("column 'c'"), e.getMessage());
    }

    // ---- catalog refinement of a leaf ---------------------------------------------------------

    @Test
    void aNestedDeclarationRefinesOneLeaf() {
        Column column = new Column("events");
        column.setTypeFamily("ARRAY");
        column.setDialectType("STRUCT(ts BIGINT, day DATE)[]");
        column.setNested(List.of(new NestedType("[].ts", "TIMESTAMP", "EPOCH:SECONDS")));

        TypeDescriptor d = PARSER.parse(column);

        TypeDescriptor ts = d.element().field("ts").orElseThrow();
        assertEquals(CoreTypeFamily.TIMESTAMP, ts.getTypeFamily());
        assertEquals("EPOCH:SECONDS", ts.getTypeEncoding().name());
        assertEquals("BIGINT", ts.getPhysicalTypeName());
        // the sibling leaf and the shape are untouched
        assertEquals(CoreTypeFamily.DATE, d.element().field("day").orElseThrow().getTypeFamily());
        assertEquals("STRUCT(ts:TIMESTAMP,day:DATE)", d.element().portable());
        assertEquals("STRUCT(ts BIGINT, day DATE)[]", d.getPhysicalTypeName());
    }

    @Test
    void aNestedDeclarationReachesIntoMapsAndQuotedFields() {
        Column column = new Column("c");
        column.setTypeFamily("MAP");
        column.setDialectType("MAP(VARCHAR, STRUCT(\"Odd Name\" BIGINT))");
        column.setNested(List.of(new NestedType("{}.\"Odd Name\"", "TIMESTAMP", "EPOCH:MILLIS")));

        TypeDescriptor leaf = PARSER.parse(column).value().field("Odd Name").orElseThrow();

        assertEquals("EPOCH:MILLIS", leaf.getTypeEncoding().name());
    }

    @Test
    void aNestedDeclarationMustFitTheTree() {
        Column column = new Column("c");
        column.setTypeFamily("ARRAY");
        column.setDialectType("INTEGER[]");

        for (String path : List.of(".x", "{}", "<>", "[][]", "[].x", "", "[", ".")) {
            column.setNested(List.of(new NestedType(path, "INTEGER", null)));
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> PARSER.parse(column), path);
            assertTrue(e.getMessage().contains("column 'c'"), path + ": " + e.getMessage());
        }
    }

    @Test
    void aNestedDeclarationOnAContainerIsRefused() {
        Column column = new Column("c");
        column.setTypeFamily("ARRAY");
        column.setDialectType("INTEGER[][]");
        column.setNested(List.of(new NestedType("[]", "INTEGER", null)));

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> PARSER.parse(column));

        assertTrue(e.getMessage().contains("only a leaf"), e.getMessage());
    }

    @Test
    void anEncodingOfAnotherFamilyOnALeafIsRefused() {
        Column column = new Column("c");
        column.setTypeFamily("ARRAY");
        column.setDialectType("BIGINT[]");
        column.setNested(List.of(new NestedType("[]", "INTEGER", "EPOCH:SECONDS")));

        assertThrows(IllegalArgumentException.class, () -> PARSER.parse(column));
    }

    // ---- the descriptor itself ----------------------------------------------------------------

    @Test
    void scalarsAreUnchanged() {
        TypeDescriptor d = parse("DECIMAL", "decimal(18,6)");

        assertFalse(d.isComplex());
        assertTrue(d.children().isEmpty());
        assertTrue(d.fieldNames().isEmpty());
        assertEquals(-1, d.length());
        assertEquals("DECIMAL", d.portable());
        assertFalse(d.toString().contains("type="), "a scalar prints exactly as before");
    }

    @Test
    void equalityIsStructural() {
        TypeDescriptor a = TypeDescriptor.array(TypeDescriptor.INTEGER);

        assertEquals(a, TypeDescriptor.array(TypeDescriptor.INTEGER));
        assertEquals(a.hashCode(), TypeDescriptor.array(TypeDescriptor.INTEGER).hashCode());
        assertNotEquals(a, TypeDescriptor.array(TypeDescriptor.TEXT));
        assertNotEquals(a, TypeDescriptor.array(TypeDescriptor.INTEGER, 3));
        assertNotEquals(
                TypeDescriptor.map(TypeDescriptor.TEXT, TypeDescriptor.INTEGER),
                TypeDescriptor.map(TypeDescriptor.INTEGER, TypeDescriptor.TEXT));
        assertNotEquals(
                TypeDescriptor.struct(List.of("a"), List.of(TypeDescriptor.INTEGER)),
                TypeDescriptor.struct(List.of("b"), List.of(TypeDescriptor.INTEGER)));
    }

    @Test
    void theChildListsCannotBeChangedFromOutside() {
        TypeDescriptor s =
                TypeDescriptor.struct(
                        new java.util.ArrayList<>(List.of("a")),
                        new java.util.ArrayList<>(List.of(TypeDescriptor.INTEGER)));

        assertThrows(
                UnsupportedOperationException.class, () -> s.children().add(TypeDescriptor.TEXT));
        assertThrows(UnsupportedOperationException.class, () -> s.fieldNames().add("b"));
    }

    @Test
    void factoriesRefuseNonsense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TypeDescriptor.array(TypeDescriptor.INTEGER, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> TypeDescriptor.array(TypeDescriptor.INTEGER, -2));
        assertThrows(
                IllegalArgumentException.class, () -> TypeDescriptor.struct(List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TypeDescriptor.struct(
                                List.of("a", "A"),
                                List.of(TypeDescriptor.INTEGER, TypeDescriptor.TEXT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TypeDescriptor.struct(List.of("a"), List.of()));
    }
}
