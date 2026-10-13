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
package ai.koryki.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The loader runs the four concept checks, so a bad catalog is refused where it is read. */
class ConceptsLoaderChecksTest {

    private static String db(String concepts) {
        return "{ \"name\": \"d\", \"tables\": [], \"relations\": [], \"concepts\": { "
                + concepts
                + " } }";
    }

    private static String refused(String concepts) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> CatalogLoader.readSchemaJson(db(concepts)))
                .getMessage();
    }

    @Test
    void aSoundSchemaLoadsFromString() {
        assertEquals(
                2,
                CatalogLoader.readSchemaJson(
                                db(
                                        "\"p\": { \"typeFamily\": \"STRUCT\", \"entries\": { \"x\": \"VARCHAR\" } },"
                                                + " \"q\": { \"typeFamily\": \"ARRAY\", \"entries\": { \"element\": \"p\" } }"))
                        .getConcepts()
                        .size());
    }

    @Test
    void aSchemaWithoutConceptsLoads() {
        CatalogLoader.readSchemaJson("{ \"name\": \"d\", \"tables\": [], \"relations\": [] }");
    }

    @Test
    void aReservedNameIsRefused() {
        assertTrue(
                refused("\"text\": { \"typeFamily\": \"TEXT\" }").contains("the name is a type"));
    }

    @Test
    void aBadShapeIsRefused() {
        assertTrue(
                refused("\"a\": { \"typeFamily\": \"ARRAY\", \"entries\": { \"x\": \"VARCHAR\" } }")
                        .contains("an ARRAY has the entries 'element'"));
    }

    @Test
    void aDanglingEntryIsRefused() {
        assertTrue(
                refused(
                                "\"a\": { \"typeFamily\": \"STRUCT\", \"entries\": { \"x\": \"nowhere\" } }")
                        .contains("neither a concept nor a type spelling"));
    }

    @Test
    void aCycleIsRefused() {
        assertTrue(
                refused(
                                "\"a\": { \"typeFamily\": \"STRUCT\", \"entries\": { \"x\": \"b\" } },"
                                        + " \"b\": { \"typeFamily\": \"STRUCT\", \"entries\": { \"y\": \"a\" } }")
                        .contains("concept cycle: a -> b -> a"));
    }

    @Test
    void theStreamIsCheckedToo() {
        byte[] json =
                db("\"a\": { \"typeFamily\": \"STRUCT\", \"entries\": { \"x\": \"nowhere\" } }")
                        .getBytes(StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogLoader.readSchemaJson(new ByteArrayInputStream(json)));
    }
}
