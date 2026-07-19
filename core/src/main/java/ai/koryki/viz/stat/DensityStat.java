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
 * Gaussian kernel density estimate computed in the database: a probe gets the
 * domain, count and stddev; a Silverman bandwidth is derived; then a grid ×
 * data cross-join sums the kernel per grid point. Rendered as an area of
 * density over the value. {@code SETTING points => N} sets the grid resolution
 * (default 64).
 */
public class DensityStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);
    private static final int DEFAULT_POINTS = 64;

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String xCol = mapping.get("x");
        if (xCol == null) {
            throw new KorykiaiException("density needs a column mapped to x");
        }
        String x = StatSql.quote(xCol);
        String base = "(" + baseSql + ") kq_stat";
        String filter = " WHERE " + x + " IS NOT NULL";

        List<VizColumn> columns = Arrays.asList(
                new VizColumn("x", QUANTITATIVE),
                new VizColumn("density", QUANTITATIVE));
        StatResult.Part area = new StatResult.Part("area", Arrays.asList(
                new StatResult.ChannelBinding("x", "x", false, xCol),
                new StatResult.ChannelBinding("y", "density", false, "density")));
        List<StatResult.Part> parts = Collections.singletonList(area);

        List<List<Object>> probe = runner.run("SELECT min(" + x + ") AS lo, max(" + x + ") AS hi, "
                + "count(" + x + ") AS n, stddev_samp(" + x + ") AS sd FROM " + base + filter);
        KernelGrid grid = KernelGrid.of(probe, layer, DEFAULT_POINTS);
        if (grid == null) {
            return new StatResult(parts, columns, new ArrayList<>());
        }

        String sql = "WITH d AS (SELECT " + x + " AS xi FROM " + base + filter + ") "
                + "SELECT g.v AS x, "
                + "sum(exp(-0.5E0 * pow((g.v - d.xi) / " + grid.h() + ", 2))) "
                + "/ (count(*) * " + grid.h() + " * sqrt(2 * pi())) AS density "
                + "FROM " + grid.gridSelect() + " g CROSS JOIN d GROUP BY g.v ORDER BY x";

        return new StatResult(parts, columns, runner.run(sql));
    }
}
