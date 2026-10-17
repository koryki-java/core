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
  A position on an Oracle collection carries a test of the collection for NULL; see
  [Oracle: an element of a VARRAY](#oracle-an-element-of-a-varray).

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

## Unnest: one row per element

A list cannot be grouped by what is inside it: "revenue by sku" needs one row per element. `unnest`
makes a list into rows, and is written where a source is, after the source the list comes from:

```
FIND check_complex t, t unnest(t.items) i
FETCH i.sku, sum(i.qty * i.price) revenue
```

Each element is a row of the alias `i`. The columns of the alias are the fields of the element,
`value` for an element that is no struct (`FIND check_complex t, t unnest(t.tags) g FETCH g.value`),
and `ordinal`, the position in the list from 1. Nothing is added to the catalog: the rows are typed
from the list, when the query is checked, so every list works the same.

- **The list is an expression.** A column, `t.items`; a list in a struct, `t.address@phones`; what
  a lambda made, `unnest(list_filter(t.items, x -> x@qty > 1)) i`; or a list inside the rows of an
  earlier unnest, a field of its alias: `t unnest(t.items) i, i unnest(i.opts) o`.
- **An empty or NULL list has no rows,** and the parent is not in the answer. The outer form keeps
  it, with NULLs for the element: `t + unnest(t.items) i`, as every `+` link does.
- **The model's words** name the fields: under a model that calls `sku` `artikel`, `i.artikel` is
  the field, and so is `i.sku`. Headers and labels take the member's label
  ([Members and notions](MEMBERS_AND_NOTIONS.md)).
- **A filter on the rows** of an inner unnest keeps the matching elements, as it does on any source.
  On an outer one the condition belongs to the join, so that the parent without a matching element
  is kept; where an engine cannot put it there it is refused (see the table).
- **`count(i)` is refused:** the rows have no key to count. Count a field of the element,
  `count(i.sku)`.
- **`ordinal` is asked of the database only where the query reads it.** On DuckDB `WITH
  ORDINALITY` made the same query about thirty times slower on two million rows (measured, see
  [the plan](plans/unnest.md)), and the rows are otherwise the same.

What `unnest` refuses, with a position and a message:

| use | message |
|---|---|
| another function as a source, `t explode(t.items) i` | only unnest(list) makes a list into rows |
| anything but one list as its argument: two, a lambda, a condition, a bare alias | unnest takes one list |
| `FIND unnest(...) i` | write it after the source the list comes from |
| inside `EXISTS` | unnest cannot start an EXISTS |
| with `VIA` or columns | a list made into rows is joined to the source it comes from |
| `unnest(t.nr)`, no list | no overload accepts the argument types |
| JSON the catalog does not declare as an array | the catalog says where the array is, as in `.a.b[]` |
| in an expression, `FETCH unnest(t.items)` | it makes a list into rows, so it is written as a source |

### What each dialect does

The SQL is a lateral join of the list, so the list may be a column of a source to its left:

| | inner | outer | position | a condition on the rows of an outer unnest |
|---|---|---|---|---|
| DuckDB | `JOIN UNNEST(l) AS i(item) ON TRUE` | `LEFT OUTER JOIN` | `WITH ORDINALITY` | in the ON |
| PostgreSQL | `JOIN LATERAL unnest(l) AS i(f1, …)` | `LEFT OUTER JOIN LATERAL` | `WITH ORDINALITY` | in the ON |
| Trino | `CROSS JOIN UNNEST(l) AS i(f1, …)` | `LEFT OUTER JOIN … ON TRUE` | `WITH ORDINALITY` | refused |
| Snowflake | `, LATERAL FLATTEN(INPUT => l) i` | `OUTER => TRUE` | `INDEX + 1` | refused |
| Oracle | `CROSS APPLY TABLE(l) i` | `OUTER APPLY` | refused | refused |
| MariaDB | `JOIN JSON_TABLE(l, '$[*]' COLUMNS (…)) i ON TRUE` | `LEFT OUTER JOIN` | `FOR ORDINALITY` | in the ON |
| SQLite | `JOIN json_each(l) i ON json_type(l) = 'array'` | `LEFT OUTER JOIN` | `key + 1` | in the ON |
| SQL Server | `CROSS APPLY OPENJSON(…) i` | `OUTER APPLY` | `[key] + 1` | refused |

Measured, and kept rather than hidden:

- **Trino** accepts `CROSS JOIN UNNEST` and `LEFT JOIN UNNEST … ON TRUE` only, and answers a lateral
  subquery over `UNNEST` with "correlated subquery is not supported" (Trino 481); a query that
  fails that way leaves the connection unusable for the next statement, so it is refused before.
- **Snowflake** `FLATTEN` leaves a NULL element out: `['x', NULL, 'z']` has two rows, and the
  position of `'z'` is 3.
- **Oracle** has no position for the elements of a collection in a lateral join: `ROWNUM` numbers
  the rows of the whole statement, and `ROW_NUMBER()` needs the key of the parent. `ordinal` is
  refused. A list of objects cannot be filtered by a lambda to be unnested (the generic collection
  types hold text, numbers and dates), as before.
- **SQLite, MariaDB and SQL Server** have no lists. They unnest an array inside a JSON column that
  the catalog declares (`.a.b[]: INTEGER`, [see below](#declaring-what-is-in-a-json-column)):
  `unnest(t.doc@a@b) b`. The elements are scalars, or objects whose members are scalars; a row whose
  document has no array there has no rows, and JSON that is not declared as an array is refused.
  Where `unnest` is given a JSON array on a dialect that has lists, the list is the converted one,
  and the rows are those of any other list.

## Flatten views: a table in the database

`unnest` needs no preparation. A view that flattens a list stays the answer where a table is
wanted: heavy use of the same rows (a view can be materialized and indexed, an `unnest` is read
again by every query), a name of its own, or an entity with labels, units and a relation to its
parent that the semantic layer and the charts work with. The view is an ordinary table of the catalog.
The JSON engines read the document again for every query, and nothing was measured on long lists
there.

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

A column says its type, and what the type is made of, in its concept: `"concept"` on the column, or
`"conceptRef"` with the name of a concept of the schema. A STRUCT concept has `entries`, one for
each field, in order; an ARRAY has the entry `element` (and an `arraySize` when its size is fixed),
a MAP the entries `key` and `value`. An entry is a string, the spelling of a type (`"VARCHAR"`) or
the name of a concept of the schema, or an object: a concept of its own that belongs to this entry
alone and is shared with nobody.

```json
{ "name": "address",
  "concept": { "label": "Address", "typeFamily": "STRUCT",
    "dialectType": "STRUCT(street VARCHAR, geo STRUCT(lat DOUBLE, lon DOUBLE), phones VARCHAR[])",
    "entries": {
      "street": "VARCHAR",
      "geo": { "typeFamily": "STRUCT", "entries": {
          "lat": { "label": "Latitude", "typeFamily": "FLOAT", "dialectType": "DOUBLE", "unit": "deg" },
          "lon": "DOUBLE" } },
      "phones": { "typeFamily": "ARRAY", "entries": { "element": "VARCHAR" } } } } }
```

A concept has a label, a description, a comment, a unit, a quantity, a presentation, an encoding
(`"typeEncoding": "EPOCH:SECONDS"` on a BIGINT that is a timestamp) and a `dialectType`, which a
leaf may leave out only where it is a spelling in an entry. The entries are the type. The
`dialectType` of a container, if it is a spelling of the same type, is checked against them and
refused when it is not. The part a selector chain ends at is found with
`ConceptNodes.node(column, steps)`: `t.address@geo@lat` is the `lat` entry of the `geo` entry of
`address`, and what the catalog says there (its label, its unit, its quantity) is what a result
column made of it is explained by. The flatten views take the same annotations from the element's
entries, so the columns of "revenue by sku" are labelled and measured as the list's fields are.
Each part can carry any of these; the complex catalogs have them on all of theirs. A part that is a
spelling says nothing but its type. A container needs its entries: a spelling alone does not type
a column.

### Declaring what is in a JSON column

JSON has no structure the database can describe, so a JSON column may list the members it is known
to hold: its concept is the container the content is, a STRUCT for an object, an ARRAY for a list,
a MAP for an object of one kind of value (the keys are text, or INTEGER when they are integers
written as text), with the encoding `JSON`, which says the value is stored as JSON in whatever type
its `dialectType` names (`JSON`, `JSONB`, `VARIANT`, `VARCHAR`, `TEXT`). The members are scalars of
the families BOOLEAN, INTEGER, FLOAT, DECIMAL or TEXT, or objects and arrays of them:

```json
{ "name": "doc",
  "concept": { "typeFamily": "STRUCT", "typeEncoding": "JSON", "dialectType": "JSON",
    "entries": {
      "n": "INTEGER",
      "a": { "typeFamily": "STRUCT", "entries": {
          "b": { "typeFamily": "ARRAY", "entries": { "element": "INTEGER" } } } } } } }
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

## Oracle: an element of a VARRAY

Oracle has no subscript for a collection in SQL, so a position is a scalar subquery over `TABLE(list)`
numbered by `ROWNUM`. On Oracle Database Free 23.26.2 that subquery, written the obvious way, is run
**once for the whole statement**: every row gets the answer of the first row, and a row whose list is
empty or NULL gets it too. This is a behaviour of the database, reproduced here without koryki:

```sql
CREATE TYPE num_list AS VARRAY(10) OF NUMBER;
CREATE TABLE t (nr INTEGER PRIMARY KEY, v num_list);
INSERT INTO t VALUES (1, num_list(10, 20));
INSERT INTO t VALUES (2, num_list());
INSERT INTO t VALUES (3, NULL);
INSERT INTO t VALUES (4, num_list(30));

-- the first element: 10, NULL, NULL, 30 is right; Oracle answers 10, 10, 10, 10
SELECT t.nr,
       (SELECT e.x FROM (SELECT COLUMN_VALUE x, ROWNUM rn FROM TABLE(t.v) z) e WHERE e.rn = 1)
FROM t ORDER BY t.nr;

-- with a test of the list in the subquery the answer is right: 10, NULL, NULL, 30
SELECT t.nr,
       (SELECT e.x FROM (SELECT COLUMN_VALUE x, ROWNUM rn FROM TABLE(t.v) z) e
         WHERE e.rn = 1 AND t.v IS NOT NULL)
FROM t ORDER BY t.nr;
```

What was measured:

* **The plan says why.** `EXPLAIN PLAN` of the first query shows the subquery's `VIEW` over
  `COLLECTION ITERATOR PICKLER FETCH` as a branch of its own, with no bind variable: the reference to
  `t.v` inside `TABLE()` is not counted as a correlation. With the test, the plan has a `FILTER` on
  `:B1 IS NOT NULL` above it, the correlation is there, and the subquery runs for each row. With a
  single row (`WHERE t.nr = 2`) the first query is right, since the first row is that row.
* **What the guard does not change.** An empty list is not NULL, so `t.v IS NOT NULL` is true for it
  and the subquery finds no element, as it should; for a NULL list the subquery answers NULL as before.
* **Where it happens.** A list in a column of a table, a VARRAY, inline or stored as a LOB (`VARRAY(2000)`
  behaves as `VARRAY(10)`). It does not happen for a NESTED TABLE column counted with `COUNT(*)`, for a
  subquery without the `ROWNUM` view (Oracle merges the view then), and, which we cannot explain, for a
  table that also has a JSON column: `check_complex` has one and never showed the fault, `check_shared`
  has none and did. A query that is right today on a table is therefore no proof that it is right on
  another, which is why the test is always written.
* **What was checked on the same data and is right without it.** `list_size`, `list_any`,
  `list_transform` (they build a collection with `CAST(MULTISET(...))`, which is a correlation of its
  own), and a position from the end (`@-1`, which names the list in the subquery's `WHERE`).
* **The cost.** The test repeats the list expression, which for a list made by a lambda is a long
  one. It does not change the meaning.

The bed `check_shared` (`SharedConceptOracleTest`) is the regression test: it has a list that is
empty after one that is not. We have not reported this to Oracle and know of no bug number; the
reproduction above is what to send.
