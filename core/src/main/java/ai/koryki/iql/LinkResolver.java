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

import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.kql.DictionaryTranslator;
import ai.koryki.kql.TableDictionary;
import ai.koryki.scaffold.domain.Attribute;
import ai.koryki.scaffold.domain.Entity;
import ai.koryki.scaffold.domain.Link;
import ai.koryki.scaffold.domain.Model;
import ai.koryki.scaffold.schema.Relation;
import ai.koryki.scaffold.schema.Schema;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LinkResolver {

    private Locale locale;
    private Schema db;
    private Model model;

    private Map<String, Link> linkMap;
    private boolean alignedOnly;
    private boolean qualifiedOnly;
    private boolean strict;

    public LinkResolver(Locale locale, Schema db, Model model) {
        this(locale, db, model, false, false);
    }

    public LinkResolver(Locale locale, Schema db, Model model, boolean alignedOnly, boolean qualifiedOnly) {
        this.locale = locale;
        this.db = db;
        this.model = model;
        this.linkMap = model.getLinks().stream().collect(Collectors.toMap(Link::getName, Function.identity()));
        this.alignedOnly = alignedOnly;
        this.qualifiedOnly = qualifiedOnly;
    }

    private List<Relation> _listTypedAligned(String crit, String start, String end) {

        List<Relation> rl = db.linkRelations(model.getTable(start), model.getTable(end), crit);
        return rl;
    }

    private List<Relation> _listTypedReverse(String crit, String start, String end) {

        List<Relation> rl =db.linkRelations(model.getTable(end), model.getTable(start), crit);
        return rl;
    }

    private List<Relation> _listUntypedAligned(String crit, String start, String end) {

        List<Relation> rl =db.linkRelations(model.getTable(start), model.getTable(end));
        return strict && crit != null ? Collections.emptyList() : rl;
    }

    private List<Relation> _listUntypedReverse(String crit, String start, String end) {

        List<Relation> rl =db.linkRelations(model.getTable(end), model.getTable(start));
        return strict && crit != null ? Collections.emptyList() : rl;
    }

    private boolean compareStart(String s, Relation relation) {
        return relation.getStartTable().equals(model.getTable(s));
    }

    private boolean compareEnd(String e, Relation relation) {
        return relation.getEndTable().equals(model.getTable(e));
    }

    public boolean isEntity(String entity) {
        return model.getEntity(entity).isPresent();
    }

    public boolean isInverse(String link) {


        String l = relation(link);
        return linkMap.entrySet().stream().filter(e -> e.getValue().getName().equals(l)).findFirst().map(LinkResolver::isInverse).orElseThrow(() -> new RuntimeException(link));
    }


    private String relation(String link) {
        return model.getLink(link).get().getRelation() != null ? model.getLink(link).get().getRelation() : model.getLink(link).get().getName();
    }

    private static boolean isInverse(Map.Entry<String, Link> e) {
        return e.getValue().getNature() != null && e.getValue().getNature().contains("inverse");
    }

    private String resolveForeignKey(Range range, String startTable, String endTable, String link) {

        if (link == null) {
            return null;
        }

        Link link1 = model.getLink(link).orElse(null);
        if (link1 == null) {
            throw new RangeException(range, "c'ant resolve link " + link + " " + startTable + " " + endTable);
        }
        String ll = link1.getRelation() != null ? link1.getRelation() : link1.getName();
        Link v = linkMap.get(ll);
        if (v == null) {
            throw new RangeException(range, "c'ant resolve link " + link + " " + startTable + " " + endTable);
        }


        List<String> list = v.getRelations();

        if (list == null) {
            if (strict) {
                throw new RangeException(range, "c'ant resolve link " + link);
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
            throw new RangeException(range, "c'ant resolve link " + link + " " + startTable + " " + endTable);
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


        Relation d = checkSingleR(range, start, end, foreignKey, _listTypedAligned(foreignKey, start, end), true, false);
        if (d != null) {
            return Optional.of(d);
        }

        if (!alignedOnly) {
            d = checkSingleR(range, start, end, foreignKey, _listTypedReverse(foreignKey, start, end), true, true);
            if (d != null) {
                return Optional.of(d);
            }
        }

        if (!qualifiedOnly) {

            d = checkSingleR(range, start, end, foreignKey, _listUntypedAligned(foreignKey, start, end), false, false);
            if (d != null) {
                return Optional.of(d);
            }

            if (!alignedOnly) {
                d = checkSingleR(range, start, end, foreignKey, _listUntypedReverse(foreignKey, start, end), false, true);
                if (d != null) {

                    //toLink(d);
                    return Optional.of(d);
                }
            }
        }


        return Optional.empty();
    }

    private String toLink(String fk) {
        String link = linkMap.entrySet().stream().filter((entry) -> entry.getValue().getRelations().contains(fk)).map(entry -> entry.getKey()).findFirst().get();
        return link;
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

    public void setStrict(boolean strict) {
        this.strict = strict;
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

    // approved
    public Optional<String> getDialectTable(String entity) {
        return model.getEntity(entity).map(Entity::getDialectTable);
    }

    // approved
    public Optional<String> getDialectColumn(String entity, String attribute) {
        return model.getEntity(entity).
                flatMap(e -> e.getAttributes().stream().filter(a -> a.getName().equals(attribute)).findFirst())
                .map(Attribute::getDialectColumn);
    }

    // approved
    public static DictionaryTranslator dictionary(Model from, Model to) {

        Map<String, TableDictionary> toSchema = from.getEntities().stream().collect(Collectors.toMap(Entity::getName, (e) -> {

            TableDictionary dictionary = new TableDictionary();

            String dialectTable = e.getDialectTable();

            Entity te = to.getEntities().stream()
                    .filter(t -> t.getDialectTable().equals(dialectTable)).findFirst()
                    .orElseThrow(() -> new RuntimeException("No value present " + dialectTable));
            dictionary.setName(te.getName());

            dictionary.setColumns(e.getAttributes().stream().collect(Collectors.toMap(Attribute::getName, a -> {
                String dialectColumn = a.getDialectColumn();
                return te.getAttributes().stream()
                        .filter(aa -> aa.getDialectColumn().equals(dialectColumn)).findFirst()
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

//    public DictionaryTranslator reverseDictionary() {
//        DictionaryTranslator d = dictionary();
//        return new DictionaryTranslator(swapMap(d.getLinkDictionary()), swapDictionary(d.getSchemaDictionary()));
//    }
//
//    private static Map<String, TableDictionary> swapDictionary(Map<String, TableDictionary> map) {
//
//        Map<String, TableDictionary> s = new HashMap<>();
//        map.forEach((k, v) -> {
//            String n = v.getName();
//
//            TableDictionary d = new TableDictionary();
//            d.setName(k);
//            d.setColumns(swapMap(v.getColumns()));
//            s.put(n, d);
//
//        });
//        return s;
//    }
//
//    private static Map<String, String> swapMap(Map<String, String> map) {
//        Map<String, String> s = new HashMap<>();
//        map.forEach((k, v) -> s.put(v, k));
//        return s;
//    }

}
