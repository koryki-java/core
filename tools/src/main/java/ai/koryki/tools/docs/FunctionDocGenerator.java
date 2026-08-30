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

import ai.koryki.databases.cases.Fixtures;
import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.FunctionSignature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Per-dialect SQL for the documentation samples. Supplied by the golden test rather than
     * looked up here: the {@code tools} main source set depends only on {@code :core} and
     * {@code :northwind}, with the dialect modules on the test classpath.
     *
     * @param dialects          dialect names, in display order
     * @param sqlBySlug         sample slug → dialect → transpiled SQL; a dialect is absent when
     *                          it cannot render the sample
     * @param unsupportedByName function name → the dialects that mark it unsupported
     */
    public record DialectSql(List<String> dialects,
            Map<String, Map<String, String>> sqlBySlug,
            Map<String, Set<String>> unsupportedByName) {

        /** No dialect information: pages render without a {@code Generated SQL} section. */
        public static DialectSql none() {
            return new DialectSql(List.of(), Map.of(), Map.of());
        }
    }

    /** One markdown page per non-empty category, keyed by file name (e.g. {@code string.md}). */
    public Map<String, String> categoryPages(FunctionRegistry canonical) {
        return categoryPages(canonical, DialectSql.none());
    }

    /** As {@link #categoryPages(FunctionRegistry)}, adding each sample's per-dialect SQL. */
    public Map<String, String> categoryPages(FunctionRegistry canonical, DialectSql dialectSql) {
        Map<String, String> samples = loadSamples();
        Map<String, String> pages = new LinkedHashMap<>();
        for (FunctionCategory category : FunctionCategory.values()) {
            String page = categoryPage(category, canonical, samples, dialectSql);
            if (page != null) {
                pages.put(category.name().toLowerCase() + ".md", page);
            }
        }
        return pages;
    }

    /**
     * Optional, manually-authored introduction inserted right after a category page's {@code # Title}
     * and before the first function. Read from {@code resources/ai/koryki/tools/docs/intro/<category>.md}
     * (e.g. {@code intro/string.md}) — keyed by the lowercase {@link FunctionCategory} name, the same
     * as the generated page's filename. Returns {@code null} when no file is present, so a category
     * without an intro is unchanged. The generated page itself stays "do not edit"; the prose is
     * authored in its own resource file, not in the output.
     */
    private static String intro(FunctionCategory category) {
        String resource = "intro/" + category.name().toLowerCase() + ".md";
        try (InputStream in = FunctionDocGenerator.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sample-query roots in the shared fixture project (from {@code -Dtest.root}). A function's sample is drawn
     * from whichever schema demonstrates it best: business queries from {@code northwind}, type-cast
     * queries from the {@code typecheck} {@code check_type} table, and encoding-sensitive temporal queries
     * from the {@code temporal} {@code check_temporal} table. Northwind is listed first so it wins on the
     * (rare) slug that appears in more than one root.
     */
    private static final List<Path> SAMPLE_ROOTS = List.of(
            Fixtures.queries("northwind").resolve("docs"),
            Fixtures.queries("typecheck").resolve("docs"),
            Fixtures.queries("temporal").resolve("docs"));

    /**
     * Sample KQL queries keyed by {@link #slug function slug}, read once by walking each sample root
     * . Empty when the directories are absent — e.g. the generator is run outside
     * the module — so pages simply carry no samples.
     */
    static Map<String, String> loadSamples() {
        Map<String, String> samples = new HashMap<>();
        for (Path root : SAMPLE_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                walk.filter(p -> p.getFileName().toString().endsWith(".kql")).forEach(p -> {
                    String key = p.getFileName().toString().replaceFirst("\\.kql$", "");
                    try {
                        samples.putIfAbsent(key, Files.readString(p, StandardCharsets.UTF_8).strip());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return samples;
    }

    /**
     * Filesystem-safe key for a function's sample file: the lowercased name, with comparison operator
     * symbols mapped to mnemonics ({@code =} → {@code eq}, {@code >=} → {@code ge}, …), since a file
     * cannot be named {@code =.kql}. Keyword operators match directly ({@code AND} → {@code and}).
     */
    static String slug(String functionName) {
        switch (functionName) {
            case "=":  return "eq";
            case "<>": case "!=": return "ne";
            case "<":  return "lt";
            case "<=": return "le";
            case ">":  return "gt";
            case ">=": return "ge";
            default:   return functionName.toLowerCase();
        }
    }

    /**
     * Markdown page for one category, or {@code null} if the category has no functions.
     *
     * <p>Functions appear in <em>registration order</em> — {@link FunctionRegistry#all()} is
     * insertion-ordered, so the order of {@code register(...)} calls in the {@code catalog/*Functions}
     * source is the order on the page. Reorder the source to reorder the docs.
     */
    public String categoryPage(FunctionCategory category, FunctionRegistry canonical, Map<String, String> samples) {
        return categoryPage(category, canonical, samples, DialectSql.none());
    }

    /** As {@link #categoryPage(FunctionCategory, FunctionRegistry, Map)}, with per-dialect SQL. */
    public String categoryPage(FunctionCategory category, FunctionRegistry canonical,
            Map<String, String> samples, DialectSql dialectSql) {
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
        b.append(frontMatter(category.getTitle(), DOCS_PARENT, category.getOrder()));
        b.append(GENERATED_NOTE).append("\n\n");
        b.append("# ").append(category.getTitle()).append("\n");
        String intro = intro(category);
        if (intro != null) {
            b.append("\n").append(intro).append("\n");
        }
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
            }
            String slug = slug(e.getKey());
            String sample = samples.get(slug);
            if (sample != null) {
                b.append("Sample query:\n\n```kql\n").append(stripIgnoreMarkers(sample)).append("\n```\n\n");
            }
            appendGeneratedSql(b, e.getKey(), slug, sample, dialectSql);
        }
        return b.toString();
    }

    /**
     * The {@code ### Generated SQL} section: one full statement for the largest group of dialects
     * that render identically, then the divergences.
     *
     * <p>Every documentation sample is a single-table, one-function query, so a divergent dialect
     * normally differs in exactly one line — the expression — and is shown as a table row instead
     * of a near-duplicate statement. That is an optimisation, not an assumption: a group whose SQL
     * differs structurally falls back to a full block (see {@link #soleDifference}).
     */
    private static void appendGeneratedSql(StringBuilder b, String functionName, String slug,
            String sample, DialectSql dialectSql) {

        Map<String, String> byDialect = dialectSql.sqlBySlug().getOrDefault(slug, Map.of());
        Set<String> unsupported = dialectSql.unsupportedByName().getOrDefault(functionName, Set.of());
        if (byDialect.isEmpty() && unsupported.isEmpty()) {
            return;
        }

        // Group dialects by identical SQL, keeping the caller's dialect order inside each group.
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String dialect : dialectSql.dialects()) {
            String sql = byDialect.get(dialect);
            if (sql != null) {
                groups.computeIfAbsent(sql, k -> new ArrayList<>()).add(dialect);
            }
        }

        b.append("### Generated SQL\n\n");
        if (!groups.isEmpty()) {
            // Largest group first; tie-broken by dialect name so the golden output is stable.
            List<Map.Entry<String, List<String>>> ordered = new ArrayList<>(groups.entrySet());
            ordered.sort(Comparator
                    .comparingInt((Map.Entry<String, List<String>> g) -> -g.getValue().size())
                    .thenComparing(g -> g.getValue().get(0)));

            Map.Entry<String, List<String>> majority = ordered.get(0);
            b.append(label(majority.getValue(), dialectSql.dialects().size())).append("\n\n");
            b.append("```sql\n").append(majority.getKey()).append("\n```\n\n");

            List<String[]> rows = new ArrayList<>();
            List<Map.Entry<String, List<String>>> structural = new ArrayList<>();
            for (Map.Entry<String, List<String>> g : ordered.subList(1, ordered.size())) {
                String expression = soleDifference(majority.getKey(), g.getKey());
                if (expression == null) {
                    structural.add(g);
                } else {
                    rows.add(new String[] {String.join(" · ", g.getValue()), expression});
                }
            }
            if (!rows.isEmpty()) {
                b.append("The remaining dialects differ only in this expression:\n\n");
                b.append("| Dialect | Expression |\n|---|---|\n");
                for (String[] row : rows) {
                    b.append("| ").append(row[0])
                            .append(" | `").append(row[1].replace("|", "\\|")).append("` |\n");
                }
                b.append("\n");
            }
            for (Map.Entry<String, List<String>> g : structural) {
                b.append(label(g.getValue(), dialectSql.dialects().size())).append("\n\n");
                b.append("```sql\n").append(g.getKey()).append("\n```\n\n");
            }
        }

        if (!unsupported.isEmpty()) {
            b.append("Unsupported: **").append(String.join("**, **", new TreeSet<>(unsupported)))
                    .append("**\n\n");
        }
        // The same rule as in the dialect matrix further down, which already applies it: a
        // function a dialect does not offer at all produces no SQL -- a statement about its RESULT
        // there would contradict the status. Without the subtraction the page printed both facts
        // for the same thing, "Unsupported: sqlite" and "Results differ ... on sqlite".
        List<String> ignored = new ArrayList<>(divergentDialects(sample, dialectSql.dialects()));
        ignored.removeAll(unsupported);
        if (!ignored.isEmpty()) {
            b.append("> Results differ from the other dialects on **")
                    .append(String.join("**, **", ignored)).append("**.\n\n");
        }
    }

    private static String label(List<String> group, int total) {
        return group.size() == total && total > 1
                ? "**all dialects**"
                : "**" + String.join(" · ", group) + "**";
    }

    /**
     * The one line by which {@code variant} differs from {@code base}, or {@code null} when they
     * differ structurally — a different line count, or more than one differing line.
     */
    private static String soleDifference(String base, String variant) {
        String[] baseLines = base.split(Text.NL, -1);
        String[] variantLines = variant.split(Text.NL, -1);
        if (baseLines.length != variantLines.length) {
            return null;
        }
        String only = null;
        for (int i = 0; i < baseLines.length; i++) {
            if (!baseLines[i].equals(variantLines[i])) {
                if (only != null) {
                    return null;
                }
                only = variantLines[i].strip();
            }
        }
        return only;
    }

    /**
     * Dialects whose results for this sample genuinely differ from the rest.
     *
     * <p>Derived from the sample's {@code // ignore=<dialect>} markers: the CSV goldens are shared
     * across dialects, so a marker means that dialect's output does not match the others. A sample
     * marked for <em>every</em> dialect is different in kind — it is non-deterministic
     * ({@code now}, {@code random}, …), which distinguishes no dialect from any other and is
     * already evident from the function's own description — so it yields nothing here.
     */
    private static List<String> divergentDialects(String sample, List<String> dialects) {
        if (sample == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String dialect : dialects) {
            if (sample.contains("// ignore=" + dialect)) {
                out.add(dialect);
            }
        }
        return out.size() == dialects.size() ? List.of() : out;
    }

    /** Drops the {@code // ignore=<dialect>} test directives from a sample before publishing it. */
    private static String stripIgnoreMarkers(String sample) {
        return sample.lines()
                .filter(line -> !line.strip().startsWith("// ignore="))
                .collect(Collectors.joining(Text.NL));
    }

    /**
     * Support-matrix page for one dialect: status and rendering of every catalog function.
     *
     * @param allDialects every documented dialect — needed to tell a sample that diverges on
     *                    <em>this</em> dialect from one that is simply non-deterministic everywhere
     */
    public String dialectPage(String dialectName, String menuTitle, int order,
            FunctionRegistry canonical, FunctionRenderer dialect, List<String> allDialects) {
        Map<String, List<FunctionDefinition>> names = new TreeMap<>();
        for (List<FunctionDefinition> set : canonical.all()) {
            names.put(set.get(0).getName(), set);
        }
        if (dialect instanceof FunctionRegistry registry) {
            for (List<FunctionDefinition> set : registry.all()) {
                names.putIfAbsent(set.get(0).getName(), set);
            }
        }

        Map<String, String> samples = loadSamples();

        StringBuilder b = new StringBuilder();
        b.append(frontMatter(menuTitle, DOCS_PARENT, order));
        b.append(GENERATED_NOTE).append("\n\n");
        b.append("# Function support: ").append(dialectName).append("\n\n");
        b.append("What is specific to ").append(dialectName)
                .append(": the rendering column is filled in only where this dialect departs from the ")
                .append("standard one. The canonical form, and the SQL every dialect generates for a ")
                .append("sample query, are on the function pages.\n\n");
        b.append("| Function | Category | Kind | Status | Dialect rendering | Notes |\n");
        b.append("|---|---|---|---|---|---|\n");
        for (String name : names.keySet()) {
            List<FunctionDefinition> canonicalSet = canonical.overloads(name);
            List<FunctionDefinition> dialectSet = dialect.overloads(name);
            List<FunctionDefinition> effective = dialectSet.isEmpty() ? canonicalSet : dialectSet;

            // Classify from the canonical entry when there is one: a dialect overlay that does not
            // re-declare .category()/.kind() falls back to FunctionDoc.NONE, which would file e.g.
            // Oracle's overridden date_trunc under "other". Only genuinely native functions (absent
            // from the catalog) are classified from the dialect's own definition.
            FunctionDefinition rep = canonicalSet.isEmpty() ? effective.get(0) : canonicalSet.get(0);
            String status = status(canonicalSet, dialectSet);
            // "standard" means the dialect uses the canonical rendering verbatim — repeating it here
            // would echo the function page on every one of the eight matrices.
            String rendering = "standard".equals(status) ? "" : renderings(effective);
            // An unsupported function produces no SQL at all, so a note about its results would
            // contradict the status. divergentDialects() also drops the non-deterministic samples,
            // which are marked on every dialect and so say nothing about this one.
            boolean divergent = !"unsupported".equals(status)
                    && divergentDialects(samples.get(slug(name)), allDialects).contains(dialectName);
            // A dialect can declare that a function has no OVER form (T-SQL's STRING_AGG, MySQL's
            // GROUP_CONCAT, LISTAGG on Oracle and Snowflake, COUNT(DISTINCT ...) on five engines).
            // That was in the catalog but on no page: the row read "standard", and a reader had no
            // way to learn the function is barred from the one place they most likely wanted it.
            boolean noWindow = !dialectSet.isEmpty() && dialectSet.get(0).isWindowUnsupported();
            List<String> notes = new ArrayList<>();
            if (noWindow) {
                notes.add("no OVER clause");
            }
            if (divergent) {
                notes.add("results differ");
            }

            b.append("| ").append(name)
                    .append(" | ").append(rep.getCategory().name().toLowerCase())
                    .append(" | ").append(rep.getKind().name().toLowerCase())
                    .append(" | ").append(status)
                    .append(" | ").append(rendering)
                    .append(" | ").append(String.join("; ", notes))
                    .append(" |\n");
        }
        return b.toString();
    }

    private static String status(List<FunctionDefinition> canonicalSet, List<FunctionDefinition> dialectSet) {
        // allMatch, not get(0): "unsupported" is a statement about the whole function, and a dialect
        // can lack just one arity of it. Reading the first overload gave the right answer only
        // because the supported one happens to register first.
        if (!dialectSet.isEmpty() && dialectSet.stream().allMatch(FunctionDefinition::isUnsupported)) {
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
            // A dialect can lack one arity of a function rather than the whole of it (MariaDB has
            // trim(string) but no set-based trim(string, characters)). preview() answers "—" for
            // anything unsupported, which reads fine when the Status column already says so — but
            // beside a supported sibling it left the reader unable to tell WHICH form is missing.
            String p = d.isUnsupported() && set.size() > 1
                    ? d.getName() + "(" + String.join(", ", argNames(d)) + ") — unsupported"
                    : preview(d);
            // Escape the cell separator: SQL string concatenation (Oracle's `a || b`) would
            // otherwise split the row into extra columns.
            previews.add("`" + p.replace("|", "\\|") + "`");
        }
        return String.join("<br>", previews);
    }

    private static List<String> argNames(FunctionDefinition d) {
        return d.getSignature() != null
                ? d.getSignature().args().stream().map(FunctionArg::name).toList()
                : List.of("…");
    }

    /** Symbolic rendering of one definition, e.g. {@code INSTR(str, substr)}. */
    public static String preview(FunctionDefinition d) {
        if (d.isUnsupported()) {
            return "—";
        }
        List<String> argNames = argNames(d);
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
        // Shares FunctionSignature's renderer so the two forms cannot drift — and so the leading
        // optional argument is bracketed without a comma (count([value: any]), not count([, value: any])).
        String s = FunctionSignature.render(sig.args(), a -> a.name() + ": " + familyLabel(a.family()));
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
            case "INTERVAL"         -> "DURATION";
            case "NUMERIC"          -> "numeric";
            case "TEMPORAL"         -> "date/time";
            case "ADDITIVE"         -> "numeric | duration";
            case "NUMBER_OR_TEXT"   -> "numeric | text";
            case "TEMPORAL_OR_TEXT" -> "date/time | text";
            case "ANY"              -> "any";
            default                 -> family.name();
        };
    }

    /**
     * Per-argument input-type reference. A signature whose operands are all {@code any} (the comparison,
     * BETWEEN, IN and ISNULL operators) collapses to a compact line instead of a table of "any" rows.
     * Otherwise a {@code | Argument | Type |} table, gaining a Description column when any argument
     * carries one. {@code null} when the signature declares no arguments.
     */
    private static String argTable(FunctionDefinition d) {
        FunctionSignature sig = d.getSignature();
        if (sig == null || sig.args().isEmpty()) {
            return null;
        }
        boolean allAny = sig.args().stream()
                .allMatch(a -> a.family() == null || "ANY".equals(a.family().name()));
        if (allAny) {
            return "Operands: any type.\n\n";
        }
        boolean hasDescription = sig.args().stream().anyMatch(a -> a.description() != null);
        StringBuilder b = new StringBuilder(hasDescription
                ? "| Argument | Type | Description |\n|---|---|---|\n"
                : "| Argument | Type |\n|---|---|\n");
        for (FunctionArg a : sig.args()) {
            // escape '|' so multi-family labels (ADDITIVE → "numeric | duration") don't split the cell
            String type = familyLabel(a.family()).replace("|", "\\|") + (a.optional() ? " *(optional)*" : "");
            b.append("| ").append(a.name()).append(" | ").append(type);
            if (hasDescription) {
                b.append(" | ").append(a.description() != null ? a.description().replace("|", "\\|") : "");
            }
            b.append(" |\n");
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
