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
package ai.koryki.viz.stat;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.query.viz.Layer;
import ai.koryki.viz.VizColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Two-dimensional binned count (heatmap): bin {@code x} and {@code y} into a
 * grid in the database and count rows per cell, rendered as a {@code rect} with
 * pre-binned x/x2 and y/y2 and a count-driven colour. {@code SETTING bins => N}
 * sets the grid resolution on both axes (default 20).
 */
public class HeatmapStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);
    private static final int DEFAULT_BINS = 20;

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String xCol = mapping.get("x");
        String yCol = mapping.get("y");
        if (xCol == null || yCol == null) {
            throw new KorykiaiException("heatmap needs columns mapped to x and y");
        }
        String x = StatSql.quote(xCol);
        String y = StatSql.quote(yCol);
        String base = "(" + baseSql + ") kq_stat";
        String filter = " WHERE " + x + " IS NOT NULL AND " + y + " IS NOT NULL";

        List<VizColumn> columns = Arrays.asList(
                new VizColumn("x_start", QUANTITATIVE),
                new VizColumn("x_end", QUANTITATIVE),
                new VizColumn("y_start", QUANTITATIVE),
                new VizColumn("y_end", QUANTITATIVE),
                new VizColumn("count", QUANTITATIVE));
        StatResult.Part rect = new StatResult.Part("rect", Arrays.asList(
                new StatResult.ChannelBinding("x", "x_start", true, xCol),
                new StatResult.ChannelBinding("x2", "x_end", false, null),
                new StatResult.ChannelBinding("y", "y_start", true, yCol),
                new StatResult.ChannelBinding("y2", "y_end", false, null),
                new StatResult.ChannelBinding("color", "count", false, null)));
        List<StatResult.Part> parts = Collections.singletonList(rect);

        List<List<Object>> probe = runner.run("SELECT min(" + x + ") AS xlo, max(" + x + ") AS xhi, "
                + "min(" + y + ") AS ylo, max(" + y + ") AS yhi, count(*) AS n FROM " + base + filter);
        Number xlo = probe.isEmpty() ? null : num(probe.get(0).get(0));
        Number xhi = probe.isEmpty() ? null : num(probe.get(0).get(1));
        Number ylo = probe.isEmpty() ? null : num(probe.get(0).get(2));
        Number yhi = probe.isEmpty() ? null : num(probe.get(0).get(3));
        long n = probe.isEmpty() ? 0 : longOf(probe.get(0).get(4));
        if (n == 0 || xlo == null || xhi == null || ylo == null || yhi == null) {
            return new StatResult(parts, columns, new ArrayList<>());
        }

        int bins = Math.max(1, intOr(layer.getSettings().get("bins"), DEFAULT_BINS));
        // note: 'by' is a reserved word, so the bin-index aliases are bin_x/bin_y
        Axis ax = new Axis(x, xlo.doubleValue(), xhi.doubleValue(), bins, "bin_x");
        Axis ay = new Axis(y, ylo.doubleValue(), yhi.doubleValue(), bins, "bin_y");

        String binSql = "SELECT " + ax.start() + " AS x_start, " + ax.end() + " AS x_end, "
                + ay.start() + " AS y_start, " + ay.end() + " AS y_end, cnt AS cell_count "
                + "FROM (SELECT " + ax.index() + " AS bin_x, " + ay.index() + " AS bin_y, count(*) AS cnt FROM "
                + base + filter + " GROUP BY 1, 2) g ORDER BY x_start, y_start";

        return new StatResult(parts, columns, runner.run(binSql));
    }

    /** Bin expressions for one axis: the grouping index, and the outer edges from the index alias. */
    private static final class Axis {
        private final String colSql;
        private final double lo;
        private final double hi;
        private final int bins;
        private final String alias;

        Axis(String colSql, double lo, double hi, int bins, String alias) {
            this.colSql = colSql;
            this.lo = lo;
            this.hi = hi;
            this.bins = hi > lo ? bins : 1;
            this.alias = alias;
        }

        private double width() {
            return hi > lo ? (hi - lo) / bins : 1.0;
        }

        String index() {
            return "CASE WHEN " + colSql + " = " + StatSql.lit(hi) + " THEN " + (bins - 1)
                    + " ELSE floor((" + colSql + " - " + StatSql.lit(lo) + ") / " + StatSql.lit(width()) + ") END";
        }

        String start() {
            return StatSql.lit(lo) + " + " + StatSql.lit(width()) + " * " + alias;
        }

        String end() {
            return StatSql.lit(lo) + " + " + StatSql.lit(width()) + " * (" + alias + " + 1)";
        }
    }

    private static long longOf(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static Number num(Object v) {
        return v instanceof Number ? (Number) v : null;
    }

    private static int intOr(Object v, int dflt) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
        return dflt;
    }
}
