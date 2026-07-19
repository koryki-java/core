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
import ai.koryki.iql.query.viz.Mapping;
import ai.koryki.viz.VizColumn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Equal-width histogram computed in the database: a probe query gets the
 * domain and row count, the bin width is derived (SETTING {@code bins}/
 * {@code binwidth}, else Sturges), then a {@code GROUP BY} query returns one row
 * per non-empty bin with {@code bin_start, bin_end, count, density}. Rendered as
 * a pre-binned bar; {@code y} defaults to count, or density via
 * {@code REMAPPING density AS y}.
 */
public class HistogramStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String statColumn = mapping.get("x");
        if (statColumn == null) {
            throw new KorykiaiException("histogram layer needs a column mapped to x");
        }
        String col = quote(statColumn);
        String base = "(" + baseSql + ") kq_stat";

        List<List<Object>> probe = runner.run(
                "SELECT min(" + col + ") AS lo, max(" + col + ") AS hi, count(" + col + ") AS n FROM " + base);
        Number lo = probe.isEmpty() ? null : num(probe.get(0).get(0));
        Number hi = probe.isEmpty() ? null : num(probe.get(0).get(1));
        long n = probe.isEmpty() ? 0 : longOf(probe.get(0).get(2));

        List<VizColumn> columns = Arrays.asList(
                new VizColumn("bin_start", QUANTITATIVE),
                new VizColumn("bin_end", QUANTITATIVE),
                new VizColumn("count", QUANTITATIVE),
                new VizColumn("density", QUANTITATIVE));
        String yField = remapsDensityToY(layer) ? "density" : "count";
        StatResult.Part bar = new StatResult.Part("bar", Arrays.asList(
                new StatResult.ChannelBinding("x", "bin_start", true, statColumn),
                new StatResult.ChannelBinding("x2", "bin_end", false, null),
                new StatResult.ChannelBinding("y", yField, false, null)));
        List<StatResult.Part> parts = Collections.singletonList(bar);

        if (n == 0 || lo == null || hi == null) {
            return new StatResult(parts, columns, new ArrayList<>()); // empty histogram
        }

        double loD = lo.doubleValue();
        double hiD = hi.doubleValue();
        Double binwidth = dbl(layer.getSettings().get("binwidth"));
        Integer binsSetting = intOf(layer.getSettings().get("bins"));

        int bins;
        double width;
        if (hiD == loD) {
            bins = 1;
            width = 1.0;
        } else if (binwidth != null && binwidth > 0) {
            width = binwidth;
            bins = Math.max(1, (int) Math.ceil((hiD - loD) / width));
        } else {
            bins = Math.max(1, binsSetting != null ? binsSetting : sturges(n));
            width = (hiD - loD) / bins;
        }

        String w = lit(width);
        String lit = lit(loD);
        // clamp the maximum value into the last bin rather than a lone overflow bin
        String binIndex = "CASE WHEN " + col + " = " + lit(hiD) + " THEN " + (bins - 1)
                + " ELSE floor((" + col + " - " + lit + ") / " + w + ") END";
        String binSql = "SELECT " + lit + " + " + w + " * b AS bin_start, "
                + lit + " + " + w + " * (b + 1) AS bin_end, "
                + "cnt AS bin_count, "
                + "cnt / (" + lit((double) n) + " * " + w + ") AS density "
                + "FROM (SELECT " + binIndex + " AS b, count(*) AS cnt FROM " + base
                + " WHERE " + col + " IS NOT NULL GROUP BY 1) g ORDER BY bin_start";

        return new StatResult(parts, columns, runner.run(binSql));
    }

    /** Sturges' rule: ceil(log2 n) + 1 bins. */
    private static int sturges(long n) {
        return (int) Math.ceil(Math.log(n) / Math.log(2)) + 1;
    }

    private static boolean remapsDensityToY(Layer layer) {
        for (Mapping m : layer.getRemapping()) {
            if ("y".equals(m.getChannel()) && "density".equals(m.getColumn())) {
                return true;
            }
        }
        return false;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * A SQL DOUBLE literal. The {@code E0} suffix forces floating-point parsing:
     * without it engines read {@code 91.60…} as a high-scale DECIMAL and the
     * downstream multiplication overflows (e.g. DuckDB {@code DECIMAL(18)}).
     */
    private static String lit(double d) {
        return BigDecimal.valueOf(d).toPlainString() + "E0";
    }

    private static Number num(Object v) {
        return v instanceof Number ? (Number) v : null;
    }

    private static long longOf(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static Double dbl(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer intOf(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
