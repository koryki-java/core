---
menuTitle: "Nested Values"
parent: "REFERENCE"
order: 6
---

# Nested Values: selectors and lambdas

Lists, structs, maps and JSON are values like any other: a column can hold one, and a query can reach
into it with a **selector** or pass a **lambda** to a function that works on a list. This document
says what KQL promises; the table at the end says which dialect keeps which promise. Where a dialect
cannot, the query is refused with a message, never answered with something close.

## Selectors

A selector follows a value and is written `@`:

| Written | On a list | On a struct | On a map | On JSON |
|---|---|---|---|---|
| `@1`, `@-1` | the element; from the end when negative | the field by position | the value of the integer key | the array position |
| `@2:`, `@:3`, `@1:3` | the list of the elements, both ends included | refused | refused | refused |
| `@name`, `@'a key'` | refused | the field | the value of the text key | the object member |

* Positions count **from 1**, and from the end when negative. There is no position 0; it is refused
  when the query is checked. JSON paths count from 0 in SQL; the rendering shifts them.
* A position outside the list, a key the map does not have, a member JSON does not have: **NULL**,
  not an error. A range is cut to the list (`@-5:` on three elements is all three).
* A struct field is found by the name the type declares, without regard to case.
* A selection is a value: it can be filtered on, compared, passed to a function, and followed by
  another selector (`t.items@1@opts@2`). The type of every step is checked against what it meets.

## Lambdas

`list_transform`, `list_filter` and `list_reduce` take a lambda. Its parameters are named, and used
by that name: `list_transform(t.scores, x -> x * 2)`. A selection from a parameter is the usual
postfix one: `it@price`.

* `list_transform` and `list_filter`: one parameter (the element) or two (element, position from 1).
* `list_reduce`: two parameters (state, element); the first element is the initial state, so a list
  of one gives that element.
* A bare name in a lambda body is its parameter. As a function argument, a bare name outside a lambda
  that declares it is an alias, as in `count(o)`.
* A lambda sees the columns of the query as well as its parameters. A parameter may not be named like
  an alias visible where the lambda stands (`t.price` is a field of the alias, `t@price` a selection
  from the parameter), nor like a parameter of a lambda around it; both are refused when the query is
  checked.
* A NULL list stays NULL.

## What a list answers about itself

| Function | Answers |
|---|---|
| `list_size(l)` | the number of elements, NULL elements included |
| `list_contains(l, v)` or `v IN l` | whether an element equals `v`; true or false for a list that is there |
| `list_sum(l)`, `list_min(l)`, `list_max(l)` | over the elements that are not NULL |
| `list_avg(l)` | the mean of those, as a floating point number |

* A list that is NULL gives NULL; a list with no element left to count (empty, or only NULLs) gives
  NULL for the sum, the mean, the smallest and the largest.
* `x IN l` is the membership of a value in a list column, `x IN (1, 2)` stays the membership in a
  list of literals. The value meets the *elements* of the list, so `1 IN t.tags` over a list of
  texts is refused. Negate with `NOT (x IN l)`.
* A list is not a text: `length(t.tags)` is refused and names `list_size`.
* The list may be anything that is a list, a column or what a lambda made: `list_size(list_filter(
  t.scores, x -> x > 5))`.
* Available where lists are: DuckDB, PostgreSQL, Trino, Snowflake and Oracle. The aggregates of
  Oracle ignore the order a nested table has none of; its `list_avg` is a floating point number.

## Shaping and making lists

| Function | Answers |
|---|---|
| `list_sort(l)`, `list_sort(l, 'desc')` | the list in order; NULL elements come last in either order |
| `list_distinct(l)` | the elements without duplicates and without NULLs, in ascending order |
| `list_concat(a, b)` | `a` followed by `b`; a NULL list counts as empty, the answer is NULL only when both are |
| `list_any(l, x -> test)`, `list_all(l, x -> test)` | whether some, or every, element passes the test; a test that is NULL does not pass; an empty list has no element that passes and every element of it does |
| `list(a, b, ...)` | a list of the values, in the order they are written |
| `list_agg(x)` | an aggregate: the values of the group as a list, NULLs left out, no order promised, NULL where nothing was collected |

* A list from `list_agg` has no order, so one that is shown or compared is sorted:
  `list_sort(list_agg(t.nr))`. On Oracle this is the only thing besides showing it that can be done
  with it: TABLE() cannot read an aggregate in a subquery (ORA-00934), and the refusal says so.
* `list_any` and `list_all` work on a list of structs on every dialect, including Oracle, which
  cannot build a list of objects for `list_filter`.
* An aggregate over a selection, `sum(t.scores@2)`, groups the other items of the query like an
  aggregate over a column does.
* An expression of constants in a query with an aggregate is a key of the GROUP BY on every dialect;
  Oracle refuses a subquery there, so `list_size(list(1, 2, 3))` is written in a query of its own.

## Flatten views: one row per element

A list cannot be grouped by what is inside it: "revenue by sku" needs one row per element. KQL has no
UNNEST; the answer is a generated view per list column. The view is an ordinary table of the catalog,
so it gets an entity, labels, a relation back to its parent, and everything built on them.

`FlattenViews` (module `tools`) reads a `db.json`, and writes the DDL and the catalog entries:

```
./gradlew :tools:flattenViews -Pflatten.args="duckdb db.json model.json check_complex out-dir"
```

A view `<table>_<column>` has the parent's key, `ordinal` (the position in the list, from 1) and one
column per struct field, or `value` for a list of scalars. The relation `<column>_of_<table>` runs from
the view to the parent:

```
FIND check_complex_items i, i VIA items_of_check_complex check_complex t
FETCH t.nr, i.sku, sum(i.qty * i.price) revenue
```

SQL Server, MariaDB and SQLite have no lists, but an array inside a JSON column that the catalog
declares (`.a.b[]: INTEGER`) is flattened the same way, named by its path:

```
./gradlew :tools:flattenViews -Pflatten.args="sqlite db.json model.json check_complex out-dir doc:.a.b"
```

gives `check_complex_doc_a_b` (`json_each`, `JSON_TABLE` or `CROSS APPLY OPENJSON`) with the relation
`doc_a_b_of_check_complex`; the elements are scalars or objects whose members are scalars. A row
whose document has no array there has no elements.

The generator needs a table key. It handles lists of structs and of scalars for DuckDB, PostgreSQL,
Trino, Snowflake and Oracle, and refuses lists of lists and of maps. The complex test beds carry the
three views `check_complex_tags`, `_items` and `_events`. Two differences remain: Snowflake `FLATTEN`
drops NULL elements, and the ordinal of an Oracle nested table follows storage order.

## What the dialects declare

What an engine can do with nested values is declared by the dialect (`SqlDialect.nestedSupport()`),
and a use it cannot express is a violation of the query, with a position, before any SQL is built.
This table is generated from those declarations.

<!-- nested-support:begin (generated: ./gradlew :tools:nestedMatrix) -->

| | list | struct | map | JSON | JSON from the end | lambda over a JSON array | compare a list |
|---|---|---|---|---|---|---|---|
| DuckDB | yes | yes | yes | yes | yes | yes | yes |
| Oracle | yes | yes | no | yes | yes | no | no |
| Snowflake | yes | yes | yes | yes | no | yes | yes |
| SQL Server | no | no | no | yes | no | no list | no list |
| PostgreSQL | yes | yes | no | yes | yes | yes | yes |
| MariaDB | no | no | no | yes | yes | no list | no list |
| SQLite | no | no | no | yes | yes | no list | no list |
| Trino | yes | yes | yes | yes | no | yes | yes |

<!-- nested-support:end -->

## What the dialects do

The details behind the declarations above, by hand: what each engine does with what it accepts. Where
the two disagree, the table above is the declaration and this one is out of date.

| | list | range | struct | map | JSON | JSON from the end | transform, filter | position parameter | reduce |
|---|---|---|---|---|---|---|---|---|---|
| DuckDB | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| PostgreSQL | yes (an array of arrays only by a position in every dimension) | yes | yes | as JSON | yes | yes | yes, not to a list of lists | yes | no |
| Trino | yes | yes | yes | yes | yes | no | yes | transform only | yes |
| Snowflake | yes | yes | yes | yes | yes | no | yes; not by a parameter of the lambda around | yes | yes |
| Oracle | yes (VARRAY, nested table) | text, number, date | yes (object types) | as JSON | yes | yes | to text, number, date | yes | no |
| SQL Server | no | no | no | no | yes | no | no | no | no |
| MariaDB | no | no | no | no | yes | yes | no | no | no |
| SQLite | no | no | no | no | yes | yes | no | no | no |

"as JSON": the dialect has no map type; the column is a JSON object. A catalog that declares what the
object holds (`value`, and `key` INTEGER when the keys are integers written as text) makes it a map for
a query: `t.attrs@color` is a text and `t.lookup@1` the member `"1"`, as on a dialect with maps. Without
that declaration `@1` on it is an array position, and an integer key is written `@'1'`.

### Casting JSON

A JSON member is JSON, not a number or a text, so it cannot be compared or summed as it is. The casts
read the scalar at the path: `to_integer(t.doc@n) > 0`, `sum(to_float(t.doc@price))`,
`to_text(t.attrs@color) = 'red'`. A JSON string gives its text without the quotes and a number its
digits, on every dialect, so a cast answers the same everywhere; a member that is not there gives
NULL. The casts are `to_boolean`, `to_float`, `to_double`, `to_integer`, `to_bigint`, `to_smallint`,
`to_decimal`, `to_text` and `to_varchar`. A JSON object or list has no scalar: `to_text` of one is its
JSON text on DuckDB, PostgreSQL, Snowflake, SQLite, SQL Server and MariaDB, and NULL on Trino and
Oracle.

### Describing a nested column in the catalog

A nested column describes its own structure in `db.json`, with the same node a column is, to any
depth. A STRUCT has `fields`, an ARRAY an `element` (and a `length` when its size is fixed), a MAP a
`key` and a `value`:

```json
{ "name": "address", "label": "Address", "typeFamily": "STRUCT",
  "dialectType": "STRUCT(street VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE), phones VARCHAR[])",
  "fields": [
    { "name": "street", "typeFamily": "TEXT", "dialectType": "VARCHAR" },
    { "name": "geo", "typeFamily": "STRUCT", "fields": [
        { "name": "lat", "label": "Latitude", "typeFamily": "FLOAT", "unit": "deg" },
        { "name": "lon", "typeFamily": "FLOAT" } ] },
    { "name": "phones", "typeFamily": "ARRAY", "element": { "typeFamily": "TEXT" } } ] }
```

A node has what a column has: a label, a description, a unit, a quantity, a presentation, an encoding
(`"typeEncoding": "EPOCH:SECONDS"` on a BIGINT that is a timestamp) and a `dialectType`, which a leaf
may leave out (it is then the plain name of its family). The tree is the type. The `dialectType` of
the column, if it is a spelling of the same type, is checked against it and refused when it is not.
The part a selector chain ends at is found with `ConceptNodes.node(column, steps)`, over the concepts a catalog is converted to: `t.address@geo@lat` is
the `lat` node of the `geo` node of `address`, and what the catalog says there (its label, its unit,
its quantity) is what a result column made of it is explained by. The flatten views take the same
annotations from the element's nodes, so the columns of "revenue by sku" are labelled and measured as
the list's fields are. Each node can carry any of these; the complex catalogs have them on all of
theirs. A node may say nothing, and a column without a tree says nothing about its parts.

A column without a tree is read from its spelling, as before; `TypeTrees` (`./gradlew
:tools:typeTrees`) writes the tree of a spelling, for a catalog to start from.

### Declaring what is in a JSON column

JSON has no structure the database can describe, so a JSON column may list the members it is known to
hold, with the same nodes: `fields` for an object, `element` for an array, `value` for an object of
one kind of value (the keys are text). The members are scalars of the families BOOLEAN, INTEGER,
FLOAT, DECIMAL or TEXT, or objects and arrays of them:

```json
{ "name": "doc", "typeFamily": "JSON", "dialectType": "JSON", "fields": [
    { "name": "n", "typeFamily": "INTEGER" },
    { "name": "a", "typeFamily": "STRUCT", "fields": [
        { "name": "b", "typeFamily": "ARRAY", "element": { "typeFamily": "INTEGER" } } ] } ] }
```

Nothing is said about the members not listed.

A selection that reaches a declared scalar is that scalar: `t.doc@n > 0`, `t.doc@n + 1`,
`sum(t.doc@a@b@1)` and `GROUP BY t.doc@n` need no cast; the renderer reads the JSON scalar and casts
it, the way `to_integer(t.doc@n)` does. A step to a declared object or list is JSON again, carrying
the shape of that member; a member that is not declared is JSON of unknown content, as before. The
declaration is trusted, not checked: a value of another type gives what the cast gives (NULL or an
error, the dialect's).

A declared list of scalars (`.a.b[]: INTEGER`) is a list: `list_size(t.doc@a@b)`,
`list_sum(t.doc@a@b)` and `list_transform(t.doc@a@b, x -> x * 10)` work, and the dialect converts the
JSON array into its own list (DuckDB and Trino a cast, PostgreSQL `jsonb_array_elements_text`,
Snowflake `TRANSFORM`, Oracle `JSON_TABLE`). A list of objects or of lists stays JSON. Oracle
applies the functions but refuses a lambda over such a list, because the list is built where Oracle
returns nothing. SQL Server, MariaDB and SQLite have no list functions, so the array stays JSON.

### Differences that are kept, not hidden

* **JSON scalars.** DuckDB, PostgreSQL, Trino, Oracle and SQLite answer a JSON string as JSON, with
  its quotes, unless it is cast (see above). SQL Server and MariaDB answer it as plain text: their functions do not say whether a
  value was a string or a number. Objects and lists are JSON text everywhere; the spacing inside
  them is the engine's own.
* **Reduce on an empty list** is an error on DuckDB and NULL on Trino.
* **Oracle** builds a list only of text, number or date: a result that is a list of structs or of
  lists is refused. Its generic collection types hold text up to 4000 characters and turn a double
  into a NUMBER. The order of the elements of a nested table is not defined by the database; a
  VARRAY keeps its order, and the position of an element of a nested table is the order the database
  happens to return.
* **PostgreSQL** arrays do not nest: a list of lists is one multi-dimensional array, which must be
  rectangular, so a lambda cannot return a list of lists.
* **Snowflake** returns a nested value as JSON text; it is read back by the type of the column.

## How it is written in SQL

Lambda parameters are bound to what the dialect can name: a lambda parameter where it has lambdas,
a column of a derived table where it has not (Oracle, PostgreSQL). Where SQL has to name a list more
than once (its size and its elements, a position parameter), a list that is an expression is
evaluated once in a derived table of one row, so nesting does not multiply the SQL and a function
such as `random()` is not called again for each mention. Oracle and MariaDB cannot do that: Oracle
evaluates a derived table that refers to the outer query once for the whole query instead of once
per row, so every row would get the first row's list (measured), and MariaDB refuses the reference
("Unknown table"). They write the expression wherever it is needed, and the SQL of a lambda whose
list is itself a lambda grows with every nesting there; keep such chains short.
