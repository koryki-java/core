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

import ai.koryki.antlr.Range;

import java.util.List;

public class Violation {

    /**
     * Category of the violation raised when a dialect declares a function unsupported. Distinct from
     * the general {@code "function"} category because it means "this engine cannot express the
     * query", not "the query is wrong" — the query may be perfectly valid elsewhere.
     */
    public static final String UNSUPPORTED = "unsupported";

    /**
     * Category of the violation raised when a field names a column the schema does not have.
     *
     * <p>Distinct from the general {@code "schema"} category, which means the query is wrong. This
     * one need not: a shared corpus runs against eight schemas that deliberately differ, and an
     * interval column exists only where the engine has an interval type at all. A test harness can
     * therefore skip the fixture on the dialects whose schema lacks the column, the same way it
     * skips an unsupported function — see {@link ValidateException#isOnlyUnknownColumn()}.
     *
     * <p>Its own category rather than a pattern match on the message: the message is prose meant for
     * the query's author and will be reworded, the category is the claim.
     */
    public static final String UNKNOWN_COLUMN = "schema.unknown-column";

    /**
     * Whether a violation makes the query invalid.
     *
     * <p>{@link #ERROR} is the default and the only kind that aborts transpilation. A
     * {@link #WARNING} is advisory: the query still transpiles and runs, but something about it is
     * worth telling the author — today, a function name the catalog does not know, which is passed
     * through to SQL verbatim and may not exist on another dialect.
     */
    public enum Severity {
        ERROR, WARNING
    }

    private final String category;
    private final Severity severity;
    private String message;
    private Object iql;
    private Range range;
    private Range related;

    /**
     * The names the author may have meant, when this violation is about a name that is not known.
     *
     * <p>Carried as data as well as prose. A consumer that renders a diagnostic — an editor marker,
     * the JSON handed to a language model — wants the list, not a sentence it has to take apart
     * again; and taking it apart again is exactly what the alternative looked like, a regular
     * expression over {@link #getMessage()} living in another repository, which every rewording of
     * that message broke silently. See the note on {@link #UNKNOWN_COLUMN}: the message is prose and
     * will be reworded, so nothing may depend on its shape.
     */
    private List<String> didYouMean = List.of();

    public Violation(String category, Object iql, Range range, String message) {
        this(category, iql, range, message, null);
    }


    public Violation(String category, Object iql, Range range, String message, Range related) {
        this(category, Severity.ERROR, iql, range, message, related);
    }

    /** An advisory violation: reported, but the query still transpiles. */
    public static Violation warning(String category, Object iql, Range range, String message) {
        return new Violation(category, Severity.WARNING, iql, range, message, null);
    }

    public Violation(String category, Severity severity, Object iql, Range range, String message, Range related) {
        this.category = category;
        this.severity = severity;
        this.iql = iql;
        this.range = range;
        this.related = related;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    /** True when this violation makes the query invalid — the only kind that aborts transpilation. */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public Object getIql() {
        return iql;
    }

    public void setIql(Object iql) {
        this.iql = iql;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public Range getRelated() {
        return related;
    }

    public void setRelated(Range related) {
        this.related = related;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return (severity == Severity.ERROR ? "" : severity.name().toLowerCase() + " ")
                + category + ": " + iql.getClass().getSimpleName() + " [" + range + "]: " + getMessage()
                + (related != null ? " related [" + related + "]" : "");
    }

    /**
     * Whether this is the "the dialect cannot express this" violation — an unsupported function,
     * or a language construct a dialect validator rejected (see {@code SqlDialect#validators}).
     */
    public boolean isUnsupported() {
        return UNSUPPORTED.equals(category) && isError();
    }

    /** Whether this is the "this schema has no such column" violation. */
    public boolean isUnknownColumn() {
        return UNKNOWN_COLUMN.equals(category) && isError();
    }

    /**
     * Whether this error says "this engine cannot run the query" rather than "the query is wrong" —
     * an unsupported function or construct, or a column this dialect's schema does not declare.
     *
     * <p>The two are one question for a caller deciding whether to skip a shared fixture, and asking
     * them separately is a trap: a fixture can hit both at once, and then neither "are they all
     * unsupported?" nor "are they all unknown columns?" is true, so it fails instead of skipping.
     */
    public boolean isDialectLimitation() {
        return isUnsupported() || isUnknownColumn();
    }

    public String getCategory() {
        return category;
    }

    /** The names the author may have meant; empty when this violation is not about a name. */
    public List<String> getDidYouMean() {
        return didYouMean;
    }

    /**
     * Records the suggestions and folds them into the message in one move.
     *
     * <p>One call rather than a setter and a separate append, so the list and the sentence cannot
     * drift apart — a violation that carried suggestions no reader could see, or a message
     * promising names the list did not hold, would both be worse than having neither.
     *
     * <p>Returns {@code this} so a construction site stays a single expression.
     */
    public Violation suggesting(List<String> names) {
        if (names == null || names.isEmpty()) {
            return this;
        }
        this.didYouMean = List.copyOf(names);
        this.message = message + Suggest.hint(this.didYouMean);
        return this;
    }
}
