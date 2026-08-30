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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Function catalog: maps a name to its overload set and renders calls.
 *
 * <p>Registration semantics: a new definition supersedes existing overloads it
 * collides with. Definitions without a signature claim the whole name (the
 * legacy replace-by-name behavior); definitions with signatures coexist as
 * overloads as long as their arity ranges don't overlap. All overloads of a
 * name must share the same {@link FunctionKind} — classification (GROUP BY /
 * HAVING inference) works on names before argument types are known.
 */
public class FunctionRegistry implements FunctionRenderer {

    private final Map<String, List<FunctionDefinition>> functions = new LinkedHashMap<>();

    public FunctionRegistry register(FunctionDefinition fn) {
        List<FunctionDefinition> set = functions.computeIfAbsent(fn.getName(), k -> new ArrayList<>());
        set.removeIf(existing -> collides(existing, fn));
        if (!set.isEmpty() && set.get(0).getKind() != fn.getKind()) {
            throw new KorykiaiException("all overloads of '" + fn.getName() + "' must share the same FunctionKind: "
                    + set.get(0).getKind() + " vs " + fn.getKind());
        }
        set.add(fn);
        return this;
    }

    private static boolean collides(FunctionDefinition a, FunctionDefinition b) {
        if (a.getSignature() == null || b.getSignature() == null) {
            return true;
        }
        return a.getSignature().overlaps(b.getSignature());
    }

    /**
     * Dialect overlay: replaces only the rendering of an existing definition.
     * Kind, signature, return type and documentation are inherited from the
     * base definition, so a dialect can never disagree with the catalog about
     * what a function <em>is</em> — only about how it renders.
     */
    public FunctionRegistry override(String name, String template) {
        List<FunctionDefinition> set = overloads(name);
        if (set.isEmpty()) {
            throw new KorykiaiException("cannot override unknown function: " + name);
        }
        if (set.size() > 1) {
            throw new KorykiaiException("ambiguous override for '" + name + "': specify the arity");
        }
        return register(copyForOverlay(set.get(0)).template(template));
    }

    /**
     * Dialect overlay that re-renders <em>every</em> overload of a name with the same template.
     * For uniformly-rendered overload sets (e.g. type-overloaded {@code to_text}, where each
     * source family renders the same CAST) a dialect changes the physical type once for all.
     */
    public FunctionRegistry overrideAll(String name, String template) {
        List<FunctionDefinition> set = overloads(name);
        if (set.isEmpty()) {
            throw new KorykiaiException("cannot override unknown function: " + name);
        }
        for (FunctionDefinition base : new ArrayList<>(set)) {
            register(copyForOverlay(base).template(template));
        }
        return this;
    }

    /** Arity-specific overlay for one overload of an overloaded function. */
    public FunctionRegistry override(String name, int arity, String template) {
        FunctionDefinition base = overloads(name).stream()
                .filter(d -> d.getSignature() != null && d.getSignature().matchesArity(arity))
                .findFirst()
                .orElseThrow(() -> new KorykiaiException(
                        "cannot override unknown overload: " + name + "/" + arity));
        return register(copyForOverlay(base).template(template));
    }

    /**
     * Dialect overlay: the function exists here, but not with this many arguments — the counterpart
     * of {@link #override(String, int, String)}, and it was missing. MariaDB's {@code trim} is the
     * case: it has no set-based form, so {@code trim(s)} works and {@code trim(s, chars)} does not.
     * The only way to say that was to leave the one-argument template in place and let the call die
     * in {@code SqlTemplate}'s surplus-argument guard — a {@code KorykiaiException} with no
     * position, no {@code UNSUPPORTED} category, and therefore a hand-written {@code ignore=} marker
     * on the fixture whose own comment had to explain it was not a result divergence.
     *
     * <p>Splits the base overload: the arities below {@code arity} keep rendering, {@code arity}
     * itself becomes a declared-unsupported overload. Deliberately limited to dropping <em>trailing
     * optional</em> arguments, which is what a dialect actually lacks; a hole in the middle would
     * need two surviving ranges and has no caller.
     */
    public FunctionRegistry unsupported(String name, int arity) {
        FunctionDefinition base = overloads(name).stream()
                .filter(d -> d.getSignature() != null && d.getSignature().matchesArity(arity))
                .findFirst()
                .orElseThrow(() -> new KorykiaiException(
                        "cannot mark unknown overload as unsupported: " + name + "/" + arity));
        FunctionSignature sig = base.getSignature();
        if (sig.variadic() || arity != sig.maxArgs() || arity <= sig.minArgs()) {
            throw new KorykiaiException("unsupported(" + name + ", " + arity
                    + ") can only drop a trailing optional argument; this overload takes "
                    + sig.minArgs() + ".." + sig.maxArgs() + (sig.variadic() ? "+" : ""));
        }
        FunctionArg[] kept = sig.args().subList(0, arity - 1).toArray(new FunctionArg[0]);
        // The dropped overload must be exactly this arity, so its trailing argument becomes
        // required — left optional it would still span minArgs..arity and swallow the kept range.
        FunctionArg[] dropped = sig.args().subList(0, arity).stream()
                .map(a -> new FunctionArg(a.name(), a.family(), false, a.description()))
                .toArray(FunctionArg[]::new);
        // Order matters: the narrowed definition overlaps the base and so replaces it, then the
        // dropped arity is registered as its own non-overlapping, unsupported overload.
        register(copyForOverlay(base).args(kept));
        return register(copyForOverlay(base).args(dropped).unsupported());
    }

    /**
     * Dialect overlay: the function exists, but not with a <em>variable</em> number of values.
     *
     * <p>A sibling of {@link #unsupported(String, int)} rather than a loosening of it: that one
     * refuses variadic signatures on purpose, because dropping one arity out of an open-ended range
     * would leave a hole it cannot express. Here the whole variadic overload goes, which is exactly
     * what a dialect lacks — {@code COUNT(DISTINCT a, b)} is measured to work on mariadb and
     * snowflake, {@code COUNT(DISTINCT (a, b))} on duckdb, postgresql and trino, and neither on
     * oracle, mssql or sqlite. The fixed-arity overloads keep rendering untouched.
     *
     * @param hint what the author can do instead; it reaches the violation message
     */
    public FunctionRegistry unsupportedVariadic(String name, String hint) {
        List<FunctionDefinition> variadic = overloads(name).stream()
                .filter(d -> d.getSignature() != null && d.getSignature().variadic())
                .toList();
        if (variadic.isEmpty()) {
            throw new KorykiaiException("no variadic overload to mark unsupported: " + name);
        }
        for (FunctionDefinition base : variadic) {
            register(copyForOverlay(base).unsupported().unsupportedHint(hint));
        }
        return this;
    }

    /** Dialect overlay: marks a catalog function as rejected by this dialect. */
    public FunctionRegistry unsupported(String name) {
        List<FunctionDefinition> set = overloads(name);
        if (set.isEmpty()) {
            throw new KorykiaiException("cannot mark unknown function as unsupported: " + name);
        }
        return register(copyForOverlay(set.get(0)).unsupported());
    }

    /**
     * Dialect overlay: the function renders normally but rejects an OVER clause
     * (e.g. MySQL GROUP_CONCAT cannot be used as a window function). Applied on
     * top of the current definition, so call it after any {@code override}.
     */
    public FunctionRegistry windowUnsupported(String name) {
        List<FunctionDefinition> set = overloads(name);
        if (set.isEmpty()) {
            throw new KorykiaiException("cannot mark unknown function as window-unsupported: " + name);
        }
        // Every overload, not just the first: whether an engine can put a function behind OVER is a
        // property of the function, not of how many arguments it was given. Marking set.get(0) left
        // the other overloads silently window-capable once string_agg gained a sorted form.
        for (FunctionDefinition base : new ArrayList<>(set)) {
            register(copyForOverlay(base).windowUnsupported());
        }
        return this;
    }

    private static FunctionDefinition copyForOverlay(FunctionDefinition base) {
        // Full-fidelity copy (incl. fixity + paragraph); callers then set the new template
        // or mark it unsupported. See FunctionDefinition's copy constructor.
        return new FunctionDefinition(base);
    }

    /** Representative definition for name-level questions (kind, existence). */
    public FunctionDefinition lookup(String name) {
        List<FunctionDefinition> set = functions.get(name);
        return set == null || set.isEmpty() ? null : set.get(0);
    }

    /** Overload resolution by arity; falls back to the representative if nothing matches. */
    public FunctionDefinition lookup(String name, int argCount) {
        List<FunctionDefinition> set = functions.get(name);
        if (set == null || set.isEmpty()) {
            return null;
        }
        if (set.size() == 1) {
            return set.get(0);
        }
        return set.stream()
                .filter(d -> d.getSignature() == null || d.getSignature().matchesArity(argCount))
                .findFirst()
                .orElse(set.get(0));
    }

    /**
     * Overload resolution by arity, disambiguated by argument family when several overloads
     * share the arity. {@code callFamilies} is a supplier so argument types are resolved only
     * when there is a genuine choice (a single-overload name never triggers type resolution).
     * Falls back to the arity match, then the representative, when nothing matches by family.
     */
    public FunctionDefinition lookup(String name, int argCount, Supplier<List<TypeFamily>> callFamilies) {
        List<FunctionDefinition> set = functions.get(name);
        if (set == null || set.isEmpty()) {
            return null;
        }
        if (set.size() == 1) {
            return set.get(0);
        }
        List<TypeFamily> families = callFamilies.get();
        return set.stream()
                .filter(d -> d.getSignature() != null && d.getSignature().matches(families))
                .findFirst()
                .or(() -> set.stream()
                        .filter(d -> d.getSignature() == null || d.getSignature().matchesArity(argCount))
                        .findFirst())
                .orElse(set.get(0));
    }

    private static List<TypeFamily> familiesOf(FunctionBinding binding) {
        List<TypeFamily> families = new ArrayList<>(binding.getOperandCount());
        for (int i = 0; i < binding.getOperandCount(); i++) {
            try {
                TypeDescriptor t = binding.getOperandType(i);
                families.add(t == null ? null : t.getTypeFamily());
            } catch (RuntimeException unresolved) {
                families.add(null);   // can't type this argument → wildcard, fall back to arity
            }
        }
        return families;
    }

    private static List<TypeFamily> familiesOf(SqlSelectRenderer renderer, Function function) {
        List<TypeFamily> families = new ArrayList<>();
        for (Expression a : function.getArguments()) {
            try {
                TypeDescriptor t = renderer.resolveType(a);
                families.add(t == null ? null : t.getTypeFamily());
            } catch (RuntimeException unresolved) {
                families.add(null);
            }
        }
        return families;
    }

    public List<FunctionDefinition> overloads(String name) {
        List<FunctionDefinition> set = functions.get(name);
        return set == null ? List.of() : Collections.unmodifiableList(set);
    }

    public Collection<List<FunctionDefinition>> all() {
        return Collections.unmodifiableCollection(functions.values());
    }

    /**
     * The names a query may write as {@code name(...)}, sorted and without repeats.
     *
     * <p>Overloads collapse to one name — a suggestion is about spelling, and offering {@code round}
     * three times says nothing three times. Operators are left out (see {@link
     * FunctionCatalog#names()}), and so is anything this dialect declares unsupported: naming a
     * function the engine has just refused would be a worse answer than none.
     */
    @Override
    public List<String> names() {
        return all().stream()
                .flatMap(List::stream)
                .filter(d -> !d.isUnsupported())
                .filter(d -> d.getFixity() == null || d.getFixity() == Fixity.PREFIX)
                .map(FunctionDefinition::getName)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public String predicate(SqlSelectRenderer renderer, Function function, int indent) {
        FunctionDefinition fn = lookup(function.getFunc(), function.getArguments().size(),
                () -> familiesOf(renderer, function));
        if (fn != null) {
            String sql = fn.renderPredicate(renderer, function, indent);
            if (sql != null) return sql;
        }
        return null;
    }

    @Override
    public TypeDescriptor descriptor(FunctionBinding binding) {
        FunctionDefinition fn = lookup(binding.getFunction().getFunc(), binding.getOperandCount(),
                () -> familiesOf(binding));
        return fn != null ? fn.returnType(binding) : null;
    }

    @Override
    public String function(SqlSelectRenderer renderer, Function function, int indent) {
        FunctionDefinition fn = lookup(function.getFunc(), function.getArguments().size(),
                () -> familiesOf(renderer, function));
        if (fn != null) {
            String rendered = fn.render(renderer, function, indent);
            if (rendered != null) return rendered;
        }
        return defaultRender(renderer, function, indent);
    }

    private String defaultRender(SqlSelectRenderer renderer, Function function, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(function.getFunc());
        b.append("(");
        b.append(function.getArguments().stream()
                .map(a -> renderer.toSql(a, indent))
                .collect(Collectors.joining(", ")));
        b.append(")");
        b.append(renderer.toSql(function.getWindow(), indent));
        return b.toString();
    }
}
