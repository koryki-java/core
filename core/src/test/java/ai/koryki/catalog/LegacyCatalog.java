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

import ai.koryki.catalog.schema.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A catalog in the form every db.json had before the concepts: the type and the wording in fields
 * of the column. The input of the tests of the converter, which the catalogs of the northwind
 * repository can no longer be, since they have been rewritten.
 */
public final class LegacyCatalog {

    private LegacyCatalog() {}

    /** The complex bed of DuckDB, as it was written before. */
    public static Schema duckdbComplex() {
        try (InputStream in =
                LegacyCatalog.class.getResourceAsStream(
                        "/ai/koryki/catalog/legacy/duckdb-complex-db.json")) {
            return CatalogLoader.readSchemaJson(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
