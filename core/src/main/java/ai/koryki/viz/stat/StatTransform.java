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

import ai.koryki.iql.query.viz.Layer;

import java.util.Map;

/**
 * Computes a statistical layer (histogram, boxplot, …) by aggregating in the
 * database. The stat is generated as SQL wrapped around {@code baseSql} and run
 * via {@code runner}, so only the aggregated rows transfer — not the raw data.
 */
public interface StatTransform {

    /**
     * @param runner  runs SQL and returns rows
     * @param baseSql the transpiled query SQL (a single SELECT, no trailing ';')
     * @param layer   the DRAW layer (its SETTING/REMAPPING refine the stat)
     * @param mapping the layer's channel → output-column bindings (e.g. x, y);
     *                each transform reads the channels it needs and validates them
     */
    StatResult compute(SqlRunner runner, String baseSql, Layer layer, Map<String, String> mapping);
}
