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
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.SchemaToModel;
import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.LinkResolver;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A query moves from the language of one model to another with the names of struct fields in its
 * selectors: English {@code location@latitude} is German {@code position@breitengrad}.
 */
class SelectorNamesTranslationTest {

    private static final Schema SCHEMA = CatalogLoader.db(ComplexService.DB);

    private static Map<String, Member> members(Object... keyAndMember) {
        Map<String, Member> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndMember.length; i += 2) {
            map.put((String) keyAndMember[i], (Member) keyAndMember[i + 1]);
        }
        return map;
    }

    private static LinkResolver resolver(
            Locale locale, String street, String geo, String lat, String lon) {
        Model model = SchemaToModel.convert(SCHEMA);
        Member geoMember = new Member(geo);
        geoMember.setMembers(members("lat", new Member(lat), "lon", new Member(lon)));
        model.getEntity("check_complex")
                .orElseThrow()
                .getAttribute("address")
                .orElseThrow()
                .setMembers(members("street", new Member(street), "geo", geoMember));
        return new LinkResolver(locale, SCHEMA, model);
    }

    private static final LinkResolver EN =
            resolver(Locale.ENGLISH, "road", "location", "latitude", "longitude");
    private static final LinkResolver DE =
            resolver(Locale.GERMAN, "strasse", "position", "breitengrad", "laengengrad");

    private static String translate(LinkResolver from, LinkResolver to, String fetch)
            throws IOException {
        KQLTranspiler transpiler =
                KQLTranspiler.builder("FIND check_complex t FETCH " + fetch, from).build();
        return new KQLFormatter(
                        transpiler.getCtx(),
                        transpiler.getDescription(),
                        from,
                        LinkResolver.dictionary(from.getModel(), to.getModel(), SCHEMA))
                .format();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.address@road|t.address@strasse",
                "t.address@location@latitude|t.address@position@breitengrad",
                "t.address@'location'@longitude|t.address@'position'@laengengrad",
                // what the model does not name, and what is no field, stays
                "t.address@phones@1|t.address@phones@1",
                "t.tags@1:2|t.tags@1:2",
            })
    void aQueryMovesFromEnglishToGerman(String english, String german) throws IOException {
        String kql = translate(EN, DE, english);

        assertEquals(
                german,
                kql.lines()
                        .filter(l -> l.contains("t."))
                        .map(String::strip)
                        .findFirst()
                        .orElse(kql)
                        .replaceAll("^FETCH\\s+", ""));
    }

    @Test
    void andBack() throws IOException {
        String kql = translate(DE, EN, "t.address@position@breitengrad");

        assertEquals(true, kql.contains("t.address@location@latitude"), kql);
        assertNotNull(KQLTranspiler.builder(kql, EN).build());
    }
}
