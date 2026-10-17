# Naming the parts of a nested column: members and notions

The schema (`db.json`) is the same in every language, so the parts of a nested column have the keys
the database has: `street`, `geo`, `lat`. A query is written in the language of a model, and a model
may call them something else. This page says where a model does that, what the name does, and what is
checked. The fields of `Member` and `Notion` are listed in
[`SEMANTIC_LAYER.md`](./SEMANTIC_LAYER.md); how a column is described in the schema is in
[`NESTED.md`](./NESTED.md#describing-a-nested-column-in-the-catalog).

## Two places to say it

**On the attribute**, for a concept the column has of its own. `members` is keyed by the physical key
of the entry:

```json
{
  "name": "address",
  "column": "address",
  "members": {
    "street": { "name": "strasse" },
    "geo": {
      "name": "position",
      "label": "Position",
      "members": {
        "lat": { "name": "breitengrad", "label": "Breite" },
        "lon": { "name": "laengengrad", "label": "Länge" }
      }
    }
  }
}
```

With this, `t.address@position@breitengrad` reaches `address.geo.lat`.

**In a notion**, for a concept the schema has by name (`Schema.concepts`) and that columns refer to
with `conceptRef`. It says the same once for every column that refers to it:

```json
"notions": {
  "point": {
    "label": "Position",
    "members": {
      "lat": { "name": "breitengrad", "label": "Breite" },
      "lon": { "name": "laengengrad", "label": "Länge" }
    }
  }
}
```

In the `shared` catalog (`check_shared`) the columns `home` and `work`, and the elements of `route`,
are all the concept `point`; the German model names the fields of `point` once, and
`g.heim@breitengrad`, `g.arbeit@laengengrad` and `g.strecke@2@breitengrad` all work.

**An attribute that has members of its own replaces the notion's as a whole, not key by key.** Say one
name on the attribute and the notion's other names no longer apply to that column; say all of them.
(The audit holds such an attribute to every part.)

## The keys

A key is the key of the entry in the concept:

| the part | its key |
|---|---|
| a field of a struct | its name in the schema |
| the elements of a list | `element` |
| the keys and the values of a map | `key`, `value` |

`members` nest: the fields of a struct held in a list are `members` of `element`.

```json
"members": { "element": { "members": { "sku": { "name": "artikel" } } } }
```

Only the **fields of a struct** are written by name in a query, so only they can have a `name`. A
member of `element`, `key` or `value` carries wording (a label, a comment, a description) and, below
it, the names of the fields inside; a `name` on it has no effect. A JSON column whose content the
catalog declares has its fields named the same way.

## What a name does

- **A selector can be written with it.** A rule translates the names of a selector to the keys of the
  schema before the query is validated and rendered, so every dialect sees the keys. The match is
  exact, then without regard to case. The step keeps the name as it was written
  (`Selector.written()`) and says the entry it goes to (`Selector.key()`).
- **It names the fields of an `unnest` row too.** The alias of `unnest(t.items) i` has the fields of the
  element as columns, and `i.artikel` under a model that calls `sku` `artikel` is `i.sku`; headers take the
  label of the member.
- **It works through blocks and `EXISTS`.** The names of a field of a block are those of the column the
  block projects; an output that is computed (a function, a selection) has none.
- **The key still works** where no other field has taken it as a name: `t.address@street` is accepted
  under the model above, as it was before the model named anything.
- **An error is said in the names.** `t.address@hoehe` under the German model is refused with "its
  fields are strasse, position, phones" and the type as `STRUCT(strasse:TEXT,position:STRUCT(...))`.
  A selection from a lambda parameter or a computed value has no column to take names from and keeps
  the keys.
- **A query moves between models.** `KQLFormatter` with `LinkResolver.dictionary(from, to, schema)`
  writes the names of the other model, where either model names the field, so an English query whose
  model names nothing becomes `heim@breitengrad` in German.
- **The wording heads the column.** The Investigator of `koryki-derivation` heads a result column from
  `t.address@position@breitengrad` with the label of the member, else the label of the concept in the
  schema, else the step as written.

## What is checked

When a model is read with its schema (`new LinkResolver(...)`), `MemberNames.check()` refuses:

| the fault | the message begins |
|---|---|
| a notion of a concept the schema does not have | `notion 'x': the schema has no concept of that name; it has …` |
| an attribute with members whose column has no concept | `attribute 'e.a': it names members, but its column has no concept to name them in` |
| a member that is no entry of its concept | `… member 'k' is no entry of its concept; it has …` |
| two fields with the same name in the language, case aside | `… two fields are called 'x' in this language` |
| a blank name | `… has a blank name` |
| members under an entry that is only a type | `… names members, but its entry is a type and has no parts` |

## Writing them

- **Drafting.** `SchemaToModel` writes a model from a schema, and gives each attribute the members, and
  each shared concept the notion, with the label, comment and description the schema has. Those words
  are in the language of the schema; a model of another language says them again.
- **Ingesting.** `SchemaModelBuilder` (the incubator) names a struct field that a query cannot write (a
  capital, a blank, a keyword) as an attribute is named, and keeps the key as the key.
- **The catalog editor** of the demo application has the *Parts* list under a nested attribute and a
  *Notions* tab; both mark what is missing.

## Finding what a translation has left out

`WordingAudit.audit(schema, model)` lists, for each column, each part and each shared concept, the
words the schema has and the model has not (**missing**), or has with the very words of the schema
(**unchanged**, a gap only for a model in another language). The catalogs of the northwind repository
are held to *no word missing*, the catalog view of the demo server lists the gaps of the saved
catalog, and `KorykiCatalog.wordingGaps(locale)` of the springai tools does the same for a language,
with a line in the log when the model of a language is first read and leaves words to the schema.
