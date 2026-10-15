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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A selector names the fields of a struct in the language of the model: under a German model {@code
 * t.address@position@breitengrad} reaches {@code geo.lat}. The SQL speaks the physical keys.
 */
class SelectorNamesTest {

    private static Map<String, Member> members(Object... keyAndMember) {
        Map<String, Member> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndMember.length; i += 2) {
            map.put((String) keyAndMember[i], (Member) keyAndMember[i + 1]);
        }
        return map;
    }

    private static LinkResolver german() {
        LinkResolver bed = ComplexService.resolver();
        Schema schema = bed.getSchema();
        Model model = bed.getModel();
        Member geo = new Member("position");
        geo.setMembers(members("lat", new Member("breitengrad"), "lon", new Member("laengengrad")));
        model.getEntity("check_complex")
                .orElseThrow()
                .getAttribute("address")
                .orElseThrow()
                .setMembers(members("street", new Member("strasse"), "geo", geo));
        return new LinkResolver(Locale.GERMAN, schema, model);
    }

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, german())
                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.indexOf("FROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.address@strasse|t.address['street']",
                "t.address@position@breitengrad|t.address['geo']['lat']",
                "t.address@position@'Laengengrad'|t.address['geo']['lon']",
                // no name of the model: the physical key still works
                "t.address@phones@1|t.address['phones'][1]",
                "t.address@geo@lat|t.address['geo']['lat']",
                "t.address@'position'@breitengrad|t.address['geo']['lat']",
            })
    void aNameOfTheModelIsTheKeyOfTheSchema(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    /** The whole SQL of a query, for the cases where the SELECT list is not what is asked. */
    private static String query(String kql) {
        return KQLTranspiler.builder(kql, german())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    @Test
    void aNameOfTheModelIsFollowedThroughABlock() {
        String sql =
                query(
                        """
                        WITH b AS (FIND check_complex t FETCH t.nr n, t.address a)
                        FIND b x FETCH x.a@position@breitengrad
                        """);

        assertTrue(sql.contains("['geo']['lat']"), sql);
    }

    @Test
    void aNameOfTheModelIsFollowedThroughABlockOverABlock() {
        String sql =
                query(
                        """
                        WITH b AS (FIND check_complex t FETCH t.nr n, t.address a),
                             c AS (FIND b y FETCH y.n m, y.a z)
                        FIND c x FETCH x.z@position@breitengrad
                        """);

        assertTrue(sql.contains("['geo']['lat']"), sql);
    }

    @Test
    void aNameOfTheModelIsTranslatedInsideAnExists() {
        String sql =
                query(
                        """
                        FIND check_complex_items i
                        FILTER EXISTS (i VIA items_of_check_complex check_complex c FILTER c.address@position@breitengrad > 1)
                        FETCH i.sku
                        """);

        assertTrue(sql.contains("['geo']['lat']"), sql);
    }

    @Test
    void aNameOfNoLanguageIsStillRefused() {
        assertThrows(RuntimeException.class, () -> sql("t.address@hoehe"));
    }
}
