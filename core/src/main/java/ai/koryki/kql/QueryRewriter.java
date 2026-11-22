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
package ai.koryki.kql;

import ai.koryki.antlr.Range;

/**
 * Turns a query that uses an extension of KQL into plain KQL, before the transpiler reads it.
 *
 * <p>{@code agg(alias.measure)} of the measures project is one: it is no function of any database,
 * and a rewriter in front of the transpiler makes it into blocks that are. It is attached to the
 * {@link ai.koryki.iql.LinkResolver} of the catalog it belongs to ({@link
 * ai.koryki.iql.LinkResolver#queryRewriter(QueryRewriter)}), so that every way into the transpiler
 * -- the generator, the engine, the chart rules -- applies it without knowing it exists.
 *
 * <p>What this costs, and what the {@link RangeMap} pays back: validation and rendering see the
 * <em>rewritten</em> text, so the {@link Range} of a violation or exception points into text the
 * user never wrote. The transpiler carries those ranges back to the query that was asked, through
 * the map the rewriter returns.
 */
@FunctionalInterface
public interface QueryRewriter {

    /** Rewrites nothing: the default of a resolver. */
    QueryRewriter NONE = kql -> Rewrite.unchanged(kql);

    /**
     * @param kql the query as the user wrote it
     * @throws ai.koryki.iql.validate.ValidateException when the query is refused; the violations
     *     carry ranges in {@code kql} itself, not in anything rewritten
     */
    Rewrite rewrite(String kql);

    /** The range of the original for a range of the rewritten query. */
    @FunctionalInterface
    interface RangeMap {
        Range toOriginal(Range range);
    }

    /**
     * @param kql the plain KQL to transpile
     * @param map where its parts come from in the original; {@code null} when nothing was rewritten
     */
    record Rewrite(String kql, RangeMap map) {

        public static Rewrite unchanged(String kql) {
            return new Rewrite(kql, null);
        }

        public boolean changed() {
            return map != null;
        }
    }
}
