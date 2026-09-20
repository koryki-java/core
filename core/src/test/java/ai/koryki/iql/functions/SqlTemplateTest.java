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

import static ai.koryki.iql.functions.FunctionArg.arg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlTemplateTest {

    @Test
    void literalOnlyTemplateNeedsNoRenderer() {
        Function f = function("now");
        String sql = new SqlTemplate("CURRENT_TIMESTAMP").render(null, f, 0);
        assertEquals("CURRENT_TIMESTAMP", sql);
    }

    @Test
    void unclosedPlaceholderIsRejectedAtConstruction() {
        assertThrows(KorykiaiException.class, () -> new SqlTemplate("POSITION({0 IN {1})"));
    }

    @Test
    void nonNumericPlaceholderIsRejectedAtConstruction() {
        assertThrows(KorykiaiException.class, () -> new SqlTemplate("FOO({x})"));
    }

    @Test
    void outOfRangeArgumentFailsAtRenderTime() {
        Function f = function("foo");
        SqlTemplate t = new SqlTemplate("FOO({0})");
        assertThrows(KorykiaiException.class, () -> t.render(null, f, 0));
    }

    @Test
    void templatedDefinitionEnforcesArityBeforeRendering() {
        FunctionDefinition def =
                new FunctionDefinition("position", ReturnTypes.INTEGER)
                        .args(arg("substr"), arg("str"))
                        .template("POSITION({0} IN {1})");

        Function call = function("position");
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> def.render(null, call, 0));
        assertEquals("position expects (substr, str), got 0 arguments", e.getMessage());
    }

    private static Function function(String name) {
        Function f = new Function();
        f.setFunc(name);
        f.setArguments(List.<Expression>of());
        return f;
    }
}
