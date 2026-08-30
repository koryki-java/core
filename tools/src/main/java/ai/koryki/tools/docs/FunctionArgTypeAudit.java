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
package ai.koryki.tools.docs;

import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionSignature;
import ai.koryki.iql.functions.StandardFunctions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Argument-type coverage audit: do all function definitions declare a type family for every
 * argument? Unlike {@link FunctionDocCoverage}, this checks <strong>every overload</strong> in each
 * name's overload set (not just the representative), so a set with one typed and one untyped
 * overload is reported in full.
 *
 * <p>An argument is "untyped" when {@link FunctionArg#family()} is {@code null} — the any-type
 * wildcard ({@code arg("name")} rather than {@code arg("name", FAMILY)}). A definition with no
 * signature ({@link FunctionDefinition#getSignature()} {@code == null}) declares no arguments or
 * arity at all and is reported separately.
 *
 * <p>Run: <pre>./gradlew :tools:argTypeAudit -q</pre>
 */
public final class FunctionArgTypeAudit {

    private FunctionArgTypeAudit() {
    }

    public static String report(FunctionRegistry registry) {
        Map<FunctionCategory, List<FunctionDefinition>> byCategory = new EnumMap<>(FunctionCategory.class);
        for (List<FunctionDefinition> overloadSet : registry.all()) {
            for (FunctionDefinition d : overloadSet) {                 // every overload, not just the first
                byCategory.computeIfAbsent(d.getCategory(), k -> new ArrayList<>()).add(d);
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("Function argument-type coverage — StandardFunctions (all overloads)\n");
        b.append("(untyped arg = FunctionArg.family() is null; \"no signature\" = no declared args/arity)\n\n");

        int defs = 0, fullyTyped = 0, withUntyped = 0, noSignature = 0, totalArgs = 0, untypedArgs = 0;
        for (FunctionCategory category : FunctionCategory.values()) {
            List<FunctionDefinition> defsInCategory = byCategory.get(category);
            if (defsInCategory == null) {
                continue;
            }
            List<String> gaps = new ArrayList<>();
            for (FunctionDefinition d : defsInCategory) {
                defs++;
                FunctionSignature sig = d.getSignature();
                if (sig == null) {
                    noSignature++;
                    gaps.add("  " + d.getName() + " — NO SIGNATURE (declares no arguments)");
                    continue;
                }
                List<String> untyped = new ArrayList<>();
                for (FunctionArg a : sig.args()) {
                    totalArgs++;
                    if (a.family() == null) {
                        untyped.add(a.name());
                        untypedArgs++;
                    }
                }
                if (untyped.isEmpty()) {
                    fullyTyped++;
                } else {
                    withUntyped++;
                    gaps.add("  " + d.getName() + sig + " — untyped: " + String.join(", ", untyped));
                }
            }
            if (!gaps.isEmpty()) {
                b.append(category.name()).append(" (").append(category.getTitle()).append(")\n");
                gaps.forEach(g -> b.append(g).append('\n'));
                b.append('\n');
            }
        }

        boolean complete = withUntyped == 0 && noSignature == 0 && untypedArgs == 0;
        b.append(complete ? "COMPLETE: every argument of every overload has a declared type.\n\n" : "");
        b.append("TOTAL: ").append(defs).append(" overload definitions")
                .append(" | fully typed: ").append(fullyTyped)
                .append(" | has untyped args: ").append(withUntyped)
                .append(" | no signature: ").append(noSignature)
                .append(" | args: ").append(totalArgs)
                .append(", untyped: ").append(untypedArgs)
                .append('\n');
        return b.toString();
    }

    public static void main(String[] args) {
        System.out.print(report(StandardFunctions.registry()));
    }
}
