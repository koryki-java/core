package ai.koryki.iql.types;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionBinding;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.query.*;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeFamily;
import ai.koryki.catalog.schema.types.TypeFamilyRegistry;
import ai.koryki.catalog.schema.types.TypeNames;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public class ExpressionTypeResolver {

    private final LinkResolver linkResolver;
    private final IQLVisibilityContext visibility;
    private final FunctionCatalog catalog;

    // Memo by node identity, scoped to this resolver's visibility context. Per-instance
    // (never shared) so the same Expression can resolve differently in a child scope.
    private final java.util.Map<Expression, TypeDescriptor> cache = new java.util.IdentityHashMap<>();

    public ExpressionTypeResolver(LinkResolver linkResolver, IQLVisibilityContext visibility, FunctionCatalog catalog) {
        this.linkResolver = linkResolver;
        this.visibility = visibility;
        this.catalog = catalog;
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
        } else if (expression.getIdentity() != null) {
            throw new KorykiaiException("identity is not allowed here");
        } else if (expression.getNumber() != null) {
            if (expression.getNumber() instanceof BigInteger) {
                return TypeDescriptor.INTEGER;
            } else if (expression.getNumber() instanceof BigDecimal bd) {
                // carry the literal's own precision/scale (e.g. 12.34 -> DECIMAL(4,2))
                return new TypeDescriptor(TypeNames.TYPE_DECIMAL, null, CoreTypeFamily.DECIMAL,
                        bd.precision(), bd.scale());
            } else {
                throw new KorykiaiException("unknown number type: " + expression.getNumber().getClass());
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
            Out out = outList.stream()
                    .filter(o -> field.getName().equals(getHeaderOrColumnname(o)))
                    .findFirst()
                    .orElseThrow(() -> new KorykiaiException("Column not found in block: " + field.getName()));
            return child(SqlQueryRenderer.select(block.getSet())).resolve(out.getExpression());
        } else {
            Entity entity = linkResolver.getModel().getEntity(s.getName()).get();
            Table dbtable = linkResolver.getSchema().getTable(
                    entity.getTable() != null ? entity.getTable() : entity.getName()).get();

            if (dbtable.getColumn(field.getName()).isPresent()) {
                ai.koryki.catalog.schema.Column column = dbtable.getColumn(field.getName()).get();
                return linkResolver.getTypeDescriptor(column);
            } else {
                Optional<Attribute> oo = entity.getAttribute(field.getName());
                Attribute attr = oo.get();

                String col = entity.getAttribute(attr.getColumn() != null ? attr.getColumn() : attr.getName())
                        .map(a -> a.getColumn() != null ? a.getColumn() : a.getName())
                        .orElse(dbtable.getColumn(attr.getColumn() != null ? attr.getColumn() : attr.getName()).get().getName());

                ai.koryki.catalog.schema.Column column = dbtable.getColumn(col).get();
                return linkResolver.getTypeDescriptor(column);
            }
        }
    }

    private TypeDescriptor resolveFunction(Function function) {
        return catalog.descriptor(new FunctionBinding(function, this::resolve));
    }

    private ExpressionTypeResolver child(Select select) {
        return new ExpressionTypeResolver(linkResolver, visibility.child(select), catalog);
    }

    public static TypeFamily toGenericType(String genericType) {
        return TypeFamilyRegistry.of(genericType);
    }
}
