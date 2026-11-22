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
package ai.koryki.catalog;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The default links of a model, checked against the schema, and the pairs of tables that have no
 * default although a query could not tell their links apart.
 *
 * <p>A pair is two tables, in no order: a link over {@code emp -> dept} and one over {@code dept ->
 * emp} are links of the same pair. What is refused: a default link that names a relation the schema
 * lacks, one over a relation with a condition, and a second default on a pair.
 */
public final class LinkDefaults {

    private final Schema schema;
    private final Model model;

    public LinkDefaults(Schema schema, Model model) {
        this.schema = schema;
        this.model = model;
    }

    /** The links of the model by pair, in model order. */
    private Map<String, List<Link>> pairs;

    private Map<String, List<Link>> byPair() {
        if (pairs != null) {
            return pairs;
        }
        Map<String, List<Link>> pairs = new LinkedHashMap<>();
        for (Link link : model.getLinks()) {
            for (String pair : pairsOf(link)) {
                pairs.computeIfAbsent(pair, k -> new ArrayList<>()).add(link);
            }
        }
        this.pairs = pairs;
        return pairs;
    }

    private java.util.Set<String> pairsOf(Link link) {
        java.util.Set<String> pairs = new TreeSet<>();
        for (String name : link.getRelations() == null ? List.<String>of() : link.getRelations()) {
            schema.getRelation(name)
                    .ifPresent(r -> pairs.add(pair(r.getStartTable(), r.getEndTable())));
        }
        return pairs;
    }

    private static String pair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + " / " + b : b + " / " + a;
    }

    /** @throws KorykiaiException for the first default that is not allowed */
    public void check() {
        for (Link link : model.getLinks()) {
            if (!link.defaulted()) {
                continue;
            }
            for (String name : link.getRelations()) {
                Relation r =
                        schema.getRelation(name)
                                .orElseThrow(
                                        () ->
                                                new KorykiaiException(
                                                        "default link "
                                                                + link.getName()
                                                                + ": unknown relation "
                                                                + name));
                if (r.getCondition() != null) {
                    throw new KorykiaiException(
                            "default link "
                                    + link.getName()
                                    + ": relation "
                                    + name
                                    + " has a condition, and a join like that is always written"
                                    + " with VIA");
                }
            }
        }
        for (Map.Entry<String, List<Link>> e : byPair().entrySet()) {
            List<String> defaults =
                    e.getValue().stream().filter(Link::defaulted).map(Link::getName).toList();
            if (defaults.size() > 1) {
                throw new KorykiaiException(
                        "more than one default link between "
                                + e.getKey()
                                + ": "
                                + String.join(", ", defaults));
            }
        }
    }

    /**
     * The pairs with more than one link and no default, with the links: a query that joins them
     * without {@code VIA} cannot be resolved. A list for tooling and a warning to the author, not an
     * error: a catalog may leave such a pair to be written out every time.
     */
    public Map<String, List<String>> pairsWithoutDefault() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Link>> e : byPair().entrySet()) {
            List<Link> links = e.getValue();
            if (links.size() > 1 && links.stream().noneMatch(Link::defaulted)) {
                out.put(e.getKey(), links.stream().map(Link::getName).toList());
            }
        }
        return out;
    }

    /** The default link of the pair, if one is flagged. */
    public java.util.Optional<String> defaultOf(String tableA, String tableB) {
        List<Link> links = byPair().getOrDefault(pair(tableA, tableB), List.of());
        return links.stream().filter(Link::defaulted).map(Link::getName).findFirst();
    }
}
