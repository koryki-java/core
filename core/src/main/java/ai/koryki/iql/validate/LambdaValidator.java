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
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.functions.LambdaSpec;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Lambda;
import ai.koryki.iql.query.Select;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.RuleContext;

/**
 * Where a lambda may stand and what may be in it. A lambda is only an argument, at a position its
 * function declares, with as many parameters as the function takes; its parameters are named once
 * and only exist inside it, so an {@code @x} outside, or one nobody declared, is wrong; and its
 * body is a plain computation on the parameter and on columns: no subselect, no aggregate, no
 * window. A parameter may not be named like an alias visible where the lambda stands: the two would
 * be read as one, since {@code t.price} is a field of the alias and {@code @t@price} a selection
 * from the parameter, and a name that means two things is a mistake waiting to be made.
 *
 * <p>Whether the body has the type the function needs, a yes/no test for a filter, and whether the
 * list is a list at all, are checked where types are: the family of the argument, and {@code
 * FunctionValidator} for the predicate.
 */
public class LambdaValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final FunctionCatalog functions;

    /** The parameters declared by the lambdas around the node being visited, outermost first. */
    private final Deque<Set<String>> scopes = new ArrayDeque<>();

    /** Lambda bodies being visited: what is forbidden inside them is checked while it is open. */
    private int insideLambda;

    /** The scopes of the selects and exists around the node being visited, innermost first. */
    private final Deque<IQLVisibilityContext> aliases = new ArrayDeque<>();

    private final IQLVisibilityContext rootVisibility;

    public LambdaValidator(Map<Object, RuleContext> iqlToContext, FunctionCatalog functions) {
        this(iqlToContext, functions, null);
    }

    /** Without a visibility there are no aliases to compare with: that check is skipped. */
    public LambdaValidator(
            Map<Object, RuleContext> iqlToContext,
            FunctionCatalog functions,
            IQLVisibilityContext visibility) {
        this.iqlToContext = iqlToContext;
        this.functions = functions;
        this.rootVisibility = visibility;
    }

    private void enter(Object scope) {
        if (rootVisibility != null) {
            aliases.push((aliases.isEmpty() ? rootVisibility : aliases.peek()).child(scope));
        }
    }

    private void leaveScope() {
        if (rootVisibility != null && !aliases.isEmpty()) {
            aliases.pop();
        }
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        enter(select);
        return true;
    }

    @Override
    public void leave(Select select) {
        leaveScope();
    }

    @Override
    public boolean visit(Deque<Object> deque, Exists exists) {
        enter(exists);
        return true;
    }

    @Override
    public void leave(Exists exists) {
        leaveScope();
    }

    @Override
    public boolean visit(Deque<Object> deque, Function function) {
        if (functions != null) {
            checkArguments(function);
        }
        if (insideLambda > 0) {
            if (function.getWindow() != null) {
                violation(function, "a window function is not allowed inside a lambda");
            } else if (StandardFunctions.isAggregate(function.getFunc())) {
                violation(
                        function,
                        "the aggregate "
                                + function.getFunc()
                                + " is not allowed inside a lambda; it works on the rows, a lambda on one element");
            }
        }
        return true;
    }

    private void checkArguments(Function function) {
        List<Expression> arguments = function.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            Expression argument = arguments.get(i);
            LambdaSpec spec = functions.lambda(function.getFunc(), arguments.size(), i);
            if (argument.getLambda() != null) {
                Lambda lambda = argument.getLambda();
                if (spec == null) {
                    if (!functions.overloads(function.getFunc()).isEmpty()) {
                        violation(
                                argument,
                                function.getFunc() + " takes no lambda as argument " + (i + 1));
                    }
                    continue;
                }
                int n = lambda.getParameters().size();
                if (n < spec.minParameters() || n > spec.maxParameters()) {
                    violation(
                            argument,
                            function.getFunc()
                                    + " passes "
                                    + (spec.minParameters() == spec.maxParameters()
                                            ? spec.minParameters()
                                            : spec.minParameters() + " or " + spec.maxParameters())
                                    + " parameters to its lambda, this one declares "
                                    + n);
                }
            }
        }
    }

    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        if (expression.getLambda() != null) {
            Lambda lambda = expression.getLambda();
            Set<String> declared = new HashSet<>();
            for (String name : lambda.getParameters()) {
                if (!declared.add(name)) {
                    violation(expression, "the lambda declares the parameter " + name + " twice");
                } else if (!aliases.isEmpty() && aliases.peek().aliases().contains(name)) {
                    violation(
                            expression,
                            "the parameter "
                                    + name
                                    + " has the name of the alias "
                                    + name
                                    + "; give it another name, so that "
                                    + name
                                    + ".x and @"
                                    + name
                                    + "@x cannot be taken for each other");
                } else if (scopes.stream().anyMatch(s -> s.contains(name))) {
                    violation(
                            expression,
                            "the parameter "
                                    + name
                                    + " hides the one of the lambda around it; give it another name");
                }
            }
            scopes.push(declared);
            insideLambda++;
        } else if (expression.getParameter() != null
                && scopes.stream().noneMatch(s -> s.contains(expression.getParameter()))) {
            violation(
                    expression,
                    "@" + expression.getParameter() + " is not a parameter of a lambda around it");
        } else if (insideLambda > 0 && expression.getSelect() != null) {
            violation(expression, "a subselect is not allowed inside a lambda");
        }
        return true;
    }

    @Override
    public void leave(Expression expression) {
        if (expression.getLambda() != null) {
            scopes.pop();
            insideLambda--;
        }
    }

    private void violation(Object node, String message) {
        violations.add(new Violation("lambda", node, Range.of(iqlToContext, node), message));
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
