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
package ai.koryki.iql.typing;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.catalog.types.TypeFamilyRegistry;
import ai.koryki.catalog.types.TypeNames;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionBinding;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.query.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class ExpressionTypeResolver {

    private final LinkResolver linkResolver;
    private final IQLVisibilityContext visibility;
    private final FunctionCatalog catalog;

    // Memo by node identity, scoped to this resolver's visibility context. Per-instance
    // (never shared) so the same Expression can resolve differently in a child scope.
    private final java.util.Map<Expression, TypeDescriptor> cache =
            new java.util.IdentityHashMap<>();

    /** The lambda parameters in scope and what they are, innermost last; empty outside a lambda. */
    private final java.util.Map<String, TypeDescriptor> parameters;

    public ExpressionTypeResolver(
            LinkResolver linkResolver, IQLVisibilityContext visibility, FunctionCatalog catalog) {
        this(linkResolver, visibility, catalog, java.util.Map.of());
    }

    private ExpressionTypeResolver(
            LinkResolver linkResolver,
            IQLVisibilityContext visibility,
            FunctionCatalog catalog,
            java.util.Map<String, TypeDescriptor> parameters) {
        this.linkResolver = linkResolver;
        this.visibility = visibility;
        this.catalog = catalog;
        this.parameters = parameters;
    }

    /**
     * The same resolver inside a lambda: its parameters are known, with the types the function gave
     * them. A parameter of the same name in an outer lambda is hidden by the inner one.
     */
    public ExpressionTypeResolver withParameters(java.util.Map<String, TypeDescriptor> added) {
        if (added.isEmpty()) {
            return this;
        }
        java.util.Map<String, TypeDescriptor> merged = new java.util.LinkedHashMap<>(parameters);
        merged.putAll(added);
        return new ExpressionTypeResolver(linkResolver, visibility, catalog, merged);
    }

    public TypeDescriptor resolve(Expression expression) {
        if (cache.containsKey(expression)) {
            return cache.get(expression);
        }
        TypeDescriptor type = resolveUncached(expression);
        cache.put(expression, type);
        return type;
    }

    private TypeDescriptor resolveUncached(Expression expression) {
        if (expression.getSelect() != null) {
            return resolveSelect(expression.getSelect());
        } else if (expression.getField() != null) {
            return resolveField(expression.getField());
        } else if (expression.getFunction() != null) {
            return resolveFunction(expression.getFunction());
        } else if (expression.getText() != null) {
            return TypeDescriptor.TEXT;
        } else if (expression.getLogical() != null) {
            return TypeDescriptor.BOOLEAN;
        } else if (expression.getSelection() != null) {
            TypeDescriptor base = resolve(expression.getSelection().getBase());
            // an unresolved base (a function the catalog does not know) stays unresolved; whoever
            // reports that has the position
            return base == null
                    ? null
                    : Selectors.apply(base, expression.getSelection().getSelectors());
        } else if (expression.getLambda() != null) {
            // a lambda has no type of its own; the function it is passed to types its body
            return null;
        } else if (expression.getParameter() != null) {
            TypeDescriptor type = parameters.get(expression.getParameter());
            if (type == null) {
                throw new KorykiaiException(
                        "lambda parameter @" + expression.getParameter() + " is not in scope");
            }
            return type;
        } else if (expression.getIdentity() != null) {
            throw new KorykiaiException("identity is not allowed here");
        } else if (expression.getNumber() != null) {
            if (expression.getNumber() instanceof BigInteger) {
                return TypeDescriptor.INTEGER;
            } else if (expression.getNumber() instanceof BigDecimal bd) {
                // carry the literal's own precision/scale (e.g. 12.34 -> DECIMAL(4,2))
                return new TypeDescriptor(
                        TypeNames.TYPE_DECIMAL,
                        null,
                        CoreTypeFamily.DECIMAL,
                        bd.precision(),
                        bd.scale());
            } else {
                throw new KorykiaiException(
                        "unknown number type: " + expression.getNumber().getClass());
            }
        } else if (expression.getLocalDate() != null) {
            return TypeDescriptor.DATE;
        } else if (expression.getLocalDateTime() != null) {
            return TypeDescriptor.TIMESTAMP;
        } else if (expression.getLocalTime() != null) {
            return TypeDescriptor.TIME;
        } else if (expression.getDuration() != null) {
            return TypeDescriptor.INTERVAL;
        } else if (expression.isNull()) {
            return TypeDescriptor.NULL;
        } else {
            throw new KorykiaiException("Cannot resolve type for expression: " + expression);
        }
    }

    private TypeDescriptor resolveSelect(Select select) {
        Out first = SqlQueryRenderer.collectOut(select).get(0);
        return child(select).resolve(first.getExpression());
    }

    private static String getHeaderOrColumnname(Out out) {
        if (out.getHeader() != null) {
            return out.getHeader();
        } else if (out.getExpression() != null && out.getExpression().getField() != null) {
            return out.getExpression().getField().getName();
        } else {
            return null;
        }
    }

    private TypeDescriptor resolveField(Field field) {
        Source s = visibility.getSource(field.getAlias());
        Block block = visibility.getBlock(s.getName());

        if (block != null) {
            List<Out> outList = SqlQueryRenderer.collectOut(block);
            Out out =
                    outList.stream()
                            .filter(o -> field.getName().equals(getHeaderOrColumnname(o)))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new KorykiaiException(
                                                    "Column not found in block: "
                                                            + field.getName()));
            return child(SqlQueryRenderer.select(block.getSet())).resolve(out.getExpression());
        } else {
            // Every step below used a bare Optional.get(). Any of them could fail, and all of them
            // failed the same way: NoSuchElementException("No value present"), with no indication
            // of
            // which name could not be resolved. That is the most common error the shared corpus
            // produces — a fixture written against one dialect's schema meets a dialect whose
            // schema
            // lacks the column — and it said nothing at all. SchemaValidator now reports the usual
            // case with a position; these messages are the backstop for the paths that skip it.
            Entity entity =
                    linkResolver
                            .getModel()
                            .getEntity(s.getName())
                            .orElseThrow(
                                    () ->
                                            new KorykiaiException(
                                                    "unknown table '"
                                                            + s.getName()
                                                            + "' in the model"));
            String tableName = entity.getTable() != null ? entity.getTable() : entity.getName();
            Table dbtable =
                    linkResolver
                            .getSchema()
                            .getTable(tableName)
                            .orElseThrow(
                                    () ->
                                            new KorykiaiException(
                                                    "table '"
                                                            + tableName
                                                            + "' is not in this schema"));

            if (dbtable.getColumn(field.getName()).isPresent()) {
                ai.koryki.catalog.schema.Column column = dbtable.getColumn(field.getName()).get();
                return linkResolver.getTypeDescriptor(column);
            } else {
                Attribute attr =
                        entity.getAttribute(field.getName())
                                .orElseThrow(
                                        () ->
                                                new KorykiaiException(
                                                        "unknown column '"
                                                                + field.getName()
                                                                + "' on "
                                                                + entity.getName()
                                                                + " — it is neither a column of table '"
                                                                + tableName
                                                                + "' nor a declared attribute"));
                String attrCol = attr.getColumn() != null ? attr.getColumn() : attr.getName();

                String col =
                        entity.getAttribute(attrCol)
                                .map(a -> a.getColumn() != null ? a.getColumn() : a.getName())
                                .orElseGet(
                                        () ->
                                                dbtable.getColumn(attrCol)
                                                        .orElseThrow(
                                                                () ->
                                                                        new KorykiaiException(
                                                                                "attribute '"
                                                                                        + field
                                                                                                .getName()
                                                                                        + "' of "
                                                                                        + entity
                                                                                                .getName()
                                                                                        + " maps to column '"
                                                                                        + attrCol
                                                                                        + "', which table '"
                                                                                        + tableName
                                                                                        + "' does not have"))
                                                        .getName());

                ai.koryki.catalog.schema.Column column =
                        dbtable.getColumn(col)
                                .orElseThrow(
                                        () ->
                                                new KorykiaiException(
                                                        "attribute '"
                                                                + field.getName()
                                                                + "' of "
                                                                + entity.getName()
                                                                + " maps to column '"
                                                                + col
                                                                + "', which table '"
                                                                + tableName
                                                                + "' does not have"));
                return linkResolver.getTypeDescriptor(column);
            }
        }
    }

    private TypeDescriptor resolveFunction(Function function) {
        return catalog.descriptor(
                new FunctionBinding(function, this::resolve, bindLambdas(function)));
    }

    /**
     * Gives every lambda argument of the call the types of its parameters, which the function
     * declares from the types of its other arguments, and types its body with them. The types are
     * written to the lambda too ({@link Lambda#setParameterTypes}), because the renderer meets the
     * body later and must know what {@code @x@price} selects from.
     *
     * @return the type of the body of each lambda, by argument position; a lambda that cannot be
     *     typed (an argument of the wrong type, a parameter count the function does not take) is
     *     left out, and whoever checks the call reports why
     */
    public java.util.Map<Integer, TypeDescriptor> bindLambdas(Function function) {
        java.util.Map<Integer, TypeDescriptor> bodies = new java.util.HashMap<>();
        List<Expression> arguments = function.getArguments();
        if (arguments.stream().noneMatch(a -> a.getLambda() != null)) {
            return bodies;
        }
        List<TypeDescriptor> argumentTypes = new java.util.ArrayList<>();
        for (Expression a : arguments) {
            TypeDescriptor type = null;
            if (a.getLambda() == null) {
                try {
                    type = resolve(a);
                } catch (RuntimeException unresolved) {
                    // left null: the list is then untyped and so is the lambda
                }
            }
            argumentTypes.add(type);
        }
        for (int i = 0; i < arguments.size(); i++) {
            Lambda lambda = arguments.get(i).getLambda();
            if (lambda == null) {
                continue;
            }
            ai.koryki.iql.functions.LambdaSpec spec =
                    catalog.lambda(function.getFunc(), arguments.size(), i);
            int n = lambda.getParameters().size();
            if (spec == null || n < spec.minParameters() || n > spec.maxParameters()) {
                continue;
            }
            List<TypeDescriptor> types = spec.parameterTypes().of(argumentTypes);
            if (types == null) {
                continue;
            }
            types = List.copyOf(types.subList(0, n));
            lambda.setParameterTypes(types);
            java.util.Map<String, TypeDescriptor> scope = new java.util.LinkedHashMap<>();
            for (int k = 0; k < n; k++) {
                scope.put(lambda.getParameters().get(k), types.get(k));
            }
            bodies.put(i, withParameters(scope).resolve(lambda.getBody()));
        }
        return bodies;
    }

    private ExpressionTypeResolver child(Select select) {
        return new ExpressionTypeResolver(linkResolver, visibility.child(select), catalog);
    }

    public static TypeFamily toGenericType(String genericType) {
        return TypeFamilyRegistry.of(genericType);
    }
}
