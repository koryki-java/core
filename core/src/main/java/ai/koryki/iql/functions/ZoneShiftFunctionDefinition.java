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
package ai.koryki.iql.functions;

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.Families;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.List;

/**
 * Explicit zone crossing (docs/TEMPORAL.md, "Time zones"): the only way to leave the zone-free algebra.
 *
 * <ul>
 *   <li>{@code at_zone(value, 'Zone')} reads {@code value} as a model-zone wall-clock value and returns
 *       its wall-clock in the named zone — e.g. {@code date(at_zone(o.ts, 'Europe/Berlin'))} is the order
 *       day in Berlin. Shift: model zone → named zone.</li>
 *   <li>{@code to_utc(value, 'Zone')} is the inverse: it reads {@code value} as a wall-clock in the named
 *       zone and returns the model-zone wall-clock. Shift: named zone → model zone (named {@code to_utc}
 *       because the model zone defaults to UTC).</li>
 * </ul>
 *
 * <p>Both return a {@code TIMESTAMP}. The conversion needs the renderer's model zone, so this overrides
 * {@link #render} rather than using a static template, and delegates the per-engine SQL to
 * {@link ai.koryki.iql.SqlDialect#zoneShiftTimestamp} (which rejects on dialects without named-zone support).
 */
public class ZoneShiftFunctionDefinition extends FunctionDefinition {

    /** true = at_zone (model → named); false = to_utc (named → model). */
    private final boolean toNamedZone;

    public ZoneShiftFunctionDefinition(String name, boolean toNamedZone) {
        super(name, ReturnTypes.TIMESTAMP);
        this.toNamedZone = toNamedZone;
        args(FunctionArg.arg("value", Families.TEMPORAL), FunctionArg.arg("zone", CoreTypeFamily.TEXT));
    }

    @Override
    public String render(SqlSelectRenderer renderer, Function function, int indent) {
        checkArity(function);
        List<Expression> args = function.getArguments();
        String valueSql = renderer.toSql(args.get(0), indent);
        String namedZoneSql = renderer.toSql(args.get(1), indent);          // SQL-quoted, e.g. 'Europe/Berlin'
        String modelZoneSql = "'" + renderer.getModelZone().getId() + "'";
        String fromSql = toNamedZone ? modelZoneSql : namedZoneSql;
        String toSql   = toNamedZone ? namedZoneSql : modelZoneSql;
        return renderer.getDialect().zoneShiftTimestamp(valueSql, fromSql, toSql);
    }
}
