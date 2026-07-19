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

import java.util.List;

/**
 * Kernel-density evaluation grid: a Silverman bandwidth plus an M-point grid
 * spanning the data extent (±3 bandwidths), derived from a probe row of
 * {@code (min, max, count, stddev)}. Shared by density and violin.
 */
final class KernelGrid {

    private final String hLit;
    private final String gridSelect;

    private KernelGrid(String hLit, String gridSelect) {
        this.hLit = hLit;
        this.gridSelect = gridSelect;
    }

    /** @return null if the density is undefined (no data, single point, or zero spread) */
    static KernelGrid of(List<List<Object>> probe, Layer layer, int defaultPoints) {
        if (probe.isEmpty()) {
            return null;
        }
        List<Object> r = probe.get(0);
        Number lo = num(r.get(0));
        Number hi = num(r.get(1));
        long n = longOf(r.get(2));
        Number sd = num(r.get(3));
        if (lo == null || hi == null || n < 2 || sd == null || sd.doubleValue() <= 0) {
            return null;
        }

        double h = 1.06 * sd.doubleValue() * Math.pow(n, -0.2); // Silverman's rule of thumb
        int m = Math.max(2, intOr(layer.getSettings().get("points"), defaultPoints));
        double gridLo = lo.doubleValue() - 3 * h;
        double gridHi = hi.doubleValue() + 3 * h;
        double step = (gridHi - gridLo) / (m - 1);

        String gridSelect = "(SELECT " + StatSql.lit(gridLo) + " + " + StatSql.lit(step)
                + " * gx AS v FROM range(0, " + m + ") t(gx))";
        return new KernelGrid(StatSql.lit(h), gridSelect);
    }

    String h() {
        return hLit;
    }

    String gridSelect() {
        return gridSelect;
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
