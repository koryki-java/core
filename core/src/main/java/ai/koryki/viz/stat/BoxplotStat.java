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
import java.util.List;
import java.util.Map;

/**
 * Box-and-whisker summary computed in the database. Aggregates the {@code y}
 * value (optionally grouped by {@code x}) into quartiles + Tukey whiskers via a
 * single `percentile_cont` query, then renders a composite: a whisker rule
 * (lower→upper), a box bar (q1→q3), and a median tick.
 *
 * <p>Whiskers are the 1.5·IQR fences clamped to the data extent (VL's default
 * boxplot look); exact in-fence whiskers and outlier points are not computed yet.
 */
public class BoxplotStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);
    private static final TypeDescriptor NOMINAL =
            new TypeDescriptor("VARCHAR", null, CoreTypeFamily.TEXT);

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String value = mapping.get("y");
        if (value == null) {
            throw new KorykiaiException("boxplot layer needs a value column mapped to y");
        }
        String group = mapping.get("x"); // optional: one box per category, else a single box
        boolean grouped = group != null;

        String v = quote(value);
        String g = grouped ? quote(group) : null;
        String base = "(" + baseSql + ") kq_stat";

        StringBuilder sql = new StringBuilder("WITH q AS (SELECT ");
        if (grouped) {
            sql.append(g).append(" AS grp, ");
        }
        sql.append("percentile_cont(0.25) WITHIN GROUP (ORDER BY ").append(v).append(") AS q1, ")
                .append("percentile_cont(0.5) WITHIN GROUP (ORDER BY ").append(v).append(") AS med, ")
                .append("percentile_cont(0.75) WITHIN GROUP (ORDER BY ").append(v).append(") AS q3, ")
                .append("min(").append(v).append(") AS vmin, max(").append(v).append(") AS vmax ")
                .append("FROM ").append(base).append(" WHERE ").append(v).append(" IS NOT NULL");
        if (grouped) {
            sql.append(" AND ").append(g).append(" IS NOT NULL GROUP BY ").append(g);
        }
        sql.append(") SELECT ");
        if (grouped) {
            sql.append("grp, ");
        }
        sql.append("q1, med, q3, ")
                .append("greatest(vmin, q1 - 1.5E0 * (q3 - q1)) AS lower, ")
                .append("least(vmax, q3 + 1.5E0 * (q3 - q1)) AS upper FROM q");
        if (grouped) {
            sql.append(" ORDER BY grp");
        }

        List<List<Object>> rows = runner.run(sql.toString());

        List<VizColumn> columns = new ArrayList<>();
        if (grouped) {
            columns.add(new VizColumn("group", NOMINAL));
        }
        columns.add(new VizColumn("q1", QUANTITATIVE));
        columns.add(new VizColumn("median", QUANTITATIVE));
        columns.add(new VizColumn("q3", QUANTITATIVE));
        columns.add(new VizColumn("lower", QUANTITATIVE));
        columns.add(new VizColumn("upper", QUANTITATIVE));

        // composite: whisker rule (lower→upper), box bar (q1→q3), median tick
        List<StatResult.ChannelBinding> whisker = new ArrayList<>();
        List<StatResult.ChannelBinding> box = new ArrayList<>();
        List<StatResult.ChannelBinding> median = new ArrayList<>();
        if (grouped) {
            whisker.add(new StatResult.ChannelBinding("x", "group", false, group));
            box.add(new StatResult.ChannelBinding("x", "group", false, group));
            median.add(new StatResult.ChannelBinding("x", "group", false, group));
        }
        whisker.add(new StatResult.ChannelBinding("y", "lower", false, value));
        whisker.add(new StatResult.ChannelBinding("y2", "upper", false, null));
        box.add(new StatResult.ChannelBinding("y", "q1", false, value));
        box.add(new StatResult.ChannelBinding("y2", "q3", false, null));
        median.add(new StatResult.ChannelBinding("y", "median", false, value));

        List<StatResult.Part> parts = Arrays.asList(
                new StatResult.Part("rule", whisker),
                new StatResult.Part("bar", box),
                new StatResult.Part("tick", median));

        return new StatResult(parts, columns, rows);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
