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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The converter on every catalog the northwind repository ships (the Northwind sample, and the
 * complex, temporal and typecheck beds of the eight dialects): every column types as it did, and
 * the converted schema passes the checks.
 */
class ConceptConverterCatalogsTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final List<String> DIALECTS =
            List.of(
                    "duckdb",
                    "mariadb",
                    "mssql",
                    "oracle",
                    "postgresql",
                    "snowflake",
                    "sqlite",
                    "trino");

    private static final List<String> BEDS = List.of("complex", "temporal", "typecheck");

    private static List<String> catalogs() {
        List<String> found = new ArrayList<>();
        for (String dialect : DIALECTS) {
            for (String bed : BEDS) {
                found.add("/ai/koryki/" + dialect + "/databases/" + bed);
            }
        }
        found.add("/ai/koryki/databases/northwind");
        return found.stream()
                .filter(c -> CatalogLoader.class.getResource(c + "/db.json") != null)
                .toList();
    }

    @Test
    void theCatalogsAreOnTheClassPath() {
        // the 24 beds of the eight dialects and the Northwind sample
        assertTrue(catalogs().size() >= 24, "catalogs found: " + catalogs());
    }

    @Test
    void everyColumnOfEveryCatalogTypesAsItDid() {
        int columns = 0;
        for (String catalog : catalogs()) {
            Schema old = CatalogLoader.db(catalog);
            Schema converted = ConceptConverter.convert(old);
            ConceptResolver resolver = new ConceptResolver(converted);
            resolver.check(PARSER);
            for (int t = 0; t < old.getTables().size(); t++) {
                Table before = old.getTables().get(t);
                for (int c = 0; c < before.getColumns().size(); c++) {
                    Column column = converted.getTables().get(t).getColumns().get(c);
                    String where = catalog + " " + before.getName() + "." + column.getName();
                    assertNotNull(column.getConcept(), where);
                    TypeDescriptor expected = PARSER.parse(before.getColumns().get(c));
                    assertEquals(expected, resolver.descriptor(column, PARSER), where);
                    columns++;
                }
            }
        }
        assertTrue(columns > 500, "columns converted: " + columns);
    }

    @Test
    void nothingWhatTheColumnsSayIsLost() {
        for (String catalog : catalogs()) {
            Schema old = CatalogLoader.db(catalog);
            Schema converted = ConceptConverter.convert(old);
            for (int t = 0; t < old.getTables().size(); t++) {
                for (int c = 0; c < old.getTables().get(t).getColumns().size(); c++) {
                    Column before = old.getTables().get(t).getColumns().get(c);
                    var concept = converted.getTables().get(t).getColumns().get(c).getConcept();
                    String where = catalog + " " + before.getName();
                    assertEquals(before.getLabel(), concept.getLabel(), where);
                    assertEquals(before.getComment(), concept.getComment(), where);
                    assertEquals(before.getDescription(), concept.getDescription(), where);
                    assertEquals(before.getUnit(), concept.getUnit(), where);
                    assertEquals(before.getQuantity(), concept.getQuantity(), where);
                    assertEquals(before.getPresentation(), concept.getPresentation(), where);
                }
            }
        }
    }
}
