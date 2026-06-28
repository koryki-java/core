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

import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.StandardFunctions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Doc-coverage worklist for {@code docs/functions/*}: which catalog functions still have thin
 * documentation metadata. A function is "thin" when it carries no {@code .doc()} one-liner or no
 * {@code .example()}; {@code .paragraph()} is optional and only counted, never required.
 *
 * <p>Run it to get the enrichment worklist:
 * <pre>./gradlew :tools:coverageReport -q</pre>
 * or invoke {@link #report(FunctionRegistry)} from a test. It reads only the catalog metadata, so it
 * never touches the generated markdown.
 */
public final class FunctionDocCoverage {

    private FunctionDocCoverage() {
    }

    public static String report(FunctionRegistry registry) {
        Map<FunctionCategory, List<FunctionDefinition>> byCategory = new EnumMap<>(FunctionCategory.class);
        for (List<FunctionDefinition> set : registry.all()) {
            FunctionDefinition d = set.get(0);            // representative overload carries the docs
            byCategory.computeIfAbsent(d.getCategory(), k -> new ArrayList<>()).add(d);
        }

        StringBuilder b = new StringBuilder();
        b.append("Function doc coverage — generated from StandardFunctions\n");
        b.append("(thin = no .doc() one-liner, or no .example())\n\n");

        int total = 0, fullyDocumented = 0, missingDoc = 0, missingExample = 0, withParagraph = 0;
        for (FunctionCategory category : FunctionCategory.values()) {
            List<FunctionDefinition> defs = byCategory.get(category);
            if (defs == null) {
                continue;
            }
            List<String> noDoc = new ArrayList<>();
            List<String> noExample = new ArrayList<>();
            for (FunctionDefinition d : defs) {
                total++;
                boolean hasDoc = d.getDescription() != null;
                boolean hasExample = d.getExample() != null;
                if (d.getParagraph() != null) {
                    withParagraph++;
                }
                if (!hasDoc) {
                    noDoc.add(d.getName());
                }
                if (!hasExample) {
                    noExample.add(d.getName());
                }
                if (hasDoc && hasExample) {
                    fullyDocumented++;
                }
            }
            missingDoc += noDoc.size();
            missingExample += noExample.size();

            b.append(category.name()).append(" (").append(category.getTitle()).append("): ")
                    .append(defs.size()).append(" functions");
            if (noDoc.isEmpty() && noExample.isEmpty()) {
                b.append(" — complete");
            }
            b.append('\n');
            if (!noDoc.isEmpty()) {
                b.append("  missing doc:     ").append(String.join(", ", noDoc)).append('\n');
            }
            if (!noExample.isEmpty()) {
                b.append("  missing example: ").append(String.join(", ", noExample)).append('\n');
            }
            b.append('\n');
        }

        b.append("TOTAL: ").append(total).append(" functions")
                .append(" | fully documented (doc+example): ").append(fullyDocumented)
                .append(" | missing doc: ").append(missingDoc)
                .append(" | missing example: ").append(missingExample)
                .append(" | with paragraph: ").append(withParagraph)
                .append('\n');
        return b.toString();
    }

    public static void main(String[] args) {
        System.out.print(report(StandardFunctions.registry()));
    }
}
