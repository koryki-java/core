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
package ai.koryki.iql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.kql.DictionaryTranslator;
import ai.koryki.kql.TableDictionary;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.iql.query.JoinColumns;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LinkResolver {

    private final Locale locale;
    private final Schema db;
    private final Model model;

    private Map<Column, TypeDescriptor> columnToTypedescriptor = new HashMap<>();


    private final Map<String, Link> linkMap;
    private final boolean alignedOnly;
    private final boolean qualifiedOnly;
    private final boolean strict;

    public LinkResolver(Locale locale, Schema db, Model model) {
        this(locale, db, model, false);
    }

    public LinkResolver(Locale locale, Schema db, Model model, boolean strict) {
        this(locale, db, model, false, false, strict);
    }

    public LinkResolver(Locale locale, Schema db, Model model, boolean alignedOnly, boolean qualifiedOnly) {
        this(locale, db, model, alignedOnly, qualifiedOnly, false);
    }

    public LinkResolver(Locale locale, Schema db, Model model, boolean alignedOnly, boolean qualifiedOnly, boolean strict) {
        this.locale = locale;
        this.db = db;
        this.model = model;
        this.linkMap = model.getLinks().stream().collect(Collectors.toMap(Link::getName, Function.identity()));
        this.alignedOnly = alignedOnly;
        this.qualifiedOnly = qualifiedOnly;
        this.strict = strict;

         TypeDescriptorParser parser = new TypeDescriptorParser() {
        };

        db.getTables().forEach(t -> {
            t.getColumns().forEach(c -> {
                TypeDescriptor d = parser.parse(c);
                columnToTypedescriptor.put(c, d);
            });
        });
    }

    private List<Relation> listRelations(String crit, String start, String end, boolean typed, boolean reverse) {

        String startTable = getDialectTable(start).orElseThrow(() -> new KorykiaiException("unknown start entity: " + start));
        String endTable = getDialectTable(end).orElseThrow(() -> new KorykiaiException("unknown end entity: " + end));
        String first = reverse ? endTable : startTable;
        String second = reverse ? startTable : endTable;

        if (typed) {
            return db.linkRelations(first, second, crit);
        }
        // untyped lookup must not satisfy an explicitly qualified link in strict mode
        return strict && crit != null ? Collections.emptyList() : db.linkRelations(first, second);
    }

    private boolean compareStart(String start, Relation relation) {
        return relation.getStartTable().equals(
                getDialectTable(start).orElseThrow(() -> new KorykiaiException("unknown start entity: " + start)));
    }

    private boolean compareEnd(String end, Relation relation) {
        return relation.getEndTable().equals(
                getDialectTable(end).orElseThrow(() -> new KorykiaiException("unknown end entity: " + end)));
    }

    public boolean isEntity(String entity) {
        return model.getEntity(entity).isPresent();
    }

    public boolean isInverse(String link) {

        Link l = linkMap.get(relation(link));
        if (l == null) {
            throw new KorykiaiException("unknown link: " + link);
        }
        return isInverse(l);
    }


    private String relation(String link) {
        Link l = model.getLink(link).orElseThrow(() -> new KorykiaiException("unknown link: " + link));
        return getLink(l);
    }

    private static String getLink(Link l) {
        return /*l.getAliasOf() != null ? l.getAliasOf() :*/ l.getName();
    }

    private static boolean isInverse(Link link) {
        return link.getNature() != null && link.getNature().contains("inverse");
    }

    private String resolveForeignKey(Range range, String startTable, String endTable, String link) {

        if (link == null) {
            return null;
        }

        Link link1 = model.getLink(link).orElse(null);
        if (link1 == null) {
            throw new RangeException(range, "cant resolve link " + link + " " + startTable + " " + endTable);
        }
        String ll = getLink(link1);
        Link v = linkMap.get(ll);
        if (v == null) {
            throw new RangeException(range, "cant resolve link " + link + " " + startTable + " " + endTable);
        }


        List<String> list = v.getRelations();

        if (list == null) {
            if (strict) {
                throw new RangeException(range, "cant resolve link " + link);
            }
            return link;
        }


        String foreignKey = list.stream().filter(l -> {

            Relation r = db.getRelation(l).get();

            if (compareStart(startTable, r) && compareEnd(endTable, r)) {
                return true;
            } else if (isSymmetric(r) && compareStart(endTable, r) && compareEnd(startTable, r)) {
                return true;
            } else {
                return false;
            }
        }).map(l -> db.getRelation(l).get().getName()).findFirst().orElse(null);

        if (strict && foreignKey == null) {
            throw new RangeException(range, "cant resolve link " + link + " " + startTable + " " + endTable);
        }
        return foreignKey;
    }

    public boolean isSymmetric(Relation r) {
        return r.isSymmetric();
    }

    public Optional<String> findLink(Range range, String startTable, String endTable) {

        return findLink(range, startTable, endTable, null);
    }


    public Optional<String> findLink(Range range, String startTable, String endTable, String relation) {

        return findRelation(range, startTable, endTable, relation).map(x -> toLink(x.getName()));
    }

    /**
     * The relation joining two tables, oriented the way it was asked for.
     *
     * <p>The search runs in both directions, but what comes back always describes {@code startTable}
     * with its {@code startColumns} — callers can attach them to the alias they named first without
     * checking which way the relation is declared. See {@link #oriented}. The one case table names
     * cannot decide is a self-join, where direction comes from the link's declared nature instead.
     */
    public Optional<Relation> findRelation(Range range, String startTable, String endTable, String link) {

        String start = Identifier.normal(Identifier.lowercase, startTable);
        String end = Identifier.normal(Identifier.lowercase, endTable);

        String foreignKey = resolveForeignKey(range, startTable, endTable, link);


        Relation d = checkSingleR(range, start, end, foreignKey, listRelations(foreignKey, start, end, true, false), true, false);
        if (d != null) {
            return Optional.of(d);
        }

        if (!alignedOnly) {
            d = checkSingleR(range, start, end, foreignKey, listRelations(foreignKey, start, end, true, true), true, true);
            if (d != null) {
                return Optional.of(oriented(d, start));
            }
        }

        if (!qualifiedOnly) {

            d = checkSingleR(range, start, end, foreignKey, listRelations(foreignKey, start, end, false, false), false, false);
            if (d != null) {
                return Optional.of(d);
            }

            if (!alignedOnly) {
                d = checkSingleR(range, start, end, foreignKey, listRelations(foreignKey, start, end, false, true), false, true);
                if (d != null) {

                    //toLink(d);
                    return Optional.of(oriented(d, start));
                }
            }
        }


        return Optional.empty();
    }

    /**
     * The relation turned round to face the way it was asked for.
     *
     * <p>The two branches above search with the tables swapped, so they can match a relation whose
     * declared start is the caller's end. What comes back out of the schema still describes itself
     * in its own direction, and every caller pairs {@code getStartColumns()} with the side it asked
     * about first — so a reverse match handed back untouched attaches the wrong table's columns to
     * the wrong alias.
     *
     * <p>Invisible almost everywhere, because a foreign key usually carries the name of the column
     * it points at: 26 of Northwind's 34 relations have identical column lists on both sides, and
     * for those a swap and no swap render the same SQL. {@code fk_orders_shippers} does not —
     * {@code orders.ship_via} points at {@code shippers.shipper_id} — and asking for
     * {@code shippers → orders} produced {@code <shippers>.ship_via}, which is not a column of that
     * table.
     *
     * <p>Turning it here rather than at each caller is what makes the result independent of how it
     * was found: ask for {@code (A, B)} and the start columns belong to {@code A}, whatever
     * direction the search took to get there. That is what the callers already assume, and what
     * {@link #linksBetween} promises on their behalf.
     *
     * @param start the entity the caller asked about first, already lowercased
     */
    private Relation oriented(Relation relation, String start) {

        String startTable = getDialectTable(start).orElse(start);
        if (startTable.equals(relation.getStartTable())) {
            return relation;
        }

        // A copy: this instance is the schema's own and is handed to every other query.
        Relation flipped = Schema.deepCopy(relation);
        flipped.setStartTable(relation.getEndTable());
        flipped.setEndTable(relation.getStartTable());
        flipped.setStartColumns(relation.getEndColumns());
        flipped.setEndColumns(relation.getStartColumns());
        return flipped;
    }

    private String toLink(String fk) {
        return linkMap.entrySet().stream()
                .filter(entry -> entry.getValue().getRelations().contains(fk))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new KorykiaiException("no link references foreign key: " + fk));
    }

    private Relation checkSingleR(Range range, String startTable, String endTable, String foreignKey, List<Relation> check, boolean strict, boolean symmetricOnly) {

        Relation d = check.size() == 1 ? check.get(0) : null;
        if (d != null) {
            if (!symmetricOnly || isSymmetric(d)) {
                return d;
            }
        } else if (strict && check.size() > 1) {
            throw new RangeException(range, "must not find more than one foreignKey: " + startTable + " " + endTable + " " + foreignKey);
        }
        return null;
    }

    /**
     * Builds the relation an explicitly written join denotes, instead of looking one up by name.
     *
     * <p>{@code VIA name} and {@code [a=x]} answer the same question — which columns are compared —
     * and the catalog answers it with a {@link Relation} carrying two ordered column lists. Writing
     * the columns out fills that same shape directly, so everything downstream, above all the ON
     * rendering, cannot tell the two apart. Nothing is looked up here: what the author wrote is the
     * criterion, and searching for one would only find a different answer.
     *
     * <p>Names are translated from the model to the dialect, exactly as a catalog relation already
     * carries them — the author writes the model's (possibly localized) attribute names, while the
     * ON clause needs the physical columns.
     *
     * @throws RangeException when a column does not exist on the entity of its own side, which is
     *                        also how {@code [a=x]} written the wrong way round surfaces
     */
    public Relation relationFor(Range range, String startEntity, String endEntity, JoinColumns columns) {
        Relation r = new Relation();
        r.setStartTable(getDialectTable(startEntity)
                .orElseThrow(() -> new KorykiaiException("unknown start entity: " + startEntity)));
        r.setEndTable(getDialectTable(endEntity)
                .orElseThrow(() -> new KorykiaiException("unknown end entity: " + endEntity)));
        r.setStartColumns(dialectColumns(range, startEntity, columns.left()));
        r.setEndColumns(dialectColumns(range, endEntity, columns.right()));
        return r;
    }

    /**
     * Model attribute names translated to their dialect columns.
     *
     * <p>An unknown name is passed through rather than raising: {@code SchemaValidator} reports it as
     * a positioned {@code schema.unknown-column} violation, together with every other one in the
     * query, and a query carrying such a violation never reaches a database. Raising here happened
     * during rule application, before the violations were collected, so the first bad column ended
     * the run and hid the rest.
     */
    private List<String> dialectColumns(Range range, String entity, List<String> attributes) {
        return attributes.stream()
                .map(a -> getDialectColumn(entity, a).orElse(a))
                .toList();
    }

    public boolean isStrict() {
        return strict;
    }

    public Model getModel() {
        return model;
    }

    public Schema getSchema() {
        return db;
    }

    public Locale getLocale() {
        return locale;
    }

    public Map<String, Link> getLinkMap() {
        return linkMap;
    }

    public Optional<String> getDialectTable(String entity) {
        return model.getEntity(entity).map(LinkResolver::getDialectTable);
    }

    public Optional<String> getDialectColumn(String entity, String attribute) {
        return model.getEntity(entity).
                flatMap(e -> e.getAttributes().stream().filter(a -> a.getName().equals(attribute)).findFirst())
                .map(LinkResolver::getDialectColumn);
    }

    /**
     * The model's name for a physical column of an entity — the inverse of
     * {@link #getDialectColumn(String, String)}.
     *
     * <p>Needed wherever a rewrite rule reads a name from the <em>schema</em> and puts it into the
     * <em>query</em>: a primary key read off {@code Table}, a foreign key read off
     * {@code Relation}. The two vocabularies coincide only while an attribute declares no
     * {@code column} override, which is why this went unnoticed — under the German northwind model
     * every attribute declares one, and a rule that wrote {@code order_id} where the model says
     * {@code bestell_id} produced a field the query language does not have.
     *
     * <p>Empty when the entity or the column is unknown here; callers keep the column name, which
     * is the best name available and what they used throughout before.
     */
    public Optional<String> getModelAttribute(String entity, String column) {
        return model.getEntity(entity)
                .flatMap(e -> e.getAttributes().stream()
                        .filter(a -> getDialectColumn(a).equals(column)).findFirst())
                .map(Attribute::getName);
    }

    public static DictionaryTranslator dictionary(Model from, Model to) {

        Map<String, TableDictionary> toSchema = from.getEntities().stream().collect(Collectors.toMap(Entity::getName, (e) -> {

            TableDictionary dictionary = new TableDictionary();

            String dialectTable = getDialectTable(e);

            Entity te = to.getEntities().stream()
                    .filter(t -> getDialectTable(t).equals(dialectTable)).findFirst()
                    .orElseThrow(() -> new RuntimeException("No value present " + dialectTable));
            dictionary.setName(te.getName());

            dictionary.setColumns(e.getAttributes().stream().collect(Collectors.toMap(Attribute::getName, a -> {
                String dialectColumn = getDialectColumn(a);
                return te.getAttributes().stream()
                        .filter(aa -> getDialectColumn(aa).equals(dialectColumn)).findFirst()
                        .orElseThrow(() -> new RuntimeException("No value present " + dialectColumn))
                        .getName();
            })));
            return dictionary;
        }));
        Map<String, String> toLink = from.getLinks().stream().collect(Collectors.toMap(Link::getName, (l) -> {
            String canonical = l.getCanonical() != null ? l.getCanonical() : l.getName();
            return to.getLinks().stream()
                    .filter(tl -> (tl.getCanonical() != null ? tl.getCanonical() : tl.getName()).equals(canonical))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No value present " + canonical))
                    .getName();

        }));
        return new DictionaryTranslator(toLink, toSchema);
    }

    public static String getDialectTable(Entity entity) {
        return entity.getTable() != null ? entity.getTable() : entity.getName();
    }

    public static String getDialectColumn(Attribute attribute) {
        return attribute.getColumn() != null ? attribute.getColumn() : attribute.getName();
    }

    /**
     * Returns all link names that are reachable from {@code entity} as the source side.
     * <p>
     * A link is included when at least one of its canonical FK relations has the entity's
     * dialect table as {@code startTable} (forward link) or {@code endTable} (inverse link).
     * Symmetric relations count in both directions.
     *
     * @param entity model-level entity name (as used in KQL FIND clauses)
     * @return sorted list of link names valid for the given entity
     */
    public List<String> linksFrom(String entity) {
        String dialectTable = getDialectTable(entity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + entity));
        return linkMap.values().stream()
                .filter(link -> isLinkEndpoint(dialectTable, link, true))
                .map(Link::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns all link names that can reach {@code entity} as the target side -
     * the mirror of {@link #linksFrom(String)}.
     *
     * @param entity model-level entity name (as used in KQL FIND clauses)
     * @return sorted list of link names valid toward the given entity
     */
    public List<String> linksTo(String entity) {
        String dialectTable = getDialectTable(entity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + entity));
        return linkMap.values().stream()
                .filter(link -> isLinkEndpoint(dialectTable, link, false))
                .map(Link::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns all link names that connect {@code startEntity} to {@code endEntity}.
     * <p>
     * Each candidate link is tested with the same {@link #findRelation} resolution the
     * query engine uses at execution time, so a link is listed here exactly when a
     * {@code startEntity VIA link endEntity} clause would resolve - suggestions can
     * never drift from executor behavior. Links whose resolution is ambiguous (more
     * than one matching foreign key) are excluded, matching the executor's rejection.
     *
     * @param startEntity model-level entity name of the source side
     * @param endEntity   model-level entity name of the target side
     * @return sorted list of link names valid between the two entities
     */
    public List<String> linksBetween(String startEntity, String endEntity) {
        getDialectTable(startEntity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + startEntity));
        getDialectTable(endEntity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + endEntity));
        // findRelation reports its diagnostics against a range; this lookup has no query text behind
        // it, so it passes the smallest legal one. Positions are 1-based, hence (1,1) and not (0,0).
        Range range = new Range(1, 1, 1, 1);
        return linkMap.keySet().stream()
                .filter(name -> {
                    try {
                        return findRelation(range, startEntity, endEntity, name).isPresent();
                    } catch (RuntimeException e) {
                        return false;
                    }
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns the names of all entities reachable from {@code entity} via any link -
     * the candidate targets for a join. An entity is included when at least one link's
     * canonical FK relation leads from {@code entity}'s dialect table to that entity's
     * table (inverse links read their relations in the opposite direction, symmetric
     * relations count both ways). Self-links include the entity itself.
     *
     * @param entity model-level entity name (as used in KQL FIND clauses)
     * @return sorted distinct list of reachable entity names
     */
    public List<String> linkedEntities(String entity) {
        String dialectTable = getDialectTable(entity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + entity));
        Set<String> result = new TreeSet<>();
        for (Link link : linkMap.values()) {
            collectLinkTargets(link, dialectTable, result);
        }
        return new ArrayList<>(result);
    }

    /**
     * Returns the names of all entities reachable via the given link criterion,
     * optionally restricted to journeys starting at {@code fromEntity} - the
     * candidate sources after {@code from? VIA criterion} in a KQL link clause.
     *
     * @param linkName   model-level (localized) link criterion name
     * @param fromEntity optional entity name of the from side; {@code null} for any
     * @return sorted distinct list of reachable entity names
     */
    public List<String> linkTargets(String linkName, String fromEntity) {
        Link link = linkMap.get(linkName);
        if (link == null) {
            throw new KorykiaiException("unknown link: " + linkName);
        }
        String fromTable = fromEntity == null ? null : getDialectTable(fromEntity)
                .orElseThrow(() -> new KorykiaiException("unknown entity: " + fromEntity));
        Set<String> result = new TreeSet<>();
        collectLinkTargets(link, fromTable, result);
        return new ArrayList<>(result);
    }

    /**
     * Adds the entity names reachable over {@code link} to {@code result}. With a
     * non-null {@code fromTable}, only relations whose from side (inverse-aware)
     * matches it contribute; with {@code null}, every relation of the link does.
     */
    private void collectLinkTargets(Link link, String fromTable, Set<String> result) {
        String canonical = getLink(link);
        Link canonicalLink = linkMap.get(canonical);
        if (canonicalLink == null || canonicalLink.getRelations() == null) return;
        Map<String, String> tableToEntity = model.getEntities().stream()
                .collect(Collectors.toMap(LinkResolver::getDialectTable, Entity::getName, (a, b) -> a));
        boolean inverse = isInverse(link);
        for (String relationName : canonicalLink.getRelations()) {
            Relation relation = db.getRelation(relationName).orElse(null);
            if (relation == null) continue;
            String from = inverse ? relation.getEndTable() : relation.getStartTable();
            String to = inverse ? relation.getStartTable() : relation.getEndTable();
            if (fromTable == null || from.equals(fromTable)) {
                String target = tableToEntity.get(to);
                if (target != null) result.add(target);
            }
            if (relation.isSymmetric() && (fromTable == null || to.equals(fromTable))) {
                String target = tableToEntity.get(from);
                if (target != null) result.add(target);
            }
        }
    }

    private boolean isLinkEndpoint(String dialectTable, Link link, boolean asSource) {
        String canonical = getLink(link);
        Link canonicalLink = linkMap.get(canonical);
        if (canonicalLink == null) return false;
        List<String> rels = canonicalLink.getRelations();
        if (rels == null || rels.isEmpty()) return false;
        // an inverse link reads its FK relations in the opposite direction
        boolean fromStartSide = asSource != isInverse(link);
        return rels.stream()
                .map(name -> db.getRelation(name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(rel -> fromStartSide
                        ? rel.getStartTable().equals(dialectTable) || (rel.isSymmetric() && rel.getEndTable().equals(dialectTable))
                        : rel.getEndTable().equals(dialectTable) || (rel.isSymmetric() && rel.getStartTable().equals(dialectTable)));
    }

    public TypeDescriptor getTypeDescriptor(Column column) {
        return columnToTypedescriptor.get(column);
    }
}
