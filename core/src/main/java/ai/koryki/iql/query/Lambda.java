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
package ai.koryki.iql.query;

import java.util.ArrayList;
import java.util.List;

/**
 * An anonymous function that is an argument of another: {@code x -> @x * 2}, {@code (acc, x)
 * -> @acc + @x}. The parameters are named without their sigil; inside the body they are written
 * {@code @x} (see {@link Expression#getParameter()}). The body is a value or, for a predicate, an
 * expression holding a logical one.
 *
 * <p>A lambda is never a value of its own: the function it is passed to says how many parameters it
 * takes and of what types. Those types are not kept here, the model stays what was written: the
 * typing returns them as a {@link ai.koryki.iql.typing.LambdaTyping} and the renderer keeps them
 * for the rendering of that call.
 */
public class Lambda {

    private List<String> parameters = new ArrayList<>();
    private Expression body;

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public Expression getBody() {
        return body;
    }

    public void setBody(Expression body) {
        this.body = body;
    }
}
