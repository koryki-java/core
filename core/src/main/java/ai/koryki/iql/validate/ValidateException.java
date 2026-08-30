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
package ai.koryki.iql.validate;

import ai.koryki.antlr.Text;
import ai.koryki.antlr.KorykiaiException;

import java.util.List;
import java.util.stream.Collectors;

public class ValidateException extends KorykiaiException {

    private List<Violation> violations;

    public ValidateException(List<Violation> violations) {
        this.violations = violations;
    }


    public List<Violation> getViolations() {
        return violations;
    }

    /**
     * Whether this failure is only "the dialect cannot express this query" — every error says the
     * dialect cannot express this construct. Test harnesses skip a shared fixture on that basis
     * rather than each fixture carrying a hand-written {@code ignore=<dialect>} marker.
     */
    public boolean isOnlyUnsupported() {
        List<Violation> errors = violations.stream().filter(Violation::isError).toList();
        return !errors.isEmpty() && errors.stream().allMatch(Violation::isUnsupported);
    }

    /**
     * Whether this failure is only "this dialect's schema has no such column" — the same purpose as
     * {@link #isOnlyUnsupported()}, for a difference in the schema rather than in the catalog. The
     * shared corpus runs against eight schemas that deliberately differ: an interval column exists
     * only where the engine has an interval type, and {@code check_temporal} carries whichever
     * encodings each engine can actually store.
     *
     * <p>What this deliberately cannot distinguish: a plain typo in a fixture also names a column
     * that is not there, and would be skipped rather than reported. Two things keep that visible —
     * the skip is recorded in a per-dialect violation golden, and a fixture skipped on every dialect
     * never produces the shared result golden its siblings have.
     */
    public boolean isOnlyUnknownColumn() {
        List<Violation> errors = violations.stream().filter(Violation::isError).toList();
        return !errors.isEmpty() && errors.stream().allMatch(Violation::isUnknownColumn);
    }

    /**
     * Whether every error is a limitation of this engine rather than a fault in the query — the
     * question a test harness actually asks before skipping a shared fixture.
     *
     * <p>Use this in preference to {@link #isOnlyUnsupported()} and {@link #isOnlyUnknownColumn()}
     * together: a fixture can hit both kinds at once (a duration this engine has no type for, read
     * from a column its schema does not declare), and each narrow predicate then answers no.
     */
    public boolean isOnlyDialectLimitation() {
        List<Violation> errors = violations.stream().filter(Violation::isError).toList();
        return !errors.isEmpty() && errors.stream().allMatch(Violation::isDialectLimitation);
    }

    @Override
    public String getMessage() {
        return violations.stream().map(v -> v.toString()).collect(Collectors.joining(Text.NL));
    }
}
