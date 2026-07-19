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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Per-group kernel density of the {@code y} value grouped by {@code x}. Rendered
 * as one density curve per group (coloured line over the value), i.e. a
 * distribution comparison. This is NOT the mirrored-violin geometry — that needs
 * an offset-area rendering that does not fit the stat-layer model yet; the data
 * (per-group KDE) is the same. Ungrouped (no x) falls back to a single curve.
 */
public class ViolinStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);
    private static final TypeDescriptor NOMINAL =
            new TypeDescriptor("VARCHAR", null, CoreTypeFamily.TEXT);
    private static final int DEFAULT_POINTS = 64;

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String value = mapping.get("y");
        if (value == null) {
            throw new KorykiaiException("violin needs a value column mapped to y");
        }
        String group = mapping.get("x"); // optional: one curve per category
        boolean grouped = group != null;

        String v = StatSql.quote(value);
        String g = grouped ? StatSql.quote(group) : null;
        String base = "(" + baseSql + ") kq_stat";
        String filter = " WHERE " + v + " IS NOT NULL" + (grouped ? " AND " + g + " IS NOT NULL" : "");

        List<VizColumn> columns = new ArrayList<>();
        List<StatResult.ChannelBinding> bindings = new ArrayList<>();
        bindings.add(new StatResult.ChannelBinding("x", "value", false, value));
        bindings.add(new StatResult.ChannelBinding("y", "density", false, "density"));
        if (grouped) {
            columns.add(new VizColumn("group", NOMINAL));
            bindings.add(new StatResult.ChannelBinding("color", "group", false, group));
        }
        columns.add(new VizColumn("value", QUANTITATIVE));
        columns.add(new VizColumn("density", QUANTITATIVE));
        List<StatResult.Part> parts = Collections.singletonList(new StatResult.Part("line", bindings));

        List<List<Object>> probe = runner.run("SELECT min(" + v + ") AS lo, max(" + v + ") AS hi, "
                + "count(" + v + ") AS n, stddev_samp(" + v + ") AS sd FROM " + base + filter);
        KernelGrid grid = KernelGrid.of(probe, layer, DEFAULT_POINTS);
        if (grid == null) {
            return new StatResult(parts, columns, new ArrayList<>());
        }

        // count(*) within (group, grid point) = that group's sample size → correct per-group density
        String select = grouped ? "d.grp AS \"group\", g.v AS value, " : "g.v AS value, ";
        String groupBy = grouped ? " GROUP BY d.grp, g.v ORDER BY \"group\", value" : " GROUP BY g.v ORDER BY value";
        String d = grouped
                ? "(SELECT " + g + " AS grp, " + v + " AS xi FROM " + base + filter + ")"
                : "(SELECT " + v + " AS xi FROM " + base + filter + ")";

        String sql = "SELECT " + select
                + "sum(exp(-0.5E0 * pow((g.v - d.xi) / " + grid.h() + ", 2))) "
                + "/ (count(*) * " + grid.h() + " * sqrt(2 * pi())) AS density "
                + "FROM " + grid.gridSelect() + " g CROSS JOIN " + d + " d" + groupBy;

        return new StatResult(parts, columns, runner.run(sql));
    }
}
