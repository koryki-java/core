package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.*;
import ai.koryki.iql.query.*;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;

public class SchemaValidator implements Collector<List<Violation>> {

    private final LinkResolver resolver;

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final Map<String, Select> blockIdToSelectMap = new HashMap<>();
    private final Map<String, Source> blockIdToLeadingSourceMap;
    private Map<String, Source> recursiveAliasToSourceMap;

    /**
     * Alias resolution, scope by scope — the same stack {@code FunctionValidator} keeps.
     *
     * <p>This used to be one flat {@code Map<String, Source>} filled as sources were visited, which
     * is wrong twice over: an alias used in two scopes kept whichever source was seen last, and
     * fields are visited in document order, so a field was resolved before the sources written after
     * it had even been recorded. {@code block_join.iql} is both at once — {@code o} names
     * {@code orders} inside the WITH block and the block {@code ord} outside it, and the outer
     * {@code o.f} is written one line before the {@code JOIN} that binds it. It resolved to
     * {@code orders}.
     *
     * <p>The scope maps come from {@code SelectScopeCollector}, which collects <em>all</em> sources
     * of a Select up front rather than incrementally, and {@link IQLVisibilityContext#child} layers
     * an inner scope over its parent so an inner alias shadows an outer one.
     */
    private final IQLVisibilityContext rootVisibility;
    private final Deque<IQLVisibilityContext> scopes = new ArrayDeque<>();

    public SchemaValidator(LinkResolver resolver, Map<String, Source> blockIdToLeadingSourceMap,
            Map<Object, RuleContext> iqlToContext, IQLVisibilityContext visibility) {
        this.resolver = resolver;
        this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
        this.iqlToContext = iqlToContext;
        this.rootVisibility = visibility;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        scopes.push((scopes.isEmpty() ? rootVisibility : scopes.peek()).child(select));
        checkJoinColumns(select.getStart(), select.getJoin());
        return true;
    }

    @Override
    public void leave(Select select) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    /**
     * An EXISTS is a scope too, and not a Select — it carries its own start source and joins, and
     * {@code SelectScopeCollector} files it under its own key. Pushing only Selects left its alias
     * unresolvable inside it, while the enclosing aliases stayed visible through {@link
     * IQLVisibilityContext#child} — which is exactly the correlation an EXISTS relies on.
     */
    @Override
    public boolean visit(Deque<Object> deque, Exists exists) {
        scopes.push((scopes.isEmpty() ? rootVisibility : scopes.peek()).child(exists));
        return true;
    }

    @Override
    public void leave(Exists exists) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    /**
     * The columns of an explicitly written join, checked against the entity of their own side.
     *
     * <p>Reported here rather than where the join is resolved, for two reasons. A resolver throws on
     * the first bad column and stops, so a query with several mistakes reveals them one run at a
     * time; collected violations name them all at once. And it happens during validation, alongside
     * every other unknown column, instead of at render time — the author sees one kind of message
     * for one kind of mistake.
     *
     * <p>Walks the chain the way the renderer does: the select's leading source is the left of the
     * first join, and each join's own source is the left of the joins nested under it. A {@code Join}
     * does not carry its left side, so it cannot be checked in isolation.
     *
     * <p>The position is the join clause, not the individual name — the column names are plain
     * strings in the model, with no parse context of their own. Writing a pair the wrong way round
     * surfaces here too, as the column then does not exist on the side it was written for.
     */
    private void checkJoinColumns(Source left, java.util.List<ai.koryki.iql.query.Join> joins) {
        if (left == null || joins == null) {
            return;
        }
        for (ai.koryki.iql.query.Join join : joins) {
            Source right = join.getSource() != null ? join.getSource() : source(join.getRef());
            if (join.getColumns() != null && right != null) {
                checkJoinSide(join, left, join.getColumns().left());
                checkJoinSide(join, right, join.getColumns().right());
            }
            checkJoinColumns(right != null ? right : left, join.getJoin());
        }
    }

    private void checkJoinSide(Object anchor, Source source, java.util.List<String> columns) {
        Optional<ai.koryki.catalog.domain.Entity> entity = resolver.getModel().getEntity(source.getName());
        if (entity.isEmpty()) {
            return;
        }
        for (String column : columns) {
            if (attribute(entity.get(), column).isEmpty()) {
                violations.add(new Violation(Violation.UNKNOWN_COLUMN, anchor,
                        Range.of(iqlToContext, anchor),
                        "unknown column '" + column + "' on " + source.getName())
                        .suggesting(columnSuggestions(entity.get(), column)));
            }
        }
    }

    /** The source an alias names in the innermost scope that binds it. */
    private Source source(String alias) {
        IQLVisibilityContext scope = scopes.isEmpty() ? rootVisibility : scopes.peek();
        return scope == null ? null : scope.getSource(alias);
    }

    /** A block bound to a placeholder has no set — same reason as in {@code BlockLeadingSourceCollector}. */
    @Override
    public boolean visit(Deque<Object> deque, Block block) {
        if (block.getSet() == null) {
            return true;
        }
        blockIdToSelectMap.put(block.getId(), SelectScopeCollector.getLeadingSelect(block.getSet()));
        recursiveAliasToSourceMap = Walker.apply(block, new AliasToSourceCollector());

        return true;
    }

    @Override
    public void leave(Block block) {
        recursiveAliasToSourceMap = null;
    }


    @Override
    public boolean visit(Deque<Object> deque, Source source) {

        if (recursiveAliasToSourceMap != null && recursiveAliasToSourceMap.containsKey(source.getName())) {
            // recursive source in CTE
            return true;
        }

        if (!blockIdToLeadingSourceMap.containsKey(source.getName())) {
            if (resolver.getModel().getEntity(source.getName()).isEmpty()) {
                violations.add(new Violation("schema", source, Range.of(iqlToContext, source),
                        "invalid source '" + source.getName() + "'")
                        .suggesting(Suggest.closest(source.getName(), entityNames())));
            }
        }
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, Field field) {

        Source selectTable = source(field.getAlias());
        if (selectTable == null) {
           violations.add(new Violation("schema", field, Range.of(iqlToContext, field),
                   "unknown alias " + field.getAlias())
                   .suggesting(Suggest.closest(field.getAlias(), aliases())));
        } else {
            Select select = blockIdToSelectMap.get(selectTable.getName());
            if (select != null) {
                List<Out> outs = SqlQueryRenderer.collectOut(select);
                if (outs.stream().anyMatch(o -> match(field, o))) {
                    return true;
                } else {
                    violations.add(new Violation("schema", field, Range.of(iqlToContext, field),
                            "unknown header " + field.getName())
                            .suggesting(Suggest.closest(field.getName(), headerNames(outs))));
                }
            } else {
                Optional<ai.koryki.catalog.domain.Entity> optional = resolver.getModel().getEntity(selectTable.getName());
                if (optional.isEmpty()) {
                    violations.add(new Violation("schema", field, Range.of(iqlToContext, field),
                            "invalid table " + selectTable.getName())
                            .suggesting(Suggest.closest(selectTable.getName(), entityNames())));
                } else {
                    checkColumnExists(field, optional.get());
                }
            }
        }
        return true;
    }

    /**
     * The sibling of "unknown alias" and "invalid table", and it was missing: a field naming a
     * column the entity does not have passed validation untouched and blew up later in
     * {@code ExpressionTypeResolver.resolveField}, on a bare {@code Optional.get()}, as
     * {@code NoSuchElementException: No value present} — no position, no column name, nothing to act
     * on.
     *
     * <p>A field resolves as one of the entity's declared attributes, and as nothing else. The
     * model is the language: what model.json does not list, KQL cannot name. This used to also
     * accept any physical column of the underlying table, which quietly made db.json a second
     * vocabulary — so a query could name a foreign key the model deliberately hides behind a link,
     * and only find out on the dialect whose schema happened to spell it differently. The corpus
     * already documented the strict rule as though it were in force; only this method disagreed.
     *
     * <p>The attribute must be named as the <em>model</em> names it. {@code Entity#getAttribute}
     * also answers to an attribute's physical {@code column} override, which is the same second
     * vocabulary arriving by a side door — under the German model it would accept
     * {@code o.order_id} for {@code bestell_id}. {@link #attribute} does not.
     *
     * <p>The schema is not consulted at all any more, and with it goes the early return taken when
     * the table was absent from db.json — that return silently switched off column checking for the
     * whole entity. Nothing is lost: {@link Violation#UNKNOWN_COLUMN} stays the category, so a
     * shared corpus can still skip a fixture on the dialects whose model lacks the column.
     *
     * <p>The alias is resolved in its own scope (see {@link #scopes}), so this reports on every
     * field rather than only on aliases bound once in the whole query — the compromise the flat map
     * forced.
     */
    private void checkColumnExists(Field field, ai.koryki.catalog.domain.Entity entity) {
        if (attribute(entity, field.getName()).isPresent()) {
            return;
        }
        violations.add(new Violation(Violation.UNKNOWN_COLUMN, field, Range.of(iqlToContext, field),
                "unknown column '" + field.getName() + "' on " + entity.getName())
                .suggesting(columnSuggestions(entity, field.getName())));
    }

    private static boolean match(Field column, Out o) {
        return column.getName().equals(o.getHeader()) || (o.getExpression().getField() != null && column.getName().equals(o.getExpression().getField().getName()));
    }

    /**
     * The attribute this entity exposes under exactly this name.
     *
     * <p>Deliberately narrower than {@code Entity#getAttribute}, which also matches an attribute's
     * physical {@code column} override. That override is how the model maps its own name onto the
     * database's; it is not a second name the query may use. Rendering is unaffected — the
     * renderers go through {@code LinkResolver.getDialectColumn}, which is already name-only.
     */
    private static Optional<ai.koryki.catalog.domain.Attribute> attribute(
            ai.koryki.catalog.domain.Entity entity, String name) {
        return entity.getAttributes().stream().filter(a -> a.getName().equals(name)).findFirst();
    }

    /** The names this entity exposes — the only candidates worth offering for one of its columns. */
    private static List<String> attributeNames(ai.koryki.catalog.domain.Entity entity) {
        return entity.getAttributes().stream()
                .map(ai.koryki.catalog.domain.Attribute::getName).toList();
    }

    /**
     * What to write instead of a column name this entity does not expose.
     *
     * <p>Two different questions, and only the second is guesswork. If the name is the
     * <em>physical</em> spelling of an attribute — its {@code column} override — then nothing needs
     * to be inferred: the model itself says which attribute owns that column, and that one name is
     * the answer. String distance cannot reach it, because a model may translate as well as rename,
     * and {@code order_id} resembles {@code bestell_id} not at all.
     *
     * <p>Only when the name maps to no column at all is it treated as a typo and measured against
     * the entity's attributes.
     */
    private static List<String> columnSuggestions(ai.koryki.catalog.domain.Entity entity, String name) {
        Optional<ai.koryki.catalog.domain.Attribute> physical = entity.getAttribute(name);
        if (physical.isPresent()) {
            return List.of(physical.get().getName());
        }
        return Suggest.closest(name, attributeNames(entity));
    }

    private List<String> entityNames() {
        return resolver.getModel().getEntities().stream()
                .map(ai.koryki.catalog.domain.Entity::getName).toList();
    }

    /** The aliases bound where this field is written — see {@link IQLVisibilityContext#aliases()}. */
    private java.util.Set<String> aliases() {
        IQLVisibilityContext scope = scopes.isEmpty() ? rootVisibility : scopes.peek();
        return scope == null ? java.util.Set.of() : scope.aliases();
    }

    /**
     * The names a field may use to reach into a block: whatever {@link #match} accepts.
     *
     * <p>Both spellings go in, because both resolve — the header a column was given, and the name
     * of the field behind it when it has one.
     */
    private static List<String> headerNames(List<Out> outs) {
        List<String> names = new ArrayList<>();
        for (Out o : outs) {
            if (o.getHeader() != null) {
                names.add(o.getHeader());
            }
            if (o.getExpression() != null && o.getExpression().getField() != null) {
                names.add(o.getExpression().getField().getName());
            }
        }
        return names;
    }
}
