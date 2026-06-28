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

import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeFamily;
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.FunctionSignature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Generates markdown documentation from the function catalog — the docs are a
 * build artifact of the metadata, never written by hand, so they cannot drift
 * from the implementation. Per-dialect renderings are produced from the
 * definitions' {@link ai.koryki.iql.functions.SqlTemplate} previews.
 *
 * <p>Used by golden-file tests (one per dialect module): a missing file is
 * generated, an existing file is compared — see {@link #sync}.
 */
public final class FunctionDocGenerator {

    private static final String GENERATED_NOTE =
            "<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->";

    /**
     * Parent of the generated pages in the docs-site nav. The convention is the parent page's
     * <em>filename without the {@code .md} suffix</em>, so the children of {@code FUNCTIONS.md}
     * declare {@code parent: "FUNCTIONS"}.
     */
    private static final String DOCS_PARENT = "FUNCTIONS";

    /** YAML frontmatter for the docs site, matching the shape of the hand-written reference pages. */
    private static String frontMatter(String menuTitle, String parent, int order) {
        return "---\n"
                + "menuTitle: \"" + menuTitle + "\"\n"
                + "parent: \"" + parent + "\"\n"
                + "order: " + order + "\n"
                + "---\n\n";
    }

    /** One markdown page per non-empty category, keyed by file name (e.g. {@code string.md}). */
    public Map<String, String> categoryPages(FunctionRegistry canonical) {
        Map<String, String> pages = new LinkedHashMap<>();
        for (FunctionCategory category : FunctionCategory.values()) {
            String page = categoryPage(category, canonical);
            if (page != null) {
                pages.put(category.name().toLowerCase() + ".md", page);
            }
        }
        return pages;
    }

    /**
     * Markdown page for one category, or {@code null} if the category has no functions.
     *
     * <p>Functions appear in <em>registration order</em> — {@link FunctionRegistry#all()} is
     * insertion-ordered, so the order of {@code register(...)} calls in the {@code catalog/*Functions}
     * source is the order on the page. Reorder the source to reorder the docs.
     */
    public String categoryPage(FunctionCategory category, FunctionRegistry canonical) {
        Map<String, List<FunctionDefinition>> byName = new LinkedHashMap<>();
        for (List<FunctionDefinition> set : canonical.all()) {
            if (set.get(0).getCategory() == category) {
                byName.put(set.get(0).getName(), set);
            }
        }
        if (byName.isEmpty()) {
            return null;
        }

        StringBuilder b = new StringBuilder();
        b.append(frontMatter(category.getTitle(), DOCS_PARENT, category.ordinal() + 1));
        b.append(GENERATED_NOTE).append("\n\n");
        b.append("# ").append(category.getTitle()).append("\n");
        for (Map.Entry<String, List<FunctionDefinition>> e : byName.entrySet()) {
            b.append("\n## ").append(e.getKey()).append("\n\n");
            for (FunctionDefinition d : e.getValue()) {
                b.append("`").append(typedCallForm(d))
                        .append("` → ").append(returnTypeLabel(d));
                if (d.getKind() == FunctionKind.AGGREGATE) {
                    b.append(" *(aggregate)*");
                }
                b.append("\n\n");
                if (d.getDescription() != null) {
                    b.append(d.getDescription()).append("\n\n");
                }
                if (d.getParagraph() != null) {
                    b.append(d.getParagraph()).append("\n\n");
                }
                String argTable = argTable(d);
                if (argTable != null) {
                    b.append(argTable);
                }
                b.append("Standard SQL: `").append(preview(d)).append("`\n\n");
                if (d.getExample() != null) {
                    b.append("Example: `").append(d.getExample()).append("`\n\n");
                }
            }
        }
        return b.toString();
    }

    /** Support-matrix page for one dialect: status and rendering of every catalog function. */
    public String dialectPage(String dialectName, String menuTitle, int order,
            FunctionRegistry canonical, FunctionRenderer dialect) {
        Map<String, List<FunctionDefinition>> names = new TreeMap<>();
        for (List<FunctionDefinition> set : canonical.all()) {
            names.put(set.get(0).getName(), set);
        }
        if (dialect instanceof FunctionRegistry registry) {
            for (List<FunctionDefinition> set : registry.all()) {
                names.putIfAbsent(set.get(0).getName(), set);
            }
        }

        StringBuilder b = new StringBuilder();
        b.append(frontMatter(menuTitle, DOCS_PARENT, order));
        b.append(GENERATED_NOTE).append("\n\n");
        b.append("# Function support: ").append(dialectName).append("\n\n");
        b.append("| Function | Category | Kind | Status | Rendering |\n");
        b.append("|---|---|---|---|---|\n");
        for (String name : names.keySet()) {
            List<FunctionDefinition> canonicalSet = canonical.overloads(name);
            List<FunctionDefinition> dialectSet = dialect.overloads(name);
            List<FunctionDefinition> effective = dialectSet.isEmpty() ? canonicalSet : dialectSet;

            FunctionDefinition rep = effective.get(0);
            b.append("| ").append(name)
                    .append(" | ").append(rep.getCategory().name().toLowerCase())
                    .append(" | ").append(rep.getKind().name().toLowerCase())
                    .append(" | ").append(status(canonicalSet, dialectSet))
                    .append(" | ").append(renderings(effective))
                    .append(" |\n");
        }
        return b.toString();
    }

    private static String status(List<FunctionDefinition> canonicalSet, List<FunctionDefinition> dialectSet) {
        if (!dialectSet.isEmpty() && dialectSet.get(0).isUnsupported()) {
            return "unsupported";
        }
        if (canonicalSet.isEmpty()) {
            return "native";
        }
        if (dialectSet.isEmpty()) {
            return "missing";
        }
        return renderings(canonicalSet).equals(renderings(dialectSet)) ? "standard" : "overridden";
    }

    private static String renderings(List<FunctionDefinition> set) {
        List<String> previews = new ArrayList<>();
        for (FunctionDefinition d : set) {
            previews.add("`" + preview(d) + "`");
        }
        return String.join("<br>", previews);
    }

    /** Symbolic rendering of one definition, e.g. {@code INSTR(str, substr)}. */
    public static String preview(FunctionDefinition d) {
        if (d.isUnsupported()) {
            return "—";
        }
        List<String> argNames = d.getSignature() != null
                ? d.getSignature().args().stream().map(FunctionArg::name).toList()
                : List.of("…");
        if (d.getTemplate() != null) {
            return d.getTemplate().preview(argNames);
        }
        if (d.getClass() != FunctionDefinition.class) {
            return d.getName() + "(…) — dialect-specific rendering";
        }
        return d.getName() + "(" + String.join(", ", argNames) + ")";
    }

    /**
     * The KQL surface form of a call: {@code name(args)} for functions, the
     * infix/affix form for operators (driven by {@link Fixity}), e.g.
     * {@code value BETWEEN low AND high}, {@code left = right}, {@code value ISNULL}.
     */
    public static String callForm(FunctionDefinition d) {
        List<String> a = d.getSignature() != null
                ? d.getSignature().args().stream().map(FunctionArg::name).toList()
                : List.of();
        String name = d.getName();
        return switch (d.getFixity()) {
            case PREFIX       -> name + (d.getSignature() != null ? d.getSignature().toString() : "(…)");
            case INFIX        -> argName(a, 0) + " " + name + " " + argName(a, 1);
            case RANGE        -> argName(a, 0) + " " + name + " " + argName(a, 1) + " AND " + argName(a, 2);
            case SET          -> argName(a, 0) + " " + name + " (" + argName(a, 1) + ", …)";
            case POSTFIX      -> argName(a, 0) + " " + name;
            case PREFIX_UNARY -> name + " " + argName(a, 0);
        };
    }

    /**
     * The PREFIX (function-call) surface form with declared argument types, e.g.
     * {@code substr(string: TEXT, start: INTEGER [, length: INTEGER])}. Operators keep their untyped
     * infix/affix form (their operands are {@code any}/{@code BOOLEAN}, where a type adds nothing).
     */
    public static String typedCallForm(FunctionDefinition d) {
        if (d.getFixity() == Fixity.PREFIX) {
            return d.getName() + (d.getSignature() != null ? typedArgs(d.getSignature()) : "(…)");
        }
        return callForm(d);
    }

    /** {@code (name: TYPE, name: TYPE [, opt: TYPE], ...)} — mirrors the signature's bracket/variadic style. */
    private static String typedArgs(FunctionSignature sig) {
        String s = sig.args().stream()
                .map(a -> {
                    String token = a.name() + ": " + familyLabel(a.family());
                    return a.optional() ? "[, " + token + "]" : token;
                })
                .collect(Collectors.joining(", "))
                .replace(", [, ", " [, ");
        if (sig.variadic()) {
            s = s + ", ...";
        }
        return "(" + s + ")";
    }

    /**
     * Display label for an operand type family. Leaf KQL types keep their type name (TEXT, INTEGER,
     * DATE, …); {@code INTERVAL} shows as its user-facing name DURATION; the umbrella groups read as
     * lower-case families so they are visually distinct from a single concrete type.
     */
    private static String familyLabel(TypeFamily family) {
        if (family == null) {
            return "any";
        }
        return switch (family.name()) {
            case "INTERVAL" -> "DURATION";
            case "NUMERIC"  -> "numeric";
            case "TEMPORAL" -> "date/time";
            case "ADDITIVE" -> "numeric | duration";
            case "ANY"      -> "any";
            default         -> family.name();
        };
    }

    /** Per-argument table, emitted only when at least one argument carries a description; else {@code null}. */
    private static String argTable(FunctionDefinition d) {
        FunctionSignature sig = d.getSignature();
        if (sig == null || sig.args().stream().noneMatch(a -> a.description() != null)) {
            return null;
        }
        StringBuilder b = new StringBuilder("| Argument | Type | Description |\n|---|---|---|\n");
        for (FunctionArg a : sig.args()) {
            String type = familyLabel(a.family()) + (a.optional() ? " *(optional)*" : "");
            String desc = a.description() != null ? a.description() : "";
            b.append("| ").append(a.name()).append(" | ").append(type).append(" | ").append(desc).append(" |\n");
        }
        return b.append("\n").toString();
    }

    private static String argName(List<String> names, int i) {
        return i < names.size() ? names.get(i) : "arg" + i;
    }

    /**
     * KQL-facing return type for the function category pages. The physical SQL name {@code INTERVAL}
     * is shown as its user-facing KQL name DURATION here; dialect support pages keep the physical
     * name, as they document the emitted SQL.
     */
    private static String returnTypeLabel(FunctionDefinition d) {
        try {
            TypeDescriptor t = d.returnType(null);
            if (t == null) {
                return "?";
            }
            String name = t.getPhysicalTypeName();
            return "INTERVAL".equals(name) ? "DURATION" : name;
        } catch (RuntimeException e) {
            return "argument-dependent";
        }
    }

    /**
     * Golden-file sync, matching the repo's transpile-test pattern: a missing
     * file is generated, an existing one must match or the build fails.
     *
     * <p>Write mode ({@code -Ddocs.write=true} or {@code DOCS_WRITE=true}) overwrites instead of
     * asserting — the regeneration loop after editing catalog doc metadata. The env var is honored
     * because Gradle forwards the environment to the test JVM but not {@code -D} system properties
     * unless the build is configured to.
     */
    public static void sync(Path file, String content) throws IOException {
        if (writeMode() || !Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return;
        }
        String existing = Files.readString(file);
        if (!existing.equals(content)) {
            throw new AssertionError("generated docs drifted from the catalog: " + file
                    + " — re-run with -Ddocs.write=true (or DOCS_WRITE=true) to regenerate");
        }
    }

    private static boolean writeMode() {
        return Boolean.getBoolean("docs.write") || "true".equalsIgnoreCase(System.getenv("DOCS_WRITE"));
    }
}
