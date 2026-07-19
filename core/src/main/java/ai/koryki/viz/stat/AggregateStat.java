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
 * Opt-in aggregation for an ordinary geom (bar/line/area/point): reduce the
 * {@code y} value per {@code x} category in the database, so a query that
 * returns raw rows still yields one mark per category. Triggered by
 * {@code SETTING aggregate => 'sum'|'mean'|'min'|'max'|'median'|'count'}
 * (default {@code sum}; {@code count} needs no y).
 */
public class AggregateStat implements StatTransform {

    private static final TypeDescriptor QUANTITATIVE =
            new TypeDescriptor("DOUBLE", null, CoreTypeFamily.FLOAT);
    private static final TypeDescriptor NOMINAL =
            new TypeDescriptor("VARCHAR", null, CoreTypeFamily.TEXT);

    @Override
    public StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping) {
        String group = mapping.get("x");
        if (group == null) {
            throw new KorykiaiException("aggregate " + layer.getMark() + " needs a category mapped to x");
        }
        String reduction = String.valueOf(layer.getSettings().getOrDefault("aggregate", "sum"))
                .toLowerCase(Locale.ROOT);
        String value = mapping.get("y");
        if (!"count".equals(reduction) && value == null) {
            throw new KorykiaiException("aggregate " + reduction + " needs a value mapped to y");
        }

        String g = StatSql.quote(group);
        String base = "(" + baseSql + ") kq_stat";
        StringBuilder where = new StringBuilder(" WHERE ").append(g).append(" IS NOT NULL");
        if (value != null) {
            where.append(" AND ").append(StatSql.quote(value)).append(" IS NOT NULL");
        }
        String sql = "SELECT " + g + " AS grp, " + aggExpr(reduction, value) + " AS value FROM "
                + base + where + " GROUP BY " + g + " ORDER BY grp";

        List<List<Object>> rows = runner.run(sql);
        List<VizColumn> columns = Arrays.asList(
                new VizColumn("group", NOMINAL),
                new VizColumn("value", QUANTITATIVE));
        StatResult.Part part = new StatResult.Part(layer.getMark(), Arrays.asList(
                new StatResult.ChannelBinding("x", "group", false, group),
                new StatResult.ChannelBinding("y", "value", false,
                        value == null ? reduction : reduction + " of " + value)));
        return new StatResult(Collections.singletonList(part), columns, rows);
    }

    private static String aggExpr(String reduction, String value) {
        String v = value == null ? null : StatSql.quote(value);
        switch (reduction) {
            case "count":
                return "count(*)";
            case "sum":
                return "sum(" + v + ")";
            case "mean":
            case "avg":
                return "avg(" + v + ")";
            case "min":
                return "min(" + v + ")";
            case "max":
                return "max(" + v + ")";
            case "median":
                return "percentile_cont(0.5) WITHIN GROUP (ORDER BY " + v + ")";
            default:
                throw new KorykiaiException("unsupported aggregate: " + reduction);
        }
    }
}
