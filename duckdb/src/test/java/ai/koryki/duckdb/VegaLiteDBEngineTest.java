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
package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: a KQL query with a VISUALISE clause is executed against a live
 * DuckDB and rendered to a Vega-Lite v6 spec with real result rows inlined.
 */
public class VegaLiteDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String queriesRoot() {
        return "src/test/resources/ai/koryki/duckdb/queries/northwind";
    }

    @Override
    protected String expectedCsv() {
        return "src/test/resources/ai/koryki/duckdb/expected/northwind/csv";
    }

    @Override
    protected String expectedSql() {
        return "src/test/resources/ai/koryki/duckdb/expected/northwind/sql";
    }

    public VegaLiteDBEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(),
                        NorthwindService.resolver(), new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                .format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void rendersRealRowsIntoVegaLiteSpec() throws Exception {
        String kql = "FIND orders o "
                + "FETCH o.ship_country country, o.freight freight "
                + "VISUALISE country AS x, freight AS y "
                + "DRAW point "
                + "LABEL title => 'Freight by country'";

        String json = engine.executeVegaLite(kql);
        JsonNode spec = new ObjectMapper().readTree(json);

        assertTrue(spec.get("$schema").asText().contains("vega-lite/v6"), json);
        assertEquals("Freight by country", spec.get("title").asText());
        assertEquals("point", spec.get("mark").get("type").asText());
        assertEquals("country", spec.get("encoding").get("x").get("field").asText());
        assertEquals("nominal", spec.get("encoding").get("x").get("type").asText());
        assertEquals("freight", spec.get("encoding").get("y").get("field").asText());
        assertEquals("quantitative", spec.get("encoding").get("y").get("type").asText());

        JsonNode values = spec.get("data").get("values");
        assertTrue(values.isArray() && values.size() > 0, "expected data rows: " + json);
        assertTrue(values.get(0).has("country") && values.get(0).has("freight"), json);
    }

    @Test
    public void queryOrderIsReflectedAsAxisSort() throws Exception {
        // feature 5, end-to-end: the Investigator derives the ORDER BY from the query and the
        // emitter sorts the matching discrete axis — no explicit sort in the VISUALISE clause
        String kql = "FIND orders o "
                + "FETCH o.ship_country country ASC, o.freight freight "
                + "VISUALISE country AS x, freight AS y "
                + "DRAW bar";
        String json = engine.executeVegaLite(kql);
        JsonNode spec = new ObjectMapper().readTree(json);

        assertEquals("nominal", spec.get("encoding").get("x").get("type").asText(), json);
        // country is the ordered column and sits on x → sort by its own values, ascending
        assertEquals("ascending", spec.get("encoding").get("x").get("sort").asText(), json);
    }

    @Test
    public void currencyUnitSuffixesTheAxisTitle() throws Exception {
        // feature 3, end-to-end: freight resolves to a money quantity (db.json unit=EUR),
        // so its axis title carries the € unit
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point";
        String json = engine.executeVegaLite(kql);
        JsonNode spec = new ObjectMapper().readTree(json);
        assertEquals("amt (€)", spec.get("encoding").get("y").get("title").asText(), json);
    }

    @Test
    public void netRevenueMetricTitlesTheAxis() throws Exception {
        // feature 2, end-to-end: the sum(unit_price * qty * (1 - discount)) shape concludes the
        // net-revenue metric (unit-price/count/ratio kinds in db.json), which titles the axis
        String kql = "FIND orders o, o order_details od "
                + "FETCH o.ship_country country, sum(od.unit_price * od.quantity * (1 - od.discount)) revenue "
                + "VISUALISE country AS x, revenue AS y DRAW bar";
        String json = engine.executeVegaLite(kql);
        JsonNode spec = new ObjectMapper().readTree(json);
        assertTrue(spec.get("encoding").get("y").get("title").asText().contains("Net revenue"), json);
    }

    @Test
    public void mixedDimensionMeasuresGetIndependentYScale() throws Exception {
        // feature 4, end-to-end: revenue (money dim) and order-line count on two layers →
        // independent y scales, derived from the columns' physical dimensions
        String kql = "FIND orders o, o order_details od "
                + "FETCH o.ship_country country, "
                + "sum(od.unit_price * od.quantity * (1 - od.discount)) revenue, sum(od.quantity) qty "
                + "VISUALISE country AS x DRAW line MAPPING revenue AS y DRAW bar MAPPING qty AS y";
        String json = engine.executeVegaLite(kql);
        JsonNode spec = new ObjectMapper().readTree(json);
        assertEquals("independent", spec.get("resolve").get("scale").get("y").asText(), json);
    }

    // ── validate-and-render: the deterministic check for a generate→validate→fix loop ──

    @Test
    public void validateAndRender_validVisualiseYieldsChart() {
        var r = engine.validateAndRender("FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point");
        assertTrue(r.ok(), r.error());
        assertTrue(r.hasChart());
        assertTrue(r.spec().contains("\"point\""), r.spec());
    }

    @Test
    public void validateAndRender_noVisualiseIsNoChartNotError() {
        // valid query, no VISUALISE clause → deliberate no-chart, not an error (step 6)
        var r = engine.validateAndRender("FIND orders o FETCH o.ship_country country, o.freight amt");
        assertTrue(r.ok());
        assertFalse(r.hasChart());
        assertNull(r.error());
    }

    @Test
    public void validateAndRender_unsupportedCoordIsError() {
        var r = engine.validateAndRender("FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW point PROJECT TO teapot");
        assertFalse(r.ok());
        assertNull(r.spec());
        assertTrue(r.error().contains("teapot"), r.error());
    }

    @Test
    public void validateAndRender_spatialWithoutGeometryIsError() {
        var r = engine.validateAndRender("FIND orders o FETCH o.ship_country region, o.freight val "
                + "VISUALISE val AS fill DRAW spatial");
        assertFalse(r.ok());
        assertTrue(r.error().contains("geometry"), r.error());
    }

    @Test
    public void validateAndRender_unknownChannelColumnIsError() {
        // VISUALISE references a column that is not a FETCH alias → error, not a bogus chart
        var r = engine.validateAndRender("FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, nonexistent AS y DRAW point");
        assertFalse(r.ok());
        assertTrue(r.error().contains("nonexistent"), r.error());
    }

    @Test
    public void validateAndRender_neverThrowsOnBadInput() {
        // the checker must never crash the generate→validate→fix loop: any input yields a
        // RenderResult, not an exception (the error is reported for the generator to act on)
        assertNotNull(engine.validateAndRender("@#$ not kql %^&"));
        assertNotNull(engine.validateAndRender("FIND orders o FETCH o.freight amt VISUALISE ?"));
    }

    @Test
    public void validateAndRender_invalidQueryIsError() {
        // unknown column → the query itself is rejected before any chart
        var r = engine.validateAndRender("FIND orders o FETCH o.does_not_exist x "
                + "VISUALISE x AS x DRAW point");
        assertFalse(r.ok());
        assertNotNull(r.error());
    }

    @Test
    public void multiLayerBarPlusLineOverLiveData() throws Exception {
        String kql = "FIND orders o "
                + "FETCH o.order_date dt, o.freight amt "
                + "VISUALISE dt AS x, amt AS y "
                + "DRAW bar "
                + "DRAW line";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));

        assertEquals("bar", spec.get("layer").get(0).get("mark").get("type").asText());
        assertEquals("line", spec.get("layer").get(1).get("mark").get("type").asText());
        assertTrue(spec.get("data").get("values").size() > 0);
    }

    @Test
    public void histogramBinsInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.freight amt "
                + "VISUALISE amt AS x DRAW histogram";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));
        JsonNode layer = spec.get("layer").get(0);

        // pre-binned bar encoding
        assertEquals("bar", layer.get("mark").get("type").asText());
        JsonNode enc = layer.get("encoding");
        assertEquals("bin_start", enc.get("x").get("field").asText());
        assertEquals("binned", enc.get("x").get("bin").asText());
        assertEquals("amt", enc.get("x").get("title").asText());
        assertEquals("bin_end", enc.get("x2").get("field").asText());
        assertEquals("count", enc.get("y").get("field").asText());
        assertEquals("quantitative", enc.get("y").get("type").asText());

        // the aggregated bins are the layer's own data; no raw rows at the top level
        assertFalse(spec.has("data"), "pure histogram should not ship raw rows");
        JsonNode bins = layer.get("data").get("values");
        assertTrue(bins.size() > 1, "expected multiple bins");

        long total = 0;
        double prevStart = Double.NEGATIVE_INFINITY;
        for (JsonNode bin : bins) {
            assertTrue(bin.get("bin_start").asDouble() < bin.get("bin_end").asDouble());
            assertTrue(bin.get("bin_start").asDouble() >= prevStart); // ordered
            prevStart = bin.get("bin_start").asDouble();
            assertTrue(bin.get("count").asLong() > 0); // only non-empty bins are returned
            total += bin.get("count").asLong();
        }
        assertTrue(total > 0, "bin counts should sum to the number of freight rows");
    }

    @Test
    public void histogramDensityViaRemapping() throws Exception {
        String kql = "FIND orders o FETCH o.freight amt "
                + "VISUALISE amt AS x DRAW histogram REMAPPING density AS y";
        JsonNode enc = new ObjectMapper().readTree(engine.executeVegaLite(kql))
                .get("layer").get(0).get("encoding");
        assertEquals("density", enc.get("y").get("field").asText());
    }

    @Test
    public void boxplotSummarisesInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW boxplot";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));
        JsonNode box = spec.get("layer").get(0);

        // composite of whisker rule, box bar, median tick over the layer's own summary data
        JsonNode parts = box.get("layer");
        assertEquals(3, parts.size());
        assertEquals("rule", parts.get(0).get("mark").get("type").asText());
        assertEquals("bar", parts.get(1).get("mark").get("type").asText());
        assertEquals("tick", parts.get(2).get("mark").get("type").asText());

        // box bar spans q1→q3, whisker lower→upper, all grouped on x
        assertEquals("q1", parts.get(1).get("encoding").get("y").get("field").asText());
        assertEquals("q3", parts.get(1).get("encoding").get("y2").get("field").asText());
        assertEquals("group", parts.get(1).get("encoding").get("x").get("field").asText());
        assertEquals("lower", parts.get(0).get("encoding").get("y").get("field").asText());
        assertEquals("upper", parts.get(0).get("encoding").get("y2").get("field").asText());

        assertFalse(spec.has("data"), "pure boxplot should not ship raw rows");
        JsonNode summary = box.get("data").get("values");
        assertTrue(summary.size() > 1, "expected one box per country");
        for (JsonNode s : summary) {
            double lower = s.get("lower").asDouble();
            double q1 = s.get("q1").asDouble();
            double median = s.get("median").asDouble();
            double q3 = s.get("q3").asDouble();
            double upper = s.get("upper").asDouble();
            assertTrue(lower <= q1 && q1 <= median && median <= q3 && q3 <= upper,
                    "quartiles out of order: " + s);
        }
    }

    @Test
    public void aggregateBarSumsInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y "
                + "DRAW bar SETTING aggregate => 'sum'";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));
        JsonNode layer = spec.get("layer").get(0);

        assertEquals("bar", layer.get("mark").get("type").asText());
        assertEquals("group", layer.get("encoding").get("x").get("field").asText());
        assertEquals("value", layer.get("encoding").get("y").get("field").asText());
        assertEquals("sum of amt", layer.get("encoding").get("y").get("title").asText());

        assertFalse(spec.has("data"), "aggregated bar should not ship raw rows");
        JsonNode bars = layer.get("data").get("values");
        assertTrue(bars.size() > 1, "expected one bar per country");
        // one row per country; freight sums are positive
        for (JsonNode b : bars) {
            assertTrue(b.has("group") && b.get("value").asDouble() > 0, b.toString());
        }
    }

    @Test
    public void heatmapBins2dInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.freight amt, o.order_id id "
                + "VISUALISE amt AS x, id AS y "
                + "DRAW heatmap SETTING bins => 8";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));
        JsonNode enc = spec.get("layer").get(0).get("encoding");

        assertEquals("rect", spec.get("layer").get(0).get("mark").get("type").asText());
        assertEquals("x_start", enc.get("x").get("field").asText());
        assertEquals("binned", enc.get("x").get("bin").asText());
        assertEquals("y_start", enc.get("y").get("field").asText());
        assertEquals("binned", enc.get("y").get("bin").asText());
        assertEquals("x_end", enc.get("x2").get("field").asText());
        assertEquals("count", enc.get("color").get("field").asText());

        assertFalse(spec.has("data"), "heatmap should not ship raw rows");
        JsonNode cells = spec.get("layer").get(0).get("data").get("values");
        assertTrue(cells.size() > 1, "expected multiple grid cells");
        long total = 0;
        for (JsonNode c : cells) {
            assertTrue(c.get("x_start").asDouble() < c.get("x_end").asDouble());
            assertTrue(c.get("y_start").asDouble() < c.get("y_end").asDouble());
            assertTrue(c.get("count").asLong() > 0);
            total += c.get("count").asLong();
        }
        assertTrue(total > 0);
    }

    @Test
    public void smoothFitsOlsLineInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.order_id id, o.freight amt "
                + "VISUALISE id AS x, amt AS y DRAW smooth";
        JsonNode layer = new ObjectMapper().readTree(engine.executeVegaLite(kql)).get("layer").get(0);

        assertEquals("line", layer.get("mark").get("type").asText());
        assertEquals("x", layer.get("encoding").get("x").get("field").asText());
        assertEquals("y", layer.get("encoding").get("y").get("field").asText());
        JsonNode fit = layer.get("data").get("values");
        assertEquals(2, fit.size(), "OLS line is two endpoints");
        assertTrue(fit.get(0).get("x").asDouble() < fit.get(1).get("x").asDouble(), "ordered by x");
    }

    @Test
    public void scatterWithTrendLineLayers() throws Exception {
        String kql = "FIND orders o FETCH o.order_id id, o.freight amt "
                + "VISUALISE id AS x, amt AS y DRAW point DRAW smooth";
        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));

        // scatter uses the shared raw result; the trend line carries its own fitted points
        assertTrue(spec.has("data"), "the point layer needs the raw rows");
        assertEquals("point", spec.get("layer").get(0).get("mark").get("type").asText());
        assertEquals("line", spec.get("layer").get(1).get("mark").get("type").asText());
        assertEquals(2, spec.get("layer").get(1).get("data").get("values").size());
    }

    @Test
    public void densityEstimatesKdeInTheDatabase() throws Exception {
        String kql = "FIND orders o FETCH o.freight amt "
                + "VISUALISE amt AS x DRAW density SETTING points => 32";
        JsonNode layer = new ObjectMapper().readTree(engine.executeVegaLite(kql)).get("layer").get(0);

        assertEquals("area", layer.get("mark").get("type").asText());
        assertEquals("x", layer.get("encoding").get("x").get("field").asText());
        assertEquals("density", layer.get("encoding").get("y").get("field").asText());

        JsonNode curve = layer.get("data").get("values");
        assertEquals(32, curve.size(), "grid resolution");
        double integral = 0;
        double prevX = Double.NEGATIVE_INFINITY;
        double step = curve.get(1).get("x").asDouble() - curve.get(0).get("x").asDouble();
        for (JsonNode p : curve) {
            assertTrue(p.get("x").asDouble() > prevX, "ordered by x");
            prevX = p.get("x").asDouble();
            assertTrue(p.get("density").asDouble() >= 0, "density is non-negative");
            integral += p.get("density").asDouble() * step;
        }
        assertTrue(integral > 0.8 && integral < 1.2, "KDE integrates to ~1, was " + integral);
    }

    @Test
    public void pointMapProjectsWithoutSpatialExtension() throws Exception {
        // lon/lat point map = two numeric columns + a projection; no ST_* / spatial extension
        String kql = "FIND orders o FETCH o.freight lng, o.order_id lt "
                + "VISUALISE lng AS lon, lt AS lat DRAW point PROJECT TO mercator";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));

        assertEquals("mercator", spec.get("projection").get("type").asText());
        // sphere/graticule/basemap backgrounds first, the point layer on top
        JsonNode point = spec.get("layer").get(3);
        assertEquals("point", point.get("mark").get("type").asText());
        assertEquals("lng", point.get("encoding").get("longitude").get("field").asText());
        assertEquals("lt", point.get("encoding").get("latitude").get("field").asText());
        assertTrue(spec.get("data").get("values").size() > 0);
    }

    @Test
    public void choroplethEmbedsGeoJsonFromTheQuery() throws Exception {
        // geometry as a GeoJSON-text column (here a literal) → geoshape Features; no spatial extension
        String kql = "FIND orders o "
                + "FETCH '{\"type\":\"Point\",\"coordinates\":[0,0]}' geom, o.freight val "
                + "VISUALISE geom AS geometry, val AS fill DRAW spatial";

        JsonNode spec = new ObjectMapper().readTree(engine.executeVegaLite(kql));

        assertEquals("equalEarth", spec.get("projection").get("type").asText());
        JsonNode layer = spec.get("layer").get(0);
        assertEquals("geoshape", layer.get("mark").get("type").asText());
        assertEquals("val", layer.get("encoding").get("fill").get("field").asText());
        JsonNode features = layer.get("data").get("values");
        assertTrue(features.size() > 0);
        assertEquals("Feature", features.get(0).get("type").asText());
        assertEquals("Point", features.get(0).get("geometry").get("type").asText());
        assertTrue(features.get(0).has("val"));
    }

    @Test
    public void violinIsPerGroupDensity() throws Exception {
        String kql = "FIND orders o FETCH o.ship_country country, o.freight amt "
                + "VISUALISE country AS x, amt AS y DRAW violin SETTING points => 24";
        JsonNode layer = new ObjectMapper().readTree(engine.executeVegaLite(kql)).get("layer").get(0);

        JsonNode enc = layer.get("encoding");
        assertEquals("line", layer.get("mark").get("type").asText());
        assertEquals("value", enc.get("x").get("field").asText());
        assertEquals("density", enc.get("y").get("field").asText());
        assertEquals("group", enc.get("color").get("field").asText()); // one curve per country

        JsonNode curves = layer.get("data").get("values");
        assertTrue(curves.size() > 24, "multiple groups × grid points");
        for (JsonNode p : curves) {
            assertTrue(p.has("group") && p.get("density").asDouble() >= 0);
        }
    }
}
