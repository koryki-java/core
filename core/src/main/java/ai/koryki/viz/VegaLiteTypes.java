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

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;

/**
 * Maps a resolved KQL column type to the Vega-Lite field {@code type}. Driven
 * off the type family, mirroring {@code jdbc/LocaleFormat}'s family switch:
 * numbers/intervals → {@code quantitative}, dates/times → {@code temporal},
 * everything else (boolean, text, uuid, json, blob) and unknown → {@code nominal}.
 * An explicit {@code SCALE} clause can override this later (Phase 2).
 */
public final class VegaLiteTypes {

    private VegaLiteTypes() {
    }

    public static String channelType(TypeDescriptor type) {
        if (type == null) {
            return "nominal";
        }
        TypeFamily family = type.getTypeFamily();
        if (family == null) {
            return "nominal";
        }
        if (Families.NUMERIC.accepts(family) || CoreTypeFamily.INTERVAL.equals(family)) {
            return "quantitative";
        }
        if (Families.TEMPORAL.accepts(family)) {
            return "temporal";
        }
        return "nominal";
    }
}
