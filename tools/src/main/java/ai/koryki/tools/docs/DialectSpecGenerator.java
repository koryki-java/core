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

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.kql.KQLTranspiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-dialect function &amp; operator specification — one markdown page per
 * dialect. Unlike {@link FunctionDocGenerator} (golden-asserted support matrix),
 * this is a richer, always-overwritten reference: a chapter per
 * {@link FunctionCategory}, a section per function/operator, the argument list,
 * the dialect rendering, and at least one <em>runnable</em> KQL sample
 * transpiled to that dialect's SQL.
 *
 * <p>Samples are real {@code .kql} files (see {@code tools/.../docs/samples}),
 * each bound to one of the prepared demo databases — {@code typecheck},
 * {@code temporal} or {@code northwind} — so the documented SQL is exactly what
 * the transpiler emits, never hand-written.
 */
public final class DialectSpecGenerator {

    /** One runnable sample: a KQL query bound to a demo database. */
    public record Sample(String function, String db, String fileName, String kql) {
    }

    private static final String NOTE =
            "<!-- Generated from the function catalog and runnable KQL samples — do not edit. -->";

    /** db name (typecheck/temporal/northwind) -> the model resolver for that database. */
    private final Map<String, LinkResolver> resolvers;

    public DialectSpecGenerator(Map<String, LinkResolver> resolvers) {
        this.resolvers = resolvers;
    }

    public String dialectSpec(String dialectName, SqlDialect dialect,
            FunctionRegistry canonical, List<Sample> samples) {

        Map<String, List<Sample>> byFunction = new HashMap<>();
        for (Sample s : samples) {
            byFunction.computeIfAbsent(s.function(), k -> new ArrayList<>()).add(s);
        }
        FunctionRenderer renderer = dialect.getFunctionRenderer();

        StringBuilder b = new StringBuilder();
        b.append(NOTE).append("\n\n");
        b.append("# Function & operator specification: ").append(dialectName).append("\n\n");
        b.append("Each first-level chapter is a function category; each second-level section a ")
                .append("function or operator. Every sample is a real KQL query run against the ")
                .append("*typecheck*, *temporal* or *northwind* demo database and transpiled to ")
                .append(dialectName).append(" SQL.\n");

        for (FunctionCategory category : FunctionCategory.values()) {
            Map<String, List<FunctionDefinition>> byName = new TreeMap<>();
            for (List<FunctionDefinition> set : canonical.all()) {
                if (set.get(0).getCategory() == category) {
                    byName.put(set.get(0).getName(), set);
                }
            }
            if (byName.isEmpty()) {
                continue;
            }
            b.append("\n# ").append(category.getTitle()).append("\n");
            for (Map.Entry<String, List<FunctionDefinition>> e : byName.entrySet()) {
                appendFunction(b, dialect, renderer, e.getKey(), e.getValue(),
                        byFunction.getOrDefault(e.getKey(), List.of()));
            }
        }
        return b.toString();
    }

    private void appendFunction(StringBuilder b, SqlDialect dialect, FunctionRenderer renderer,
            String name, List<FunctionDefinition> canonicalSet, List<Sample> samples) {

        List<FunctionDefinition> dialectSet = renderer.overloads(name);
        List<FunctionDefinition> effective = dialectSet.isEmpty() ? canonicalSet : dialectSet;

        b.append("\n## ").append(name).append("\n\n");

        for (FunctionDefinition d : effective) {
            if (d.getDescription() != null) {
                b.append(d.getDescription()).append("\n\n");
                break;
            }
        }
        for (FunctionDefinition d : effective) {
            if (d.getParagraph() != null) {
                b.append(d.getParagraph()).append("\n\n");
                break;
            }
        }
        if (effective.get(0).getKind() == FunctionKind.AGGREGATE) {
            b.append("*Aggregate function.*\n\n");
        }

        for (FunctionDefinition d : effective) {
            b.append("**`").append(FunctionDocGenerator.callForm(d)).append("`** → ")
                    .append(returnTypeLabel(d)).append("\n\n");
            appendArgs(b, d);
            b.append("Rendering: `").append(FunctionDocGenerator.preview(d)).append("`\n\n");
        }

        if (samples.isEmpty()) {
            b.append("_No runnable sample yet._\n");
            return;
        }
        for (Sample s : samples) {
            b.append("**Sample** — `").append(s.db()).append("` database\n\n");
            b.append("```kql\n").append(stripComments(s.kql())).append("\n```\n\n");
            b.append("```sql\n").append(transpile(dialect, s)).append("\n```\n");
        }
    }

    private void appendArgs(StringBuilder b, FunctionDefinition d) {
        if (d.getSignature() == null || d.getSignature().args().isEmpty()) {
            b.append("Arguments: none.\n\n");
            return;
        }
        b.append("| # | argument | family | required |\n|---|---|---|---|\n");
        int i = 1;
        for (FunctionArg a : d.getSignature().args()) {
            b.append("| ").append(i++)
                    .append(" | ").append(a.name())
                    .append(" | ").append(a.family() == null ? "any" : a.family().name())
                    .append(" | ").append(a.optional() ? "optional" : "required")
                    .append(" |\n");
        }
        b.append("\n");
    }

    private String transpile(SqlDialect dialect, Sample s) {
        LinkResolver resolver = resolvers.get(s.db());
        if (resolver == null) {
            return "-- unknown sample database: " + s.db();
        }
        try {
            return KQLTranspiler.builder(s.kql(), resolver).build()
                        .getSql(new SqlQueryRenderer(dialect, java.time.ZoneId.of("UTC"))).strip();
        } catch (RuntimeException ex) {
            return "-- transpile failed: " + ex.getMessage();
        }
    }

    private static String stripComments(String kql) {
        StringBuilder out = new StringBuilder();
        for (String line : kql.strip().split("\n", -1)) {
            if (!line.strip().startsWith("//")) {
                out.append(line).append("\n");
            }
        }
        return out.toString().strip();
    }

    private static String returnTypeLabel(FunctionDefinition d) {
        try {
            var t = d.returnType(null);
            return t != null ? t.getPhysicalTypeName() : "argument-dependent";
        } catch (RuntimeException e) {
            return "argument-dependent";
        }
    }
}
