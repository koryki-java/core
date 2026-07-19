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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordinary-least-squares trend line, fitted in the database via
 * {@code regr_slope}/{@code regr_intercept}. Returns the two endpoints of the
 * fitted line (at the x extent), rendered as a line. {@code SETTING method}
 * defaults to {@code ols} ({@code lm}); {@code loess} is not yet supported.
 */
public class SmoothStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String xCol = mapping.get("x");
        String yCol = mapping.get("y");
        if (xCol == null || yCol == null) {
            throw new KorykiaiException("smooth needs columns mapped to x and y");
        }
        String method = String.valueOf(layer.getSettings().getOrDefault("method", "ols"))
                .toLowerCase(Locale.ROOT);
        if (!"ols".equals(method) && !"lm".equals(method)) {
            throw new KorykiaiException("smooth only supports method 'ols' (got '" + method + "')");
        }

        String x = StatSql.quote(xCol);
        String y = StatSql.quote(yCol);
        String base = "(" + baseSql + ") kq_stat";
        String sql = "WITH f AS (SELECT regr_slope(" + y + ", " + x + ") AS s, "
                + "regr_intercept(" + y + ", " + x + ") AS i, "
                + "min(" + x + ") AS lo, max(" + x + ") AS hi FROM " + base
                + " WHERE " + x + " IS NOT NULL AND " + y + " IS NOT NULL) "
                + "SELECT x, y FROM ("
                + "SELECT lo AS x, s * lo + i AS y FROM f WHERE s IS NOT NULL "
                + "UNION ALL SELECT hi AS x, s * hi + i AS y FROM f WHERE s IS NOT NULL"
                + ") fit ORDER BY x";

        List<VizColumn> columns = Arrays.asList(
                new VizColumn("x", QUANTITATIVE),
                new VizColumn("y", QUANTITATIVE));
        StatResult.Part line = new StatResult.Part("line", Arrays.asList(
                new StatResult.ChannelBinding("x", "x", false, xCol),
                new StatResult.ChannelBinding("y", "y", false, yCol)));

        return new StatResult(Collections.singletonList(line), columns, runner.run(sql));
    }
}
