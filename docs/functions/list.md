---
menuTitle: "List Functions"
parent: "FUNCTIONS"
order: 13
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# List Functions

Functions on a list: a column that holds one (an ARRAY, or a JSON array where the catalog declares it),
or what another list function or a lambda made. They ask the list about itself (`list_size`,
`list_contains`, `list_sum`, `list_min`, `list_max`, `list_avg`), shape it (`list_sort`,
`list_distinct`, `list_concat`) or make one (`list(a, b, ...)`; `list_agg` is an aggregate and is on
the [Aggregate](aggregate.md) page). The ones that take a function per element, `list_transform`
and the like, are on the [Lambda](lambda.md) page. `unnest` is used as a source, not in an expression: it
makes a row of each element (`FIND check_complex t, t unnest(t.items) i FETCH i.sku`).

    list_size(t.tags)                     →  3
    list_contains(t.tags, 'red')          →  true      -- or: 'red' IN t.tags
    list_sum(t.scores)                    →  17
    list_sort(t.tags, 'desc')

A list that is NULL gives NULL. A list with nothing left to count (empty, or only NULLs) gives NULL
for the sum, the mean, the smallest and the largest. A list is not a text: `length(t.tags)` is
refused and names `list_size`.

Lists exist on DuckDB, PostgreSQL, Trino, Snowflake and Oracle; a use that another dialect cannot
express is refused with its position. [Nested values](NESTED.md) has the table, and what each
dialect does differently.

## list_size

`list_size(list: ARRAY)` → INTEGER

The number of elements of the list, NULL elements included.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_contains

`list_contains(list: ARRAY, value: any)` → BOOLEAN

Whether the list has an element equal to the value.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| value | any | the value to look for |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_sum

`list_sum(list: ARRAY)` → argument-dependent

The sum of the elements, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list of numbers |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_min

`list_min(list: ARRAY)` → argument-dependent

The smallest element, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_max

`list_max(list: ARRAY)` → argument-dependent

The largest element, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_avg

`list_avg(list: ARRAY)` → DOUBLE

The mean of the elements as a floating point number, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list of numbers |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_sort

`list_sort(list: ARRAY [, order: TEXT])` → argument-dependent

The list in order, NULL elements last.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| order | TEXT *(optional)* | 'asc' (the default) or 'desc' |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_distinct

`list_distinct(list: ARRAY)` → argument-dependent

The elements of the list without duplicates and without NULLs, in ascending order.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_concat

`list_concat(list: ARRAY, other: ARRAY)` → argument-dependent

The first list followed by the second. A NULL list counts as an empty one; the answer is NULL only when both are.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the first list |
| other | ARRAY | the list that follows |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list

`list(value: any, ...)` → argument-dependent

A list of the values, in the order they are written.

Operands: any type.

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## unnest

`unnest(list: list | JSON array)` → argument-dependent

The elements of the list as rows, one for each, written as a source: FIND orders o, o unnest(o.items) i. The fields of an element are the columns of the alias, and an empty or NULL list has no rows (+ keeps the row it comes from).

| Argument | Type | Description |
|---|---|---|
| list | list \| JSON array | the list made into rows: a list, or a JSON array the catalog declares |

