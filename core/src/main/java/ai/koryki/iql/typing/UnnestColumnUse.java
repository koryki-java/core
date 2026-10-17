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

package ai.koryki.iql.typing;

import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Select;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Which aliases of lists made into rows have their {@code ordinal} read in a select. The position
 * of an element costs on some engines, so the SQL asks the database for it only where the query
 * reads it; everything else is left out of the rows.
 *
 * <p>The aliases are those of the whole select, its sub-selects and the filters of its {@code
 * EXISTS}, which the walker does not enter by itself. An alias used again in a scope of its own
 * counts as used: more than needed is only slower, less would be wrong.
 */
public final class UnnestColumnUse {

    private UnnestColumnUse() {}

    public static Set<String> ordinalAliases(Select select) {
        Set<String> aliases = new HashSet<>();
        new Walker().walk(select, new Reads(aliases));
        return aliases;
    }

    private static final class Reads implements Visitor {

        private final Set<String> aliases;

        Reads(Set<String> aliases) {
            this.aliases = aliases;
        }

        @Override
        public boolean visit(Deque<Object> deque, Field field) {
            if (field.getAlias() != null && UnnestRows.ORDINAL.equalsIgnoreCase(field.getName())) {
                aliases.add(field.getAlias());
            }
            return true;
        }

        @Override
        public boolean visit(Deque<Object> deque, Exists exists) {
            Walker walker = new Walker();
            walker.walk(exists.getFilter(), this);
            walker.walk(exists.getHaving(), this);
            return true;
        }
    }
}
