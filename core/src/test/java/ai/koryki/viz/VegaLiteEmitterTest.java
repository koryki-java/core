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
package ai.koryki.viz;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.query.Query;
import ai.koryki.kql.KQLTranspiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1: a KQL query with a VISUALISE clause compiles to a Vega-Lite v6 spec —
 * mark, channel encodings with types resolved from the query's column types,
 * inline data, and title.
 */
class VegaLiteEmitterTest {

    private static final LinkResolver RESOLVER = NorthwindService.resolver(Locale.ENGLISH);

    @Test
    void compilesQueryToVegaLiteSpec() throws Exception {
        String kql = "FIND orders o "
                + "FETCH o.ship_country cat, o.freight amt, o.order_date dt "
                + "VISUALISE cat AS x, amt AS y, dt AS color "
                + "DRAW bar "
                + "LABEL title => 'Freight by country'";

        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        Query q = t.getQuery();
        List<VizColumn> columns = VegaLite.columns(t);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("USA", new BigDecimal("12.5"), LocalDate.of(2024, 1, 1)),
                Arrays.asList("UK", new BigDecimal("7.0"), LocalDate.of(2024, 2, 1)));

        String json = new VegaLite().render(q.getVisualise(), columns, rows);
        JsonNode spec = new ObjectMapper().readTree(json);

        assertTrue(spec.get("$schema").asText().contains("vega-lite/v6"), json);
        assertEquals("Freight by country", spec.get("title").asText());
        assertEquals("bar", spec.get("mark").get("type").asText());

        JsonNode enc = spec.get("encoding");
        assertEquals("cat", enc.get("x").get("field").asText());
        assertEquals("nominal", enc.get("x").get("type").asText());   // ship_country = TEXT
        assertEquals("amt", enc.get("y").get("field").asText());
        assertEquals("quantitative", enc.get("y").get("type").asText()); // freight = numeric
        assertEquals("temporal", enc.get("color").get("type").asText()); // order_date = date/timestamp

        JsonNode values = spec.get("data").get("values");
        assertEquals(2, values.size());
        assertEquals("USA", values.get(0).get("cat").asText());
        assertEquals(12.5, values.get(0).get("amt").asDouble());
        assertEquals("2024-01-01", values.get(0).get("dt").asText());
    }

    @Test
    void literalMappingBecomesConstantAndMarkMapsThrough() throws Exception {
        // 'path' geom maps to the 'line' Vega-Lite mark; a literal maps to {value:...}
        String kql = "FIND orders o FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y "
                + "DRAW path MAPPING 'steelblue' AS color";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();

        String json = new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(LocalDate.of(2024, 1, 1), new BigDecimal("3"))));
        JsonNode spec = new ObjectMapper().readTree(json);

        assertEquals("line", spec.get("mark").get("type").asText());
        assertEquals("steelblue", spec.get("encoding").get("color").get("value").asText());
    }

    @Test
    void scaleTransformPaletteReverseAndPerAxisLabel() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y, amt AS color "
                + "DRAW point "
                + "SCALE y VIA log SETTING reverse => true "
                + "SCALE color TO viridis "
                + "LABEL x => 'Country'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();

        String json = new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("5"))));
        JsonNode enc = new ObjectMapper().readTree(json).get("encoding");

        assertEquals("log", enc.get("y").get("scale").get("type").asText());
        assertTrue(enc.get("y").get("scale").get("reverse").asBoolean());
        assertEquals("viridis", enc.get("color").get("scale").get("scheme").asText());
        assertEquals("Country", enc.get("x").get("title").asText()); // per-axis LABEL
    }

    @Test
    void temporalViaForcesTemporalType() throws Exception {
        // order_date resolves to temporal already; VIA date must also force temporal on a text-ish channel
        String kql = "FIND orders o FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y DRAW line SCALE x VIA date";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(LocalDate.of(2024, 1, 1), new BigDecimal("2")))))
                .get("encoding");
        assertEquals("temporal", enc.get("x").get("type").asText());
    }

    @Test
    void facetWrapNestsUnitSpec() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar FACET country SETTING free => 'y'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("2")))));

        assertEquals("country", spec.get("facet").get("field").asText());
        assertEquals("independent", spec.get("resolve").get("scale").get("y").asText());
        // mark + encoding live under the nested "spec", data stays top-level
        assertEquals("bar", spec.get("spec").get("mark").get("type").asText());
        assertTrue(spec.get("spec").get("encoding").has("x"));
        assertTrue(spec.get("data").get("values").isArray());
    }

    @Test
    void facetGridUsesRowAndColumn() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.ship_city city, o.freight amt "
                + "VISUALISE amt AS y DRAW bar FACET country BY city";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode facet = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", "NYC", new BigDecimal("2")))))
                .get("facet");
        assertEquals("country", facet.get("row").get("field").asText());
        assertEquals("city", facet.get("column").get("field").asText());
    }

    @Test
    void multipleDrawsBecomeLayerArray() throws Exception {
        String kql = "FIND orders o FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y "
                + "DRAW bar "
                + "DRAW line";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(LocalDate.of(2024, 1, 1), new BigDecimal("3")))));

        JsonNode layers = spec.get("layer");
        assertEquals(2, layers.size());
        assertEquals("bar", layers.get(0).get("mark").get("type").asText());
        assertEquals("line", layers.get(1).get("mark").get("type").asText());
        assertTrue(layers.get(0).get("encoding").has("x"));
        assertTrue(layers.get(1).get("encoding").get("y").get("field").asText().equals("amt"));
        // data stays shared at the top level (single query result)
        assertTrue(spec.get("data").get("values").isArray());
    }

    @Test
    void stackPositionStacksQuantitativeChannel() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt, o.ship_city city "
                + "VISUALISE country AS x, amt AS y, city AS color "
                + "DRAW bar SETTING position => 'stack'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3"), "NYC"))))
                .get("encoding");
        assertEquals("zero", enc.get("y").get("stack").asText());
    }

    @Test
    void dodgePositionUnstacksAndOffsetsByGroup() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt, o.ship_city city "
                + "VISUALISE country AS x, amt AS y, city AS color "
                + "DRAW bar SETTING position => 'dodge'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3"), "NYC"))))
                .get("encoding");
        assertTrue(enc.get("y").get("stack").isNull());
        assertEquals("city", enc.get("xOffset").get("field").asText()); // offset by color field along x
    }

    @Test
    void placeLayerIsConstantAnnotationWithOwnData() throws Exception {
        String kql = "FIND orders o FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y "
                + "DRAW line "
                + "PLACE rule SETTING y => 100, color => 'red'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(LocalDate.of(2024, 1, 1), new BigDecimal("3")))));

        JsonNode place = spec.get("layer").get(1);
        assertEquals("rule", place.get("mark").get("type").asText());
        assertEquals(100, place.get("encoding").get("y").get("datum").asInt()); // positional → datum
        assertEquals("red", place.get("encoding").get("color").get("value").asText()); // other → value
        assertEquals(1, place.get("data").get("values").size()); // own single-row data
    }

    @Test
    void projectPolarMakesArcWithThetaAndDonut() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE amt AS y, country AS color "
                + "DRAW bar "
                + "PROJECT TO polar SETTING inner => 0.5";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));

        assertEquals("arc", spec.get("mark").get("type").asText());
        assertTrue(spec.get("mark").get("innerRadius").asInt() > 0);         // donut
        assertEquals("amt", spec.get("encoding").get("theta").get("field").asText()); // y → theta
        assertEquals("country", spec.get("encoding").get("color").get("field").asText());
    }

    @Test
    void projectCartesianReorderSwapsAxes() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y "
                + "DRAW point "
                + "PROJECT y, x TO cartesian";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))))
                .get("encoding");
        // aesthetics [y, x] → y drives x, x drives y
        assertEquals("amt", enc.get("x").get("field").asText());
        assertEquals("country", enc.get("y").get("field").asText());
    }

    @Test
    void wildcardMapsMatchingColumns() throws Exception {
        // output columns named x/y/color are auto-mapped by '*'
        String kql = "FIND orders o FETCH o.freight x, o.order_date y, o.ship_country color "
                + "VISUALISE * DRAW point";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(new BigDecimal("3"), LocalDate.of(2024, 1, 1), "USA"))))
                .get("encoding");
        assertEquals("x", enc.get("x").get("field").asText());
        assertEquals("quantitative", enc.get("x").get("type").asText());
        assertEquals("y", enc.get("y").get("field").asText());
        assertEquals("temporal", enc.get("y").get("type").asText());
        assertEquals("color", enc.get("color").get("field").asText());
    }

    @Test
    void labelAestheticAliasesToTextChannel() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW text MAPPING country AS label";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))))
                .get("encoding");
        assertEquals("country", enc.get("text").get("field").asText()); // label → text
        assertFalse(enc.has("label"));
    }

    @Test
    void scaleBreaksAndRenamingBecomeAxisGuides() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar "
                + "SCALE x SETTING breaks => ['USA', 'UK'] RENAMING 'USA' => 'United States', 'UK' => NULL";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode axis = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))))
                .get("encoding").get("x").get("axis");
        assertEquals("USA", axis.get("values").get(0).asText());
        assertTrue(axis.get("labelExpr").asText().contains("'United States'"));
        assertTrue(axis.get("labelExpr").asText().contains("datum.label")); // fallthrough
    }

    @Test
    void remappingMergesIntoLayerEncoding() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x DRAW bar REMAPPING amt AS y";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode enc = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))))
                .get("encoding");
        assertEquals("amt", enc.get("y").get("field").asText());
    }

    @Test
    void unsupportedCoordinateSystemThrowsClearError() {
        // not cartesian/polar/a map projection → clear error
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point PROJECT TO teapot";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));
        assertTrue(ex.getMessage().contains("teapot"), ex.getMessage());
    }

    @Test
    void positionJitterThrows() {
        // position => 'jitter' parses but is not implemented → clear error, not a silent no-op
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point SETTING position => 'jitter'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));
        assertTrue(ex.getMessage().contains("jitter"), ex.getMessage());
    }

    @Test
    void placeArraySettingThrows() {
        // multi-annotation array value on a PLACE setting parses but is not implemented
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point PLACE rule SETTING y => (10, 20)";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));
        assertTrue(ex.getMessage().contains("array"), ex.getMessage());
    }

    @Test
    void renamingWildcardThrows() {
        // wildcard/template RENAMING ('*' => …) parses but is not implemented
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar SCALE x RENAMING * => 'all'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));
        assertTrue(ex.getMessage().contains("wildcard"), ex.getMessage());
    }

    @Test
    void pointMapEmitsProjectionAndGeoChannels() throws Exception {
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS lon, lt AS lat DRAW point "
                + "PROJECT TO orthographic SETTING origin => (10, 50)";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(new BigDecimal("1.5"), 100L))));

        assertEquals("orthographic", spec.get("projection").get("type").asText());
        assertEquals(-10.0, spec.get("projection").get("rotate").get(0).asDouble());
        assertEquals(-50.0, spec.get("projection").get("rotate").get(1).asDouble());
        // the data layer sits on top of the default backgrounds
        JsonNode point = spec.get("layer").get(3);
        assertEquals("point", point.get("mark").get("type").asText());
        // lon/lat → longitude/latitude, field-only (no type)
        assertEquals("lng", point.get("encoding").get("longitude").get("field").asText());
        assertFalse(point.get("encoding").get("longitude").has("type"));
        assertEquals("lt", point.get("encoding").get("latitude").get("field").asText());
    }

    @Test
    void mapRemapsXyToLongitudeLatitudeAndParallels() throws Exception {
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS x, lt AS y DRAW point "
                + "PROJECT TO albers SETTING parallel => (29.5, 45.5)";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(new BigDecimal("1.5"), 100L))));

        assertEquals("albers", spec.get("projection").get("type").asText());
        assertEquals(29.5, spec.get("projection").get("parallels").get(0).asDouble());
        assertEquals(45.5, spec.get("projection").get("parallels").get(1).asDouble());
        JsonNode point = spec.get("layer").get(3);
        assertEquals("lng", point.get("encoding").get("longitude").get("field").asText()); // x → longitude
        assertEquals("lt", point.get("encoding").get("latitude").get("field").asText());   // y → latitude
    }

    @Test
    void mapTargetCrsIsRejected() {
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS lon, lt AS lat DRAW point "
                + "PROJECT TO crs SETTING target => 'EPSG:3857'";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList(new BigDecimal("1.5"), 100L))));
        assertTrue(ex.getMessage().contains("target"), ex.getMessage());
    }

    @Test
    void choroplethBuildsGeoJsonFeatures() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country geom, o.freight val "
                + "VISUALISE geom AS geometry, val AS fill DRAW spatial";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        String polygon = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}";
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(polygon, new BigDecimal("5")))));

        assertEquals("equalEarth", spec.get("projection").get("type").asText()); // default map coord
        JsonNode layer = spec.get("layer").get(0);
        assertEquals("geoshape", layer.get("mark").get("type").asText());
        // geometry channel is not encoded; fill is
        assertFalse(layer.get("encoding").has("geometry"));
        assertEquals("val", layer.get("encoding").get("fill").get("field").asText());
        // rows became GeoJSON Features with parsed geometry + property columns
        JsonNode feature = layer.get("data").get("values").get(0);
        assertEquals("Feature", feature.get("type").asText());
        assertEquals("Polygon", feature.get("geometry").get("type").asText());
        assertEquals(5.0, feature.get("val").asDouble());
        assertFalse(spec.has("data"), "choropleth carries its own Features, no top-level flat data");
    }

    @Test
    void spatialWithoutGeometryMappingThrows() {
        String kql = "FIND orders o FETCH o.ship_country region, o.freight val "
                + "VISUALISE val AS fill DRAW spatial";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        KorykiaiException ex = assertThrows(KorykiaiException.class, () ->
                new VegaLite().render(t.getQuery().getVisualise(), VegaLite.columns(t),
                        Arrays.asList(Arrays.asList("USA", new BigDecimal("5")))));
        assertTrue(ex.getMessage().contains("geometry"), ex.getMessage());
    }

    @Test
    void mapDrawsSphereGraticuleAndBasemapByDefault() throws Exception {
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS lon, lt AS lat DRAW point "
                + "PROJECT TO orthographic";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(new BigDecimal("1.5"), 100L))));

        assertEquals("orthographic", spec.get("projection").get("type").asText());
        JsonNode layers = spec.get("layer");
        assertEquals(4, layers.size());
        // backgrounds drawn under the data: sphere, graticule, then the world basemap
        assertTrue(layers.get(0).get("data").get("sphere").asBoolean());
        assertTrue(layers.get(1).get("data").get("graticule").asBoolean());
        JsonNode basemap = layers.get(2).get("data");
        assertEquals("/geo/countries-110m.json", basemap.get("url").asText());
        assertEquals("topojson", basemap.get("format").get("type").asText());
        assertEquals("countries", basemap.get("format").get("feature").asText());
        assertEquals("geoshape", layers.get(2).get("mark").get("type").asText());
        assertEquals("point", layers.get(3).get("mark").get("type").asText());
    }

    @Test
    void mapBackgroundsCanBeDisabled() throws Exception {
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS lon, lt AS lat DRAW point "
                + "PROJECT TO orthographic "
                + "SETTING sphere => false, graticule => false, basemap => false";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), VegaLite.columns(t),
                Arrays.asList(Arrays.asList(new BigDecimal("1.5"), 100L))));

        // all backgrounds off → back to a single unit spec, marks on a blank canvas
        assertFalse(spec.has("layer"));
        assertEquals("point", spec.get("mark").get("type").asText());
        assertEquals("lng", spec.get("encoding").get("longitude").get("field").asText());
    }

    // ── features 2/3/5: semantic-layer polish on an already-chosen encoding ───

    /** Replaces one column's entry with an enriched VizColumn carrying query semantics. */
    private static List<VizColumn> withSemantics(List<VizColumn> base, String field, String metric,
            String unit, String grain, Integer orderPriority, String orderDirection) {
        List<VizColumn> out = new java.util.ArrayList<>();
        for (VizColumn c : base) {
            out.add(c.getName().equals(field)
                    ? new VizColumn(c.getName(), c.getType(), metric, unit, grain, orderPriority, orderDirection)
                    : c);
        }
        return out;
    }

    @Test
    void metricNameBecomesAxisTitle() throws Exception {
        // feature 2: the concluded metric name titles the axis instead of the raw field
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withSemantics(VegaLite.columns(t), "amt", "Net revenue", null, null, null, null);
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));

        assertEquals("Net revenue", spec.get("encoding").get("y").get("title").asText());
    }

    @Test
    void realUnitIsSuffixedButDimensionlessIsNot() throws Exception {
        // feature 3: a real unit is appended to the title; the count unit "1" is ignored
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withSemantics(VegaLite.columns(t), "amt", "Revenue", "€", null, null, null);
        cols = withSemantics(cols, "country", null, "1", null, null, null); // count unit → no suffix
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));

        assertEquals("Revenue (€)", spec.get("encoding").get("y").get("title").asText());
        assertFalse(spec.get("encoding").get("x").has("title")); // "1" is not a meaningful unit
    }

    @Test
    void temporalGrainSetsAxisFormat() throws Exception {
        // feature 3: a monthly grain gives the temporal axis a "%Y-%m" format
        String kql = "FIND orders o FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y DRAW line";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withSemantics(VegaLite.columns(t), "dt", null, null, "month", null, null);
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList(LocalDate.of(2024, 1, 1), new BigDecimal("3")))));

        assertEquals("temporal", spec.get("encoding").get("x").get("type").asText());
        assertEquals("%Y-%m", spec.get("encoding").get("x").get("axis").get("format").asText());
    }

    @Test
    void queryOrderSortsDiscreteAxisByMeasure() throws Exception {
        // feature 5: ORDER BY the measure → the nominal x is sorted by that measure, descending
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withSemantics(VegaLite.columns(t), "amt", null, null, null, 0, "DESC");
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));

        JsonNode sort = spec.get("encoding").get("x").get("sort");
        assertEquals("amt", sort.get("field").asText());
        assertEquals("descending", sort.get("order").asText());
    }

    @Test
    void queryOrderSortsAxisByOwnValues() throws Exception {
        // feature 5: ORDER BY the category itself → the nominal x sorts by its own values
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW bar";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withSemantics(VegaLite.columns(t), "country", null, null, null, 0, "ASC");
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3")))));

        assertEquals("ascending", spec.get("encoding").get("x").get("sort").asText());
    }

    /** Sets one column's physical-dimension key (feature 4 scale grouping). */
    private static List<VizColumn> withDim(List<VizColumn> base, String field, String dim) {
        List<VizColumn> out = new java.util.ArrayList<>();
        for (VizColumn c : base) {
            out.add(c.getName().equals(field)
                    ? new VizColumn(c.getName(), c.getType(), null, null, null, null, null, dim)
                    : c);
        }
        return out;
    }

    @Test
    void mixedDimensionLayersGetIndependentYScale() throws Exception {
        // feature 4: a money measure and a count measure on two layers → independent y scale
        String kql = "FIND orders o FETCH o.ship_country country, o.freight revenue, o.order_id qty "
                + "VISUALISE country AS x DRAW line MAPPING revenue AS y DRAW bar MAPPING qty AS y";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withDim(withDim(VegaLite.columns(t), "revenue", "MONEY"), "qty", "COUNT");
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3"), 10L))));

        assertEquals(2, spec.get("layer").size());
        assertEquals("independent", spec.get("resolve").get("scale").get("y").asText());
    }

    @Test
    void sameDimensionLayersShareYScale() throws Exception {
        // same dimension (both money) → no forced resolve, Vega shares the y scale by default
        String kql = "FIND orders o FETCH o.ship_country country, o.freight revenue, o.order_id qty "
                + "VISUALISE country AS x DRAW line MAPPING revenue AS y DRAW bar MAPPING qty AS y";
        KQLTranspiler t = KQLTranspiler.builder(kql, RESOLVER).build();
        List<VizColumn> cols = withDim(withDim(VegaLite.columns(t), "revenue", "MONEY"), "qty", "MONEY");
        JsonNode spec = new ObjectMapper().readTree(new VegaLite().render(
                t.getQuery().getVisualise(), cols, Arrays.asList(Arrays.asList("USA", new BigDecimal("3"), 10L))));

        assertEquals(2, spec.get("layer").size());
        assertFalse(spec.has("resolve"));
    }
}
