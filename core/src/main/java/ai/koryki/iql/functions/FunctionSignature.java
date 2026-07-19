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

import ai.koryki.catalog.types.TypeFamily;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Declared argument list of one function overload. Optional arguments must be
 * trailing; a variadic signature repeats its last argument.
 */
public record FunctionSignature(List<FunctionArg> args, boolean variadic) {

    public FunctionSignature {
        args = List.copyOf(args);
        boolean optionalSeen = false;
        for (FunctionArg a : args) {
            if (a.optional()) {
                optionalSeen = true;
            } else if (optionalSeen) {
                throw new IllegalArgumentException("optional arguments must be trailing: " + args);
            }
        }
        if (variadic && (args.isEmpty() || args.get(args.size() - 1).optional())) {
            throw new IllegalArgumentException("variadic signature requires a mandatory last argument: " + args);
        }
    }

    public static FunctionSignature of(FunctionArg... args) {
        return new FunctionSignature(List.of(args), false);
    }

    public static FunctionSignature ofVariadic(FunctionArg... args) {
        return new FunctionSignature(List.of(args), true);
    }

    public int minArgs() {
        return (int) args.stream().filter(a -> !a.optional()).count();
    }

    public int maxArgs() {
        return variadic ? Integer.MAX_VALUE : args.size();
    }

    public boolean matchesArity(int argCount) {
        return argCount >= minArgs() && argCount <= maxArgs();
    }

    /** Whether some call arity is accepted by both signatures. */
    public boolean overlapsArity(FunctionSignature other) {
        return minArgs() <= other.maxArgs() && other.minArgs() <= maxArgs();
    }

    /** Declared family expected at call-position {@code i} (a variadic repeats its last arg); null = any. */
    public TypeFamily familyAt(int i) {
        if (args.isEmpty()) {
            return null;
        }
        return args.get(Math.min(i, args.size() - 1)).family();
    }

    /**
     * Whether a call with these argument families is accepted: arity plus a per-position
     * family check, where a {@code null} on either side is a wildcard that matches anything.
     */
    public boolean matches(List<TypeFamily> callFamilies) {
        if (!matchesArity(callFamilies.size())) {
            return false;
        }
        for (int i = 0; i < callFamilies.size(); i++) {
            TypeFamily declared = familyAt(i);
            TypeFamily actual = callFamilies.get(i);
            if (declared != null && actual != null && !declared.accepts(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether some call could satisfy both overloads — i.e. they are ambiguous and cannot
     * coexist. Arities must overlap and no shared argument position may distinguish them; a
     * position with two different non-null families lets a call pick one unambiguously, so
     * such overloads coexist (this is the type-aware generalization of {@link #overlapsArity}).
     */
    public boolean overlaps(FunctionSignature other) {
        if (!overlapsArity(other)) {
            return false;
        }
        int positions = Math.min(args.size(), other.args.size());
        for (int i = 0; i < positions; i++) {
            TypeFamily a = familyAt(i);
            TypeFamily b = other.familyAt(i);
            // The position separates the overloads only if the declared families are
            // disjoint — neither accepts the other (so no call can satisfy both here).
            if (a != null && b != null && !a.accepts(b) && !b.accepts(a)) {
                return false;
            }
        }
        return true;
    }

    /** Human-readable form for error messages and docs, e.g. {@code (string, start [, length])}. */
    @Override
    public String toString() {
        String s = args.stream()
                .map(a -> a.optional() ? "[, " + a.name() + "]" : a.name())
                .collect(Collectors.joining(", "))
                .replace(", [, ", " [, ");
        if (variadic) {
            s = s + ", ...";
        }
        return "(" + s + ")";
    }
}
