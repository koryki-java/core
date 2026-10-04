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
 * A value and the steps taken into it: {@code c.items@1@sku} is the base {@code c.items} and two
 * selectors. A chain is kept as one node, not as nested ones, because a dialect renders it as a
 * whole: a JSON path folds all its steps into one string.
 */
public class Selection {

    private Expression base;
    private List<Selector> selectors = new ArrayList<>();

    public Expression getBase() {
        return base;
    }

    public void setBase(Expression base) {
        this.base = base;
    }

    public List<Selector> getSelectors() {
        return selectors;
    }

    public void setSelectors(List<Selector> selectors) {
        this.selectors = selectors;
    }
}
