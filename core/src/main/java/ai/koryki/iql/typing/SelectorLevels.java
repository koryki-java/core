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

import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * What a model says of the parts of the column a field stands for: the names of its fields, as
 * {@link MemberNames.Level}. The rule that translates the names of a selector and the validator
 * that explains a failed one need the same answer, so it is found here, once.
 *
 * <p>A field of a table is the attribute of its entity. A field of a block is the output of the
 * block, followed to the column it projects, through any number of blocks; an output that is
 * computed (a function, a selection) has no parts the model could have named.
 */
public final class SelectorLevels {

    private final LinkResolver resolver;
    private final Function<String, Block> blocks;
    private final Function<Source, RowType> rows;

    /**
     * @param blocks the block of an id, or null where the id is no block
     * @param rows what the rows of a list made into rows are, or null where it has none
     */
    public SelectorLevels(
            LinkResolver resolver, Function<String, Block> blocks, Function<Source, RowType> rows) {
        this.resolver = resolver;
        this.blocks = blocks;
        this.rows = rows;
    }

    /** The names of the column the field {@code name} of {@code source} stands for. */
    public Optional<MemberNames.Level> of(Source source, String name) {
        return of(source, name, new HashSet<>());
    }

    private Optional<MemberNames.Level> of(Source source, String name, Set<String> seen) {
        if (source.getCall() != null) {
            // a field of a list made into rows is a field of its element
            RowType type = rows.apply(source);
            if (type == null || type.level() == null) {
                return Optional.empty();
            }
            MemberNames.Level element = type.level();
            return element.physical(name)
                    .or(() -> element.keys().contains(name) ? Optional.of(name) : Optional.empty())
                    .flatMap(element::into);
        }
        Block block = blocks.apply(source.getName());
        if (block != null) {
            if (!seen.add(block.getId())) {
                return Optional.empty();
            }
            Select select = SqlQueryRenderer.select(block.getSet());
            for (Out out : SqlQueryRenderer.collectOut(select)) {
                Expression e = out.getExpression();
                String called =
                        out.getHeader() != null
                                ? out.getHeader()
                                : e != null && e.getField() != null ? e.getField().getName() : null;
                if (name.equals(called)) {
                    if (e.getField() == null || e.getField().getAlias() == null) {
                        return Optional.empty();
                    }
                    Source inner = Visitor.findSourceInSelect(select, e.getField().getAlias());
                    return inner == null
                            ? Optional.empty()
                            : of(inner, e.getField().getName(), seen);
                }
            }
            return Optional.empty();
        }
        Optional<Entity> entity = resolver.getModel().getEntity(source.getName());
        return entity.flatMap(e -> e.getAttribute(name))
                .flatMap(
                        a ->
                                new MemberNames(resolver.getSchema(), resolver.getModel())
                                        .of(entity.get(), a));
    }
}
