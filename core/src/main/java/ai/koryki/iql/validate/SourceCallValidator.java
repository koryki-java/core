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
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;

/**
 * A source that is a function call must be {@code unnest} of a list, written after the source it
 * comes from.
 *
 * <p>The grammar takes any function call as a source, {@code o unnest(o.items) i}, because the one
 * form that is meant, a list made into rows, is a call and a keyword would be a reserved word. What
 * the grammar cannot say is said here: which call, with which argument, and where.
 *
 * <ul>
 *   <li>the function is {@code unnest}, with one argument and no window;
 *   <li>the argument is an expression, not a lambda, a condition or the bare name of an alias;
 *   <li>the source is joined to the source it comes from: it does not start the query or an {@code
 *       EXISTS}, and it takes no {@code VIA} and no columns, since what joins it is what the call
 *       is given.
 * </ul>
 *
 * <p>Run before the rules, as the placeholders are: a source that is not a table of the catalog has
 * no name for them to work with, and the refusal here points at the call, not at a symptom.
 */
public class SourceCallValidator implements Visitor, Collector<List<Violation>> {

    /** Category of these violations: the query is wrong, but not in its names or its functions. */
    public static final String SOURCE = "source";

    /** The one function that makes a list into rows. */
    public static final String UNNEST = "unnest";

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;

    public SourceCallValidator(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, Join join) {
        Source source = join.getSource();
        if (source != null
                && source.getCall() == null
                && join.getCrit() == null
                && join.getColumns() == null) {
            report(
                    source,
                    "a join to '" + source.getName() + "' names no criterion and no columns");
        }
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, Source source) {
        Function call = source.getCall();
        if (call == null) {
            return true;
        }
        Object parent = deque.peek();

        if (!UNNEST.equals(call.getFunc())) {
            report(
                    source,
                    "'"
                            + call.getFunc()
                            + "' cannot be a source: only unnest(list) makes a list into rows");
            return true;
        }
        if (call.getWindow() != null) {
            report(source, "unnest takes no window");
        }
        if (call.getArguments().size() != 1) {
            report(
                    source,
                    "unnest takes one list, as in unnest(o.items); it was given "
                            + call.getArguments().size());
        } else {
            Expression argument = call.getArguments().get(0);
            if (argument.getLambda() != null) {
                report(source, "unnest takes a list, not a lambda");
            } else if (argument.getLogical() != null) {
                report(source, "unnest takes a list, not a condition");
            } else if (argument.getIdentity() != null) {
                report(
                        source,
                        "unnest takes a list, not the alias '"
                                + argument.getIdentity()
                                + "': name the column that holds it, as in unnest("
                                + argument.getIdentity()
                                + ".items)");
            }
        }

        if (parent instanceof Select) {
            report(
                    source,
                    "unnest makes the list of a source before it into rows: write it after that"
                            + " source, as in FIND orders o, o unnest(o.items) i");
        } else if (parent instanceof Exists) {
            report(source, "unnest cannot start an EXISTS");
        } else if (parent instanceof Join join
                && (join.getCrit() != null || join.getColumns() != null)) {
            report(
                    source,
                    "a list made into rows is joined to the source it comes from: it takes no VIA"
                            + " and no columns");
        }
        return true;
    }

    private void report(Source source, String message) {
        violations.add(new Violation(SOURCE, source, Range.of(iqlToContext, source), message));
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
