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

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.EncodingLattice;
import ai.koryki.catalog.schema.types.Families;
import ai.koryki.catalog.schema.types.NativeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeEncoding;
import ai.koryki.catalog.schema.types.TypeFamily;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reconciles the branch result-types of a conditional function ({@code case} / {@code coalesce}) to a
 * single output type. The branches must share a family-group: the numeric families widen
 * (INTEGER &lt; DECIMAL &lt; FLOAT, see {@link Families#NUMERIC}); DATE widens to TIMESTAMP; any other
 * family reconciles only with itself. Within the reconciled family the encodings are brought to one
 * common encoding via the lossless {@link EncodingLattice} (least total {@link EncodingLattice#cost}).
 * NULL-literal branches (no family) are ignored — they coerce to anything.
 *
 * <p>Policy is lossless-only: a reconciliation that cannot be done without loss is a hard error
 * ({@link ReconcileException}), surfaced positionally by {@code FunctionValidator}.
 */
public final class ConditionalReconciler {

    private ConditionalReconciler() {}

    /** A per-branch conversion from the branch's own encoding to the reconciled target encoding. */
    public record Conversion(TypeEncoding from, TypeEncoding to) {
        public boolean isIdentity() {
            return from == null || Objects.equals(from, to);
        }
    }

    /** The reconciled output type and the per-branch conversions, aligned 1:1 to the input list. */
    public record Result(TypeDescriptor target, List<Conversion> perBranch) {
        /** Wrap branch {@code i}'s rendered SQL in its reconciliation conversion (identity → unchanged). */
        public String convert(int i, String sql) {
            Conversion c = perBranch.get(i);
            return c.isIdentity() ? sql : EncodingLattice.convertSql(sql, c.from(), c.to());
        }
    }

    /** No common family-group, or no lossless common encoding within the group. */
    public static final class ReconcileException extends RuntimeException {
        public ReconcileException(String message) {
            super(message);
        }
    }

    public static Result reconcile(List<TypeDescriptor> branches) {
        // Branches that actually carry a type; the typeless NULL literal coerces to anything.
        List<TypeDescriptor> typed = new ArrayList<>();
        for (TypeDescriptor b : branches) {
            if (b != null && b.getTypeFamily() != null) {
                typed.add(b);
            }
        }
        if (typed.isEmpty()) {
            return new Result(TypeDescriptor.NULL, passthrough(branches.size()));
        }

        // 1. Family-group: widen within the group, hard error across groups.
        TypeFamily targetFamily = typed.get(0).getTypeFamily();
        for (TypeDescriptor b : typed) {
            if (!sameGroup(targetFamily, b.getTypeFamily())) {
                throw new ReconcileException("cannot reconcile conditional branches of different "
                        + "family-groups: " + targetFamily.name() + " and " + b.getTypeFamily().name());
            }
            targetFamily = widen(targetFamily, b.getTypeFamily());
        }

        // 2. Each branch's encoding, expressed in the target family (a widened numeric branch's
        //    representation becomes the target family's NATIVE; the DB coerces it implicitly).
        List<TypeEncoding> encodings = new ArrayList<>();
        for (TypeDescriptor b : typed) {
            encodings.add(encodingInFamily(b, targetFamily));
        }

        // 3. Target encoding: the lossless common encoding of least total cost (hard error if none).
        TypeEncoding targetEncoding = chooseTarget(encodings);

        // 4. Output descriptor + per-branch conversions, aligned to the original branch list.
        TypeDescriptor target = describe(typed, targetFamily, targetEncoding);
        List<Conversion> conversions = new ArrayList<>(branches.size());
        for (TypeDescriptor b : branches) {
            if (b == null || b.getTypeFamily() == null) {
                conversions.add(new Conversion(null, null));   // NULL literal — no cast needed
            } else {
                conversions.add(new Conversion(encodingInFamily(b, targetFamily), targetEncoding));
            }
        }
        return new Result(target, conversions);
    }

    private static TypeEncoding encodingInFamily(TypeDescriptor b, TypeFamily targetFamily) {
        return b.getTypeFamily().equals(targetFamily)
                ? b.getTypeEncoding()
                : NativeEncoding.of(targetFamily);   // numeric widening — implicit in CASE/COALESCE
    }

    /** The lossless common target reachable from every branch, minimizing summed conversion cost. */
    private static TypeEncoding chooseTarget(List<TypeEncoding> encodings) {
        Set<TypeEncoding> candidates = new LinkedHashSet<>(EncodingLattice.losslessTargets(encodings.get(0)));
        for (int i = 1; i < encodings.size(); i++) {
            candidates.retainAll(EncodingLattice.losslessTargets(encodings.get(i)));
        }
        if (candidates.isEmpty()) {
            throw new ReconcileException("no lossless common encoding for conditional branches " + encodings);
        }
        TypeEncoding best = null;
        int bestCost = Integer.MAX_VALUE;
        for (TypeEncoding candidate : candidates) {
            int total = 0;
            for (TypeEncoding e : encodings) {
                total += EncodingLattice.cost(e, candidate);
            }
            if (total < bestCost) {
                bestCost = total;
                best = candidate;
            }
        }
        return best;
    }

    /** Output descriptor — reuse a branch's physical type/precision/scale where it already matches. */
    private static TypeDescriptor describe(List<TypeDescriptor> typed, TypeFamily family, TypeEncoding encoding) {
        for (TypeDescriptor b : typed) {
            if (b.getTypeFamily().equals(family) && Objects.equals(b.getTypeEncoding(), encoding)) {
                return new TypeDescriptor(b.getPhysicalTypeName(), encoding, family, b.getPrecision(), b.getScale());
            }
        }
        // Widened family / converted encoding: take the physical/precision of the dominant branch.
        TypeDescriptor dominant = typed.get(0);
        for (TypeDescriptor b : typed) {
            if (rank(b.getTypeFamily()) > rank(dominant.getTypeFamily())) {
                dominant = b;
            }
        }
        return new TypeDescriptor(dominant.getPhysicalTypeName(), encoding, family,
                dominant.getPrecision(), dominant.getScale());
    }

    // --- family-group widening (step 1: numeric only; every other family reconciles with itself) ---

    private static boolean sameGroup(TypeFamily a, TypeFamily b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        if (Families.NUMERIC.accepts(a) && Families.NUMERIC.accepts(b)) {
            return true;
        }
        return temporalWiden(a) && temporalWiden(b);
    }

    /** Temporal families that widen among themselves: DATE -> TIMESTAMP. TIME is excluded — it has no lossless promotion. */
    private static boolean temporalWiden(TypeFamily f) {
        return CoreTypeFamily.DATE.equals(f) || CoreTypeFamily.TIMESTAMP.equals(f);
    }

    private static TypeFamily widen(TypeFamily a, TypeFamily b) {
        return rank(b) > rank(a) ? b : a;
    }

    /**
     * Widening rank within a group (higher wins); only ever compared between families of the same
     * group, so numeric and temporal scales may reuse values. Singletons are rank 0.
     */
    private static int rank(TypeFamily f) {
        if (CoreTypeFamily.INTEGER.equals(f))   return 1;
        if (CoreTypeFamily.DECIMAL.equals(f))   return 2;
        if (CoreTypeFamily.FLOAT.equals(f))     return 3;
        if (CoreTypeFamily.DATE.equals(f))      return 1;
        if (CoreTypeFamily.TIMESTAMP.equals(f)) return 2;
        return 0;
    }

    private static List<Conversion> passthrough(int n) {
        List<Conversion> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Conversion(null, null));
        }
        return list;
    }
}
