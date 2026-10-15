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
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.WordingAudit.Gap;
import ai.koryki.catalog.WordingAudit.Kind;
import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Where a model has not said in its own words what the schema says, on the complex bed: a draft
 * model has every word of the schema, a translation replaces them, one that forgets a part is
 * missing it.
 */
class WordingAuditTest {

    private static final Schema SCHEMA = CatalogLoader.db(ComplexService.DB);

    private static Member geoLat(Model model) {
        return model.getEntity("check_complex")
                .orElseThrow()
                .getAttribute("address")
                .orElseThrow()
                .getMembers()
                .get("geo")
                .getMembers()
                .get("lat");
    }

    private static boolean has(List<Gap> gaps, String where, String field, Kind kind) {
        return gaps.contains(new Gap(where, field, kind));
    }

    @Test
    void aDraftModelHasEveryWordOfTheSchemaAndNothingIsMissing() {
        List<Gap> gaps = WordingAudit.audit(SCHEMA, SchemaToModel.convert(SCHEMA));

        assertTrue(gaps.stream().noneMatch(g -> g.kind() == Kind.MISSING), gaps.toString());
        assertTrue(
                has(gaps, "check_complex.address/geo/lat", "label", Kind.UNCHANGED),
                gaps.toString());
        assertTrue(has(gaps, "check_complex.address/geo/lat", "description", Kind.UNCHANGED));
    }

    @Test
    void aTranslatedWordIsNoGapAnyMore() {
        Model model = SchemaToModel.convert(SCHEMA);
        geoLat(model).setLabel("Breite");
        geoLat(model).setDescription("Grad nördlich");

        List<Gap> gaps = WordingAudit.audit(SCHEMA, model);

        assertTrue(
                gaps.stream().noneMatch(g -> g.where().equals("check_complex.address/geo/lat")),
                gaps.toString());
        // the neighbour was left as it was
        assertTrue(has(gaps, "check_complex.address/geo/lon", "label", Kind.UNCHANGED));
    }

    @Test
    void aPartTheModelDoesNotSayIsMissing() {
        Model model = SchemaToModel.convert(SCHEMA);
        model.getEntity("check_complex")
                .orElseThrow()
                .getAttribute("address")
                .orElseThrow()
                .getMembers()
                .get("geo")
                .getMembers()
                .remove("lon");

        List<Gap> gaps = WordingAudit.audit(SCHEMA, model);

        assertTrue(
                has(gaps, "check_complex.address/geo/lon", "label", Kind.MISSING), gaps.toString());
        assertEquals(
                0,
                gaps.stream()
                        .filter(
                                g ->
                                        g.where().equals("check_complex.address/geo/lat")
                                                && g.kind() == Kind.MISSING)
                        .count());
    }

    @Test
    void aSharedConceptWithoutANotionIsMissing() {
        Schema schema =
                CatalogLoader.readSchemaJson(
                        """
                {"name": "s", "tables": [], "relations": [],
                 "concepts": {"point": {"typeFamily": "STRUCT", "label": "Position",
                   "entries": {"lat": {"typeFamily": "FLOAT", "label": "Latitude"}}}}}
                """);
        Model model = new Model("s");

        List<Gap> gaps = WordingAudit.audit(schema, model);

        assertTrue(has(gaps, "concept:point", "label", Kind.MISSING), gaps.toString());
        assertTrue(has(gaps, "concept:point/lat", "label", Kind.MISSING), gaps.toString());
    }
}
