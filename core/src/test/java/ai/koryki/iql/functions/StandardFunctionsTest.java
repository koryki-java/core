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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Aggregate classification is driven by FunctionDefinition metadata — the single source of truth.
 */
class StandardFunctionsTest {

    @Test
    void aggregatesAreClassifiedAsAggregate() {
        for (String agg : new String[] {"count", "sum", "avg", "min", "max", "string_agg"}) {
            assertTrue(StandardFunctions.isAggregate(agg), agg + " should be an aggregate");
        }
    }

    @Test
    void scalarsAndUnknownsAreNotAggregate() {
        assertFalse(StandardFunctions.isAggregate("substr"));
        assertFalse(StandardFunctions.isAggregate("length"));
        assertFalse(StandardFunctions.isAggregate("coalesce"));
        assertFalse(StandardFunctions.isAggregate("does_not_exist"));
    }
}
