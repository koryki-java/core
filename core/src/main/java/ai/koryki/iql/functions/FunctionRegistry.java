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

    /** Dialect overlay: marks a catalog function as rejected by this dialect. */
    public FunctionRegistry unsupported(String name) {
        List<FunctionDefinition> set = overloads(name);
        if (set.isEmpty()) {
            throw new KorykiaiException("cannot mark unknown function as unsupported: " + name);
        }
        return register(copyForOverlay(set.get(0)).unsupported());
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
        b.append(toSql(renderer, function.getWindow(), indent));
        return b.toString();
    }
}
