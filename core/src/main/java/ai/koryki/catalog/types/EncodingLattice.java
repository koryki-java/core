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
package ai.koryki.catalog.types;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lossless conversion lattice over the {@link TypeEncoding}s of a <em>single</em> {@link TypeFamily}.
 * Reconciling the branches of a conditional (CASE / COALESCE / IF) picks one common encoding that
 * every branch reaches without loss; this lattice answers which targets are reachable
 * ({@link #losslessTargets}), how expensive each is ({@link #cost}), and how to render the
 * conversion ({@link #convertSql}).
 *
 * <p>Step 1 fills in the epoch-integer TIMESTAMP encodings: a coarser unit converts to any finer
 * unit by an exact integer scale (EPOCH:SECONDS &rarr; EPOCH:MILLIS is {@code * 1000}); the reverse
 * is lossy and excluded. Every other encoding is a lattice singleton — only itself — so unlike
 * encodings in those families have no common target and reconciliation hard-errors until their
 * conversions are added here.
 */
public final class EncodingLattice {

    /** The epoch units koryki decodes (see {@code CoreDecoder}); all powers of ten apart. */
    private static final ChronoUnit[] EPOCH_UNITS =
            { ChronoUnit.SECONDS, ChronoUnit.MILLIS, ChronoUnit.MICROS, ChronoUnit.NANOS };

    private EncodingLattice() {}

    /** The encodings {@code enc} converts to without loss, including {@code enc} itself. */
    public static Set<TypeEncoding> losslessTargets(TypeEncoding enc) {
        Set<TypeEncoding> targets = new LinkedHashSet<>();
        targets.add(enc);
        if (enc instanceof EpochTypeEncoding epoch) {
            for (ChronoUnit u : EPOCH_UNITS) {
                if (isFinerOrEqual(epoch.getUnit(), u)) {
                    targets.add(new EpochTypeEncoding(u));
                }
            }
        }
        return targets;
    }

    /** Conversion cost {@code from -> to}; {@code to} must be a {@link #losslessTargets} of {@code from}. */
    public static int cost(TypeEncoding from, TypeEncoding to) {
        if (from.equals(to)) return 0;
        if (from instanceof EpochTypeEncoding && to instanceof EpochTypeEncoding) {
            return 1;   // a single scalar multiply
        }
        throw new IllegalArgumentException("no lossless conversion " + from + " -> " + to);
    }

    /** Dialect-neutral SQL converting {@code expr} from {@code from} to {@code to}; {@code expr} if equal. */
    public static String convertSql(String expr, TypeEncoding from, TypeEncoding to) {
        if (from.equals(to)) return expr;
        if (from instanceof EpochTypeEncoding ef && to instanceof EpochTypeEncoding et) {
            long factor = ratio(ef.getUnit(), et.getUnit());   // to is finer => factor >= 1
            return factor == 1 ? expr : "(" + expr + ") * " + factor;
        }
        throw new IllegalArgumentException("no lossless conversion " + from + " -> " + to);
    }

    /** {@code to} is the same or a finer unit than {@code from} (so from-&gt;to is an exact integer scale). */
    private static boolean isFinerOrEqual(ChronoUnit from, ChronoUnit to) {
        long f = from.getDuration().toNanos();
        long t = to.getDuration().toNanos();
        return t <= f && f % t == 0;
    }

    /** from.duration / to.duration (exact when {@code to} is finer-or-equal). */
    private static long ratio(ChronoUnit from, ChronoUnit to) {
        return from.getDuration().toNanos() / to.getDuration().toNanos();
    }
}
