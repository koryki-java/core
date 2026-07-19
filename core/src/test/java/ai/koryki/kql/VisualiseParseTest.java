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

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.viz.FacetSpec;
import ai.koryki.iql.query.viz.Layer;
import ai.koryki.iql.query.viz.Projection;
import ai.koryki.iql.query.viz.ScaleSpec;
import ai.koryki.iql.query.viz.Visualise;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 0: the VISUALISE clause parses into a {@link Visualise} bean, does not
 * affect SQL, and survives {@code getKql()} round-tripping.
 */
class VisualiseParseTest {

    private static final LinkResolver RESOLVER = NorthwindService.resolver(Locale.ENGLISH);

    private static Query query(String kql) {
        return KQLTranspiler.builder(kql, RESOLVER).build().getQuery();
    }

    @Test
    void plainQueryHasNoVisualise() {
        assertNull(query("FIND customers c FETCH c.company_name name").getVisualise());
    }

    @Test
    void parsesGlobalMappingDrawAndLabel() {
        Visualise v = query("FIND customers c FETCH c.company_name name, c.mail mail "
                + "VISUALISE name AS x, mail AS y DRAW bar LABEL title => 'Customers'").getVisualise();
        assertNotNull(v);
        assertEquals(2, v.getGlobal().size());
        assertEquals("name", v.getGlobal().get(0).getColumn());
        assertEquals("x", v.getGlobal().get(0).getChannel());
        assertEquals(1, v.getLayers().size());
        assertEquals("bar", v.getLayers().get(0).getMark());
        assertFalse(v.getLayers().get(0).isPlace());
        assertEquals(1, v.getLabels().size());
        assertEquals("title", v.getLabels().get(0).getTarget());
        assertEquals("Customers", v.getLabels().get(0).getValue());
    }

    @Test
    void parsesScaleFacetProjectSettingsAndSuppression() {
        Visualise v = query("FIND customers c FETCH c.company_name name, c.country country "
                + "VISUALISE name AS x "
                + "DRAW point MAPPING country AS color SETTING size => 60, filled => true "
                + "SCALE color TO viridis "
                + "SCALE CONTINUOUS x FROM (0, 100) VIA log SETTING reverse => true "
                + "FACET country BY name SETTING free => 'y' "
                + "PROJECT y, x TO cartesian "
                + "LABEL x => 'Company', y => NULL").getVisualise();
        assertNotNull(v);

        Layer layer = v.getLayers().get(0);
        assertEquals("point", layer.getMark());
        assertEquals("color", layer.getMapping().get(0).getChannel());
        assertEquals("country", layer.getMapping().get(0).getColumn());
        assertEquals(60L, layer.getSettings().get("size"));
        assertEquals("true", layer.getSettings().get("filled"));

        assertEquals(2, v.getScales().size());
        ScaleSpec color = v.getScales().get(0);
        assertEquals("color", color.getChannel());
        assertEquals("viridis", color.getTo());
        assertTrue(color.isToPalette());
        ScaleSpec x = v.getScales().get(1);
        assertEquals("continuous", x.getType());
        assertEquals("log", x.getVia());
        assertEquals(2, x.getFrom().size());
        assertEquals("true", x.getSettings().get("reverse"));

        FacetSpec facet = v.getFacet();
        assertEquals(List.of("country"), facet.getVars());
        assertEquals(List.of("name"), facet.getBy());
        assertEquals("y", facet.getSettings().get("free"));

        Projection p = v.getProject();
        assertEquals("cartesian", p.getCoord());
        assertEquals(List.of("y", "x"), p.getAesthetics());

        assertEquals("Company", v.getLabels().get(0).getValue());
        assertNull(v.getLabels().get(1).getValue());
    }

    @Test
    void sqlIgnoresVisualise() {
        SqlQueryRenderer r = new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC"));
        String withViz = KQLTranspiler.builder(
                "FIND customers c FETCH c.company_name name VISUALISE name AS x DRAW bar", RESOLVER)
                .build().getSql(r);
        String withoutViz = KQLTranspiler.builder(
                "FIND customers c FETCH c.company_name name", RESOLVER)
                .build().getSql(r);
        assertEquals(withoutViz, withViz);
        assertFalse(withViz.toUpperCase(Locale.ROOT).contains("VISUALISE"));
    }

    @Test
    void roundTripsThroughGetKql() {
        String kql = "FIND customers c FETCH c.company_name name "
                + "VISUALISE name AS x DRAW bar LABEL title => 'Hi'";
        String formatted = KQLTranspiler.builder(kql, RESOLVER).build().getKql();
        assertTrue(formatted.contains("VISUALISE"), formatted);
        assertTrue(formatted.contains("DRAW bar"), formatted);
        Visualise reparsed = query(formatted).getVisualise();
        assertNotNull(reparsed);
        assertEquals("bar", reparsed.getLayers().get(0).getMark());
    }
}
