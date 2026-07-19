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
package ai.koryki.viz;

import ai.koryki.catalog.types.TypeDescriptor;

/**
 * One output column of a query as the Vega-Lite emitter sees it: the field
 * name (the FETCH alias, used as the JSON key and {@code encoding.field}) and
 * its resolved type (may be null when the type could not be inferred).
 */
public class VizColumn {

    private final String name;
    private final TypeDescriptor type;

    // optional query semantics used to polish the encoding (features 2/3/5);
    // all null on the pure emitter path (no query investigation available).
    private final String metric;          // semantic column name → axis/legend title
    private final String unit;            // physical unit symbol → title suffix
    private final String grain;           // temporal grain (day/month/year…) → axis format
    private final Integer orderPriority;  // ORDER BY position (lowest = primary sort key)
    private final String orderDirection;  // "ASC" / "DESC"
    private final String quantityDim;     // physical-dimension key → scale grouping (money vs count)

    public VizColumn(String name, TypeDescriptor type) {
        this(name, type, null, null, null, null, null);
    }

    public VizColumn(String name, TypeDescriptor type, String metric, String unit,
                     String grain, Integer orderPriority, String orderDirection) {
        this(name, type, metric, unit, grain, orderPriority, orderDirection, null);
    }

    public VizColumn(String name, TypeDescriptor type, String metric, String unit,
                     String grain, Integer orderPriority, String orderDirection, String quantityDim) {
        this.name = name;
        this.type = type;
        this.metric = metric;
        this.unit = unit;
        this.grain = grain;
        this.orderPriority = orderPriority;
        this.orderDirection = orderDirection;
        this.quantityDim = quantityDim;
    }

    public String getName() {
        return name;
    }

    public TypeDescriptor getType() {
        return type;
    }

    public String getMetric() {
        return metric;
    }

    public String getUnit() {
        return unit;
    }

    public String getGrain() {
        return grain;
    }

    public Integer getOrderPriority() {
        return orderPriority;
    }

    public String getOrderDirection() {
        return orderDirection;
    }

    public String getQuantityDim() {
        return quantityDim;
    }
}
