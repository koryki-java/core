package ai.koryki.result;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.DictionaryTranslator;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.query.*;
import ai.koryki.iql.functions.MathOp;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.validate.FunctionValidator;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.result.metric.MetricNamer;
import ai.koryki.result.metric.Shapes;
import ai.koryki.result.quantity.Quantity;
import ai.koryki.result.quantity.UnitRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives per-column {@link Finding}s (headers, types, grain, quantity, metric names)
 * for a transpiled query.
 *
 * <p>This class is a stateless <em>initializer</em>: it holds only immutable configuration
 * (the locale bundle and optional schema translator) and is safe to reuse and share across
 * threads. The actual work runs in a fresh {@link Investigation} — a single-use worker bound
 * to one query's transpiler — minted per call by {@link #asInfoProvider} / {@link #findout}.
 * Because the worker's transpiler is {@code final} and its {@code block2Info} cache is private
 * to that instance, nothing is mutated mid-flight and no state leaks between runs.
 */
public class Investigator {

    private final ResourceBundle bundle;
    private final DictionaryTranslator schemaTranslator;

    public Investigator(DictionaryTranslator schemaTranslator) {
        this(schemaTranslator, Locale.ENGLISH);
    }

    public Investigator(Locale locale) {
        this(null, locale);
    }

    public Investigator(DictionaryTranslator schemaTranslator, Locale locale) {
        this.schemaTranslator = schemaTranslator;
        this.bundle = ResourceBundle.getBundle("ai.koryki.result.label", locale);
    }

    public java.util.function.Function<KQLTranspiler, ListResult> findout(
            java.util.function.Function<KQLTranspiler, ListResult> f ) {

        return (t) -> {

            ListResult result = f.apply(t);
            result.setInfos(new Investigation(t).getInfoList());
            return result;
        };
    }

    public java.util.function.Function<KQLTranspiler, java.util.List<Finding>> asInfoProvider() {
        return t -> new Investigation(t).getInfoList();
    }

    private String localeOrNull(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    private String locale(String key) {
        try {
            String s = bundle.getString(key);
            return s;
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Coarse fallback type family for a Finding, used only when the authoritative
     * {@code TypeDescriptor} could not be resolved. Mirrors the historical GenericType
     * mapping (int/double/string, everything else → text) in the canonical vocabulary.
     */
    private static TypeFamily toFallbackFamily(String kind) {
        switch (kind) {
            case "int": return CoreTypeFamily.INTEGER;
            case "double": return CoreTypeFamily.FLOAT;
            case "string": return CoreTypeFamily.TEXT;
            default:
                return CoreTypeFamily.TEXT;

        }
    }

    private static void collectSourceGroups(Source start, List<Join> joins, List<Group> groups) {
        if (start != null && start.getGroup() != null) {
            groups.addAll(start.getGroup());
        }
        if (joins == null) {
            return;
        }
        for (Join join : joins) {
            collectSourceGroups(join.getSource(), join.getJoin(), groups);
        }
    }

    private static void collectSourceOrders(Source start, List<Join> joins, List<ai.koryki.iql.query.Order> orders) {
        if (start != null && start.getOrder() != null) {
            orders.addAll(start.getOrder());
        }
        if (joins == null) {
            return;
        }
        for (Join join : joins) {
            collectSourceOrders(join.getSource(), join.getJoin(), orders);
        }
    }

    /** Precision casts change the representation, never the quantity or the shape. */
    private static boolean isNumericCast(String func) {
        return "to_decimal".equals(func) || "to_number".equals(func);
    }

    private static Quantity quantity(Finding f) {
        return f.getQuantity() != null ? f.getQuantity() : Quantity.UNKNOWN;
    }

    private static String getKey(Out o) {
        return o.getHeader() != null ? o.getHeader() : Integer.toString(o.getIdx());
    }

    private String getTranslatedColumn(IQLVisibilityContext visibility, Source table, Field column) {

        Source b = visibility.getLeadingSource(table.getName());

        String tablename = b != null ? b.getName() : table.getName();
        String c = schemaTranslator.field(tablename, column.getName());
        if (c == null) {
            throw new KorykiaiException("unknow column " + table.getAlias() + " " + table.getName() + "." + column.getName());
        }
        return c;
    }

    /**
     * A single-use investigation over one query. Holds the per-run working state — the
     * bound {@code transpiler} (final) and the block→findings cache — so the enclosing
     * {@link Investigator} stays immutable config. Reads that config (bundle, translator,
     * static helpers) directly from the enclosing instance.
     */
    private final class Investigation {

        private final KQLTranspiler transpiler;
        private final Map<String, Map<String, Finding>> block2Info = new HashMap<>();

        Investigation(KQLTranspiler transpiler) {
            this.transpiler = transpiler;
        }

        private List<Finding> getInfoList() {

            IQLVisibilityContext visibility = transpiler.visibility().child(SqlQueryRenderer.select(transpiler.getQuery()));

            transpiler.getQuery().getBlock().forEach(b -> block2Info.put(b.getId(), info(visibility, b)));

            Map<String, Finding> infos = info(visibility, SqlQueryRenderer.collectOut(transpiler.getQuery()));
            List<Finding> list = new ArrayList<>(infos.values());
            attachTypeDescriptors(list);
            attachOrder(list);
            attachGrouping(list);
            attachMetric(list);
            return list;
        }

        /**
         * Concludes localized metric names ("Net revenue"/"Nettoumsatz") for
         * derived measure columns from their expression shapes. Additional
         * information only - the mechanical header stays untouched, and a missing
         * bundle entry means no metric rather than a raw key in the UI.
         * Best-effort: failures never break execution.
         */
        private void attachMetric(List<Finding> list) {
            try {
                MetricNamer namer = new MetricNamer();
                for (Finding f : list) {
                    namer.metricKey(f.getShape()).map(Investigator.this::localeOrNull).ifPresent(f::setMetric);
                }
            } catch (RuntimeException e) {
                // leave metrics unset
            }
        }

        /**
         * Marks columns that appear in the GROUP BY - the dimensions of an
         * aggregated result. KQL never writes GROUP explicitly; the GroupRule adds
         * a Group for every non-aggregate output when aggregates are present,
         * sharing the Out's expression instance - identity is the join key, like
         * attachOrder. PushGroupRule then moves the Group objects from the Select
         * onto its Sources, so both places are collected.
         * Best-effort: failures never break execution.
         */
        private void attachGrouping(List<Finding> list) {
            try {
                Select select = SqlQueryRenderer.select(transpiler.getQuery());
                List<Group> groups = new ArrayList<>(select.getGroup());
                collectSourceGroups(select.getStart(), select.getJoin(), groups);
                for (Group group : groups) {
                    for (Finding f : list) {
                        if (f.getOut() != null && f.getOut().getExpression() == group.getExpression()) {
                            f.setGrouped(true);
                        }
                    }
                }
            } catch (RuntimeException e) {
                // leave grouping unset
            }
        }

        /**
         * Marks columns the query explicitly orders by (direction + sort priority).
         * KQL declares ordering on the fetch item itself, and KQLQueryMapper shares
         * the expression instance between the Out and the Order built from the same
         * fetch item - identity is the join key. Order.idx carries the sort priority
         * (explicit number like `DESC 2`, or the fetch position when implicit).
         * PushOrderRule may have moved the Order objects from the Select onto its
         * Sources (like PushGroupRule for groups), so both places are collected.
         * Best-effort like attachTypeDescriptors: failures never break execution.
         */
        private void attachOrder(List<Finding> list) {
            try {
                Select select = SqlQueryRenderer.select(transpiler.getQuery());
                List<ai.koryki.iql.query.Order> orders = new ArrayList<>(select.getOrder());
                collectSourceOrders(select.getStart(), select.getJoin(), orders);
                // an Order and its Out share the fetch item's idx; the Order holds a distinct
                // Expression instance (toOut/toOrder each build one) and query rewrites move the
                // order onto the source, so match on idx rather than expression identity.
                for (ai.koryki.iql.query.Order order : orders) {
                    for (Finding f : list) {
                        if (f.getOut() != null && f.getOut().getIdx() == order.getIdx()) {
                            f.setOrderDirection(order.getSort() != null ? order.getSort().name() : null);
                            f.setOrderPriority(order.getIdx());
                        }
                    }
                }
            } catch (RuntimeException e) {
                // leave order info unset
            }
        }

        /**
         * Attaches the transpiler's resolved output types (same column order - both
         * derive from the query's collected outputs). Best-effort: type resolution
         * must never break query execution, and a header-key collision in the info
         * map would break index alignment, so sizes are checked first.
         */
        private void attachTypeDescriptors(List<Finding> list) {
            try {
                List<ai.koryki.kql.HeaderInfo> typed = transpiler.infos(ai.koryki.kql.HeaderInfo::new);
                if (typed.size() == list.size()) {
                    for (int i = 0; i < list.size(); i++) {
                        list.get(i).setTypeDescriptor(typed.get(i).getTypeDescriptor());
                    }
                }
            } catch (RuntimeException e) {
                // leave descriptors unset - the frontend omits type display when absent
            }
        }

        private Map<String, Finding> info(IQLVisibilityContext visibility, Block block) {

            Select select =  SqlQueryRenderer.select(block.getSet());
            IQLVisibilityContext v = visibility.child(select);

            return info(v, SqlQueryRenderer.collectOut(select));
        }

        private Map<String, Finding> info(IQLVisibilityContext visibility, List<Out> out) {

            Map<String, Finding> m = new LinkedHashMap<>();
            out.forEach(o -> {
                String h = getKey(o);
                m.put(h, info(visibility, o));
            });
            return m;
        }


        private Finding info(IQLVisibilityContext visibility, Out out) {
            Expression expression = out.getExpression();
            Finding f = info(visibility, expression);
            f.setOut(out);
            return f;
        }

        private Finding info(IQLVisibilityContext visibility, Expression expression) {
            if (expression.getSelect() != null) {
                IQLVisibilityContext v = visibility.child(expression.getSelect());
                return info(v, expression.getSelect().getOut().get(0));
            } else if (expression.getFunction() != null) {
                return function(visibility, expression.getFunction());
            } else if (expression.getText() != null) {
                Finding i = new Finding();
                i.setHeader("{text}");
                return i;
            } else if (expression.getNumber() != null) {
                Finding i = new Finding();
                i.setHeader("{number}");
                i.setQuantity(Quantity.DIMENSIONLESS);
                // the value is lost at Finding level but the shape keeps it - the
                // metric rules need it to recognize the (1 - ratio) net modifier
                i.setShape(Shapes.literal(expression.getNumber()));
                return i;
            } else if (expression.getLocalDate() != null) {
                Finding i = new Finding();
                i.setHeader("{date}");
                return i;
            } else if (expression.getLocalDateTime() != null) {
                Finding i = new Finding();
                i.setHeader("{datetime}");
                return i;
            } else if (expression.getLocalTime() != null) {
                Finding i = new Finding();
                i.setHeader("{time}");
                return i;
            } else if (expression.getField() != null) {
                return column(visibility, expression.getField());
            } else if (expression.getIdentity() != null) {
                throw new KorykiaiException("identity is not allowed here");
            } else {
                throw new KorykiaiException();
            }
        }

        private Finding function(IQLVisibilityContext visibility, Function function) {

            Finding i = new Finding();

            String func = function.getFunc();

            i.setAggregateFunction(StandardFunctions.isAggregate(func) ? func : null);
            i.setAggregate(FunctionValidator.isAggregate(function));

            List<Finding> arg = function.getArguments().stream().map(a -> info(visibility, a)).collect(Collectors.toList());

            if (MathOp.operator(func) != null) {
                MathOp m = MathOp.valueOf(func);
                i.setMath(m);
                i.setHeader(arg.stream().map(a -> a.getHeader()).collect(Collectors.joining(m.getOperator())));
            } else {
                i.setHeader(locale(func) + "("+ arg.stream().map(a -> a.getHeader()).collect(Collectors.joining(", ")) + ")");
            }
            apply(i, func, arg);
            attachGrain(i, function, arg);
            deriveQuantity(i, func, arg);
            deriveShape(i, function, arg);

            return i;
        }

        /**
         * Builds the canonical expression shape bottom-up alongside the quantity -
         * the metric rules match on it. MathOp names map to Op nodes ("negate" is
         * special-cased: MathOp.operator() has no mapping for it); numeric casts
         * are transparent (sum(to_decimal(price*qty)) must still read as revenue);
         * everything else becomes a Call. Operand shapes may be null (text/date
         * literals) - Shapes normalizes them to Opaque.
         */
        private void deriveShape(Finding finding, Function function, List<Finding> arg) {
            String func = function.getFunc();
            if (isNumericCast(func) && !arg.isEmpty()) {
                finding.setShape(arg.get(0).getShape());
                return;
            }
            List<ai.koryki.result.metric.Shape> shapes =
                    arg.stream().map(Finding::getShape).collect(Collectors.toList());
            Quantity q = finding.getQuantity();
            if (MathOp.operator(func) != null || "negate".equals(func)) {
                finding.setShape(Shapes.op(MathOp.valueOf(func), shapes, q));
            } else {
                boolean windowed = function.getWindow() != null;
                boolean ordered = windowed && function.getWindow().getOrder() != null
                        && !function.getWindow().getOrder().isEmpty();
                finding.setShape(Shapes.call(func, windowed, ordered, shapes, q));
            }
        }

        /**
         * Time-grain detection. date_trunc and the *_begin family produce real
         * calendar buckets; the part extractors (month(x) -> 1..12) produce cyclic
         * ordinals and return INTEGER, so downstream temporal classification must
         * use the grain, never the type family alone. min/max pass a grain through.
         */
        private void attachGrain(Finding finding, Function function, List<Finding> arg) {
            switch (function.getFunc()) {
                case "year_begin":
                case "quarter_begin":
                case "month_begin":
                case "day_begin": {
                    String func = function.getFunc();
                    finding.setGrain(func.substring(0, func.indexOf('_')));
                    return;
                }
                case "date_trunc": {
                    Expression part = function.getArguments().isEmpty() ? null : function.getArguments().get(0);
                    if (part != null && part.getText() != null) {
                        finding.setGrain(part.getText());
                    }
                    return;
                }
                case "year":
                case "month":
                case "day":
                case "hour":
                case "minute":
                case "second":
                    finding.setGrain(function.getFunc());
                    finding.setOrdinalTemporal(true);
                    return;
                case "min":
                case "max":
                    if (!arg.isEmpty()) {
                        finding.setGrain(arg.get(0).getGrain());
                        finding.setOrdinalTemporal(arg.get(0).isOrdinalTemporal());
                    }
                    return;
                default:
            }
        }

        /**
         * Quantity-calculus propagation through the expression tree: multiply adds
         * dimension exponents, divide subtracts them, add/subtract require equal
         * dimensions and preserve them (revenue - cost = profit), count is a
         * dimensionless count. Underivable combinations degrade to UNKNOWN.
         */
        private void deriveQuantity(Finding finding, String func, List<Finding> arg) {
            MathOp op = MathOp.operator(func) != null ? MathOp.valueOf(func) : null;
            if (op != null && !arg.isEmpty()) {
                Quantity q = quantity(arg.get(0));
                for (int k = 1; k < arg.size(); k++) {
                    Quantity o = quantity(arg.get(k));
                    switch (op) {
                        case multiply: q = q.times(o); break;
                        case divide: q = q.dividedBy(o); break;
                        case add:
                        case minus: q = q.plus(o); break;
                        case negate: break;
                    }
                }
                finding.setQuantity(q);
            } else {
                switch (func) {
                    case "count":
                        finding.setQuantity(Quantity.COUNT);
                        break;
                    case "sum":
                    case "avg":
                    case "min":
                    case "max":
                    case "lag":
                    case "lead":
                    case "first_value":
                    case "last_value":
                    case "abs":
                    case "round":
                    case "floor":
                    case "ceil":
                    case "to_decimal":
                    case "to_number":
                        if (!arg.isEmpty()) {
                            finding.setQuantity(arg.get(0).getQuantity());
                        }
                        break;
                    default:
                        // unknown function: quantity stays unset
                }
            }
            if (finding.getQuantity() != null && finding.getQuantity().symbol() != null) {
                finding.setUnit(finding.getQuantity().symbol());
            }
        }

        private void apply(Finding finding, String function, List<Finding> findings) {

            switch (function) {
                case "count": {
                    finding.setFallbackFamily(toFallbackFamily("int"));
                    return;
                }
                case "sum":
                    finding.setFallbackFamily(findings.get(0).getFallbackFamily());
                    return;
                case "avg":
                    finding.setFallbackFamily(toFallbackFamily("double"));
                    return;
                case "min":
                    finding.setFallbackFamily(findings.get(0).getFallbackFamily());
                    return;
                case "max":
                    finding.setFallbackFamily(findings.get(0).getFallbackFamily());
                    return;
                default:
                    finding.setFallbackFamily(toFallbackFamily("string"));
                    return;
            }
        }

        private Finding column(IQLVisibilityContext visibility, Field column) {

            Finding i = new Finding();

            ai.koryki.iql.query.Source t = visibility.getSource(column.getAlias());
            if (t == null) {
                throw new RuntimeException(column.getAlias() + "." + column.getName());
            }


            Source block = visibility.getLeadingSource(t.getName());
            if (block != null) {

                Map<String, Finding> bi = block2Info.get(t.getName());

                for (Finding bf : bi.values()) {
                    Out bOut = bf.getOut();
                    if (bOut == null) continue;
                    String fieldName = bOut.getHeader() != null ? bOut.getHeader()
                            : (bOut.getExpression() != null && bOut.getExpression().getField() != null
                                    ? bOut.getExpression().getField().getName() : null);
                    if (column.getName().equals(fieldName)) {
                        i.setHeader(bf.getHeader());
                        // a CTE column keeps what its defining expression derived
                        i.setFallbackFamily(bf.getFallbackFamily());
                        i.setAggregate(bf.isAggregate());
                        i.setAggregateFunction(bf.getAggregateFunction());
                        i.setGrain(bf.getGrain());
                        i.setOrdinalTemporal(bf.isOrdinalTemporal());
                        i.setQuantity(bf.getQuantity());
                        i.setUnit(bf.getUnit());
                        i.setShape(bf.getShape());
                        break;
                    }
                }
            }

            // TODO get table from blockId
            // The physical schema is keyed by table/column names; localized models
            // use their own entity/attribute names, so resolve through the entity's
            // table and the attribute's column mapping where present.
            String tableName = t.getName();
            String columnName = column.getName();
            Optional<ai.koryki.catalog.domain.Entity> entity = transpiler.getResolver().getModel().getEntity(t.getName());
            if (entity.isPresent()) {
                ai.koryki.catalog.domain.Entity x = entity.get();
                if (x.getTable() != null) {
                    tableName = x.getTable();
                }
                Optional<ai.koryki.catalog.domain.Attribute> attribute = x.getAttribute(column.getName());
                if (attribute.isPresent()) {
                    ai.koryki.catalog.domain.Attribute c = attribute.get();
                    if (c.getColumn() != null) {
                        columnName = c.getColumn();
                    }
                    String tn = x.getLabel() != null ? x.getLabel() : c.getName();
                    String cn =  c.getLabel() != null ? c.getLabel() : c.getName();
                    i.setHeader(tn + " " + cn);
                }
            }
            String physicalTable = tableName;
            String physicalColumn = columnName;
            transpiler.getResolver().getSchema().getTable(physicalTable).ifPresent(x -> {
                x.getColumn(physicalColumn).ifPresent(c -> {

                    i.setFallbackFamily(toFallbackFamily(c.getTypeFamily()));
                    i.setDialectType(c.getDialectType());
                    i.setKey(c.getPkPos() > 0);
                    //i.setScale(c.getScale());
                    attachQuantity(i, physicalTable, c);

                });
            });

            if (i.getShape() == null) {
                i.setShape(Shapes.leaf(i.getHeader(), i.getQuantity()));
            }
            return i;
        }

        /**
         * Resolves the column's unit and quantity kind: the semantic layer
         * (Column.unit / Column.quantity in db.json) wins, the side-car resource
         * /ai/koryki/result/units/&lt;schema&gt;.json is the fallback for schemas
         * whose db.json cannot be extended. Best-effort like the other attach steps.
         */
        private void attachQuantity(Finding finding, String table, Column c) {
            try {
                String unit = c.getUnit();
                String kind = c.getQuantity();
                if (unit == null && kind == null) {
                    String schema = transpiler.getResolver().getSchema().getName();
                    UnitRegistry.ColumnUnit cu = UnitRegistry.sidecar(schema).get(table + "." + c.getName());
                    if (cu != null) {
                        unit = cu.unit();
                        kind = cu.quantity();
                    }
                }
                Quantity q = UnitRegistry.quantity(unit, kind);
                if (q.known()) {
                    finding.setQuantity(q);
                    if (q.symbol() != null) {
                        finding.setUnit(q.symbol());
                    }
                }
            } catch (RuntimeException e) {
                // leave quantity unset
            }
        }
    }

}
