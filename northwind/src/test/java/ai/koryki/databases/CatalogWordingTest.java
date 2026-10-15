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

package ai.koryki.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.WordingAudit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every model of the catalogs this repository ships says in its own words what the schema says: for
 * each column, for each part of a nested column and for each shared concept. What a model leaves
 * out is read from the schema in the language of the schema, so a German heading that shows an
 * English word is one nobody wrote. A word that is still the schema's is not checked: some are the
 * same in both languages.
 */
class CatalogWordingTest {

    /** The database and the root of its models, as the catalogs of this repository lay them out. */
    private record Catalog(String db, String models, String language) {
        @Override
        public String toString() {
            return models + " " + language;
        }
    }

    static Stream<Catalog> catalogs() {
        Stream<Catalog> northwind =
                Stream.of("en", "de")
                        .map(
                                l ->
                                        new Catalog(
                                                "/ai/koryki/databases/northwind",
                                                "/ai/koryki/databases/northwind/model",
                                                l));
        // oraview keeps its models beside its db.json
        Stream<Catalog> oraview =
                Stream.of("en", "de")
                        .map(
                                l ->
                                        new Catalog(
                                                "/ai/koryki/core/oraview",
                                                "/ai/koryki/core/oraview",
                                                l));
        Stream<Catalog> beds =
                Stream.of(
                                "duckdb",
                                "mariadb",
                                "mssql",
                                "oracle",
                                "postgresql",
                                "snowflake",
                                "sqlite",
                                "trino")
                        .flatMap(
                                d ->
                                        Stream.of("complex", "temporal", "typecheck")
                                                .map(n -> "/ai/koryki/" + d + "/databases/" + n))
                        .map(root -> new Catalog(root, root + "/model", "en"));
        return Stream.of(northwind, oraview, beds)
                .flatMap(s -> s)
                .filter(
                        c ->
                                CatalogLoader.class.getResource(
                                                CatalogLoader.modelResource(
                                                        c.models(),
                                                        Locale.forLanguageTag(c.language())))
                                        != null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogs")
    void theModelLeavesNoWordToTheSchema(Catalog c) {
        List<WordingAudit.Gap> missing =
                WordingAudit.audit(
                                CatalogLoader.db(c.db()),
                                CatalogLoader.model(
                                        c.models(), Locale.forLanguageTag(c.language())))
                        .stream()
                        .filter(g -> g.kind() == WordingAudit.Kind.MISSING)
                        .toList();

        assertEquals(List.of(), missing);
    }
}
