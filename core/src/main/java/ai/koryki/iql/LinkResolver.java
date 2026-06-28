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
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeDescriptorParser;

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
        return l.getRelation() != null ? l.getRelation() : l.getName();
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
        String ll = link1.getRelation() != null ? link1.getRelation() : link1.getName();
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
                return Optional.of(d);
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
                    return Optional.of(d);
                }
            }
        }


        return Optional.empty();
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
            String base = l.getBase() != null ? l.getBase() : l.getName();
            return to.getLinks().stream()
                    .filter(tl -> (tl.getBase() != null ? tl.getBase() : tl.getName()).equals(base))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No value present " + base))
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

    public TypeDescriptor getTypeDescriptor(Column column) {
        return columnToTypedescriptor.get(column);
    }
}
