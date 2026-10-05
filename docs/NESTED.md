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
with an `@`: `list_transform(t.scores, x -> @x * 2)`.

* `list_transform` and `list_filter`: one parameter (the element) or two (element, position from 1).
* `list_reduce`: two parameters (state, element); the first element is the initial state, so a list
  of one gives that element.
* A lambda sees the columns of the query as well as its parameters. A parameter may not be named like
  an alias visible where the lambda stands (`t.price` is a field of the alias, `@t@price` a selection
  from the parameter), nor like a parameter of a lambda around it; both are refused when the query is
  checked.
* A NULL list stays NULL.

## What the dialects do

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

"as JSON": the dialect has no map type; the column is a JSON object, its keys are text, and `@1` on it
is an array position, so an integer key is written `@'1'`.

### Differences that are kept, not hidden

* **JSON scalars.** DuckDB, PostgreSQL, Trino, Oracle and SQLite answer a JSON string as JSON, with
  its quotes. SQL Server and MariaDB answer it as plain text: their functions do not say whether a
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
