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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every catalog the northwind repository ships (the Northwind sample, and the complex, temporal and
 * typecheck beds of the eight dialects) says the type and the wording of its columns in concepts,
 * and nothing else does.
 */
class MigratedCatalogsTest {

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
        assertTrue(catalogs().size() >= 24, "catalogs found: " + catalogs());
    }

    @Test
    void everyColumnHasItsConcept() {
        int columns = 0;
        for (String catalog : catalogs()) {
            Schema schema = CatalogLoader.db(catalog);
            for (Table table : schema.getTables()) {
                for (Column column : table.getColumns()) {
                    String where = catalog + " " + table.getName() + "." + column.getName();
                    assertNotNull(column.getConcept(), where);
                    assertNull(column.getConceptRef(), where);
                    columns++;
                }
            }
        }
        assertTrue(columns > 500, "columns: " + columns);
    }

    @Test
    void everyConceptIsSoundAndTypesTheColumn() {
        for (String catalog : catalogs()) {
            Schema schema = CatalogLoader.db(catalog);
            ConceptResolver resolver = new ConceptResolver(schema);
            resolver.check(PARSER);
            for (Table table : schema.getTables()) {
                for (Column column : table.getColumns()) {
                    assertNotNull(
                            resolver.descriptor(column, PARSER),
                            catalog + " " + table.getName() + "." + column.getName());
                }
            }
        }
    }
}
