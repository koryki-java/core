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

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of statistical geoms → their {@link StatTransform}. A geom absent
 * here draws the raw result directly (no aggregation).
 */
public final class StatTransforms {

    private static final Map<String, StatTransform> BY_GEOM = new HashMap<>();
    private static final StatTransform AGGREGATE = new AggregateStat();

    static {
        BY_GEOM.put("histogram", new HistogramStat());
        BY_GEOM.put("boxplot", new BoxplotStat());
        BY_GEOM.put("heatmap", new HeatmapStat());
        BY_GEOM.put("smooth", new SmoothStat());
        BY_GEOM.put("density", new DensityStat());
        BY_GEOM.put("violin", new ViolinStat());
    }

    private StatTransforms() {
    }

    /** The transform for a geom/mark, or null if it is not a statistical layer. */
    public static StatTransform forGeom(String geom) {
        return geom == null ? null : BY_GEOM.get(geom);
    }

    /** Opt-in aggregation applied to an ordinary geom carrying a {@code SETTING aggregate =>}. */
    public static StatTransform aggregate() {
        return AGGREGATE;
    }
}
