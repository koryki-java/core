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
package ai.koryki.tools.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.tools.migrate.MigrateCatalogs.Report;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A db.json is rewritten with a concept for every column, and says what it said. */
class MigrateCatalogsTest {

    private static final String OLD =
            """
            { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
              { "name": "nr", "label": "Nr", "typeFamily": "INTEGER", "dialectType": "SMALLINT",
                "nullable": false, "pkPos": 1 },
              { "name": "price", "label": "Price", "typeFamily": "DECIMAL",
                "dialectType": "DECIMAL(10,2)", "unit": "EUR", "quantity": "money" },
              { "name": "dims", "typeFamily": "STRUCT",
                "dialectType": "STRUCT(w DOUBLE, h DOUBLE)",
                "fields": [ { "name": "w", "label": "Width", "typeFamily": "FLOAT",
                              "dialectType": "DOUBLE", "unit": "m" },
                            { "name": "h", "typeFamily": "FLOAT", "dialectType": "DOUBLE" } ] },
              { "name": "tags", "typeFamily": "ARRAY", "dialectType": "VARCHAR[]" }
            ] } ] }
            """;

    @Test
    void theFileIsRewrittenWithAConceptForEveryColumnThatHasOne(@TempDir Path dir)
            throws IOException {
        Path file = dir.resolve("db.json");
        Files.writeString(file, OLD);

        Report report = MigrateCatalogs.migrate(file);

        assertEquals(4, report.columns());
        assertEquals(3, report.migrated());
        assertEquals(List.of("t.tags"), report.kept());
        Schema schema = CatalogLoader.readSchemaJson(Files.readString(file));
        Column price = schema.getTables().get(0).getColumns().get(1);
        assertNull(price.getLabel());
        assertNull(price.getUnit());
        assertEquals("Price", price.getConcept().getLabel());
        assertEquals("EUR", price.getConcept().getUnit());
        assertEquals("DECIMAL(10,2)", price.getConcept().getDialectType());
        Column dims = schema.getTables().get(0).getColumns().get(2);
        assertNull(dims.getFields());
        assertTrue(dims.getConcept().getEntries().get("w").isInline());
        Column nr = schema.getTables().get(0).getColumns().get(0);
        assertEquals(1, nr.getPkPos());
        assertEquals(Boolean.FALSE, nr.getNullable());
    }

    @Test
    void theColumnsOfTheFileDoNotCarryTheOldFieldsAnyMore(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("db.json");
        Files.writeString(file, OLD);

        MigrateCatalogs.migrate(file);

        String text = Files.readString(file);
        assertFalse(text.contains("\"fields\""), text);
        assertFalse(text.contains("\"unit\" : \"m\"\n          }\n        }"), text);
        assertTrue(text.contains("\"concept\""), text);
    }

    @Test
    void aFileThatIsMigratedAlreadyIsTheSameAgain(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("db.json");
        Files.writeString(file, OLD);
        MigrateCatalogs.migrate(file);
        String once = Files.readString(file);

        Report again = MigrateCatalogs.migrate(file);

        assertEquals(once, Files.readString(file));
        assertEquals(3, again.migrated());
    }
}
