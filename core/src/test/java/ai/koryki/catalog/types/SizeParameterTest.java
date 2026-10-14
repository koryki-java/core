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
package ai.koryki.catalog.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A size parameter is a number, a number with its Oracle unit, or MAX, which is no size. */
class SizeParameterTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static TypeDescriptor flat(String family, String dialectType) {
        return PARSER.parse("column 'c'", family, null, dialectType);
    }

    private static String refused(String family, String dialectType) {
        return assertThrows(IllegalArgumentException.class, () -> flat(family, dialectType))
                .getMessage();
    }

    @ParameterizedTest
    @CsvSource({
        "TEXT, VARCHAR(MAX)",
        "TEXT, NVARCHAR(MAX)",
        "TEXT, varchar(max)",
        "TEXT, VARCHAR( MAX )",
        "BLOB, VARBINARY(MAX)"
    })
    void maxIsNoSize(String family, String dialectType) {
        TypeDescriptor type = flat(family, dialectType);

        assertEquals(-1, type.getPrecision());
        assertEquals(dialectType, type.getPhysicalTypeName());
    }

    @ParameterizedTest
    @CsvSource({
        "VARCHAR2(10 CHAR), 10",
        "VARCHAR2(10 BYTE), 10",
        "VARCHAR2(10 char), 10",
        "VARCHAR(40), 40"
    })
    void aNumberMayCarryTheUnitOracleWritesBehindIt(String dialectType, int precision) {
        assertEquals(precision, flat("TEXT", dialectType).getPrecision());
    }

    @Test
    void somethingElseIsNotASize() {
        assertTrue(
                refused("TEXT", "VARCHAR(abc)").contains("'abc' is not a size in dialectType"),
                "word");
        assertTrue(refused("TEXT", "VARCHAR(10 FOO)").contains("'10 FOO' is not a size"), "unit");
    }

    @Test
    void maxIsTheOnlySizeOfATypeNotOneOfSeveral() {
        assertTrue(refused("DECIMAL", "DECIMAL(10, MAX)").contains("MAX is the only size"));
        assertTrue(refused("DECIMAL", "DECIMAL(MAX, 2)").contains("MAX is the only size"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "STRUCT(a VARCHAR(MAX), b INTEGER)|STRUCT(TEXT,INTEGER)",
                "VARCHAR(MAX)[]|ARRAY(TEXT)",
                "MAP(VARCHAR(MAX), VARCHAR2(10 CHAR))|MAP(TEXT,TEXT)",
                "STRUCT(a DECIMAL(10,2), b NVARCHAR(MAX))|STRUCT(DECIMAL,TEXT)"
            })
    void insideANestedSpellingToo(String spelling, String ignored) {
        TypeDescriptor type = TypeSyntax.parse(spelling, PARSER::parseLeaf);

        assertTrue(type.isComplex(), spelling);
    }

    @Test
    void theSizeOfALeafInsideANestedSpellingIsKept() {
        TypeDescriptor type =
                TypeSyntax.parse("STRUCT(a VARCHAR2(10 CHAR), b VARCHAR(MAX))", PARSER::parseLeaf);

        assertEquals(10, type.children().get(0).getPrecision());
        assertEquals(-1, type.children().get(1).getPrecision());
    }

    @Test
    void aBadSizeInsideANestedSpellingIsRefused() {
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> TypeSyntax.parse("STRUCT(a VARCHAR(abc))", PARSER::parseLeaf))
                        .getMessage()
                        .contains("expected a size, found 'ABC'"));
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        TypeSyntax.parse(
                                                "STRUCT(a DECIMAL(10, MAX))", PARSER::parseLeaf))
                        .getMessage()
                        .contains("MAX is the only size"));
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        TypeSyntax.parse(
                                                "STRUCT(a VARCHAR(10 FOO))", PARSER::parseLeaf))
                        .getMessage()
                        .contains("expected CHAR or BYTE after a size, found 'FOO'"));
    }
}
