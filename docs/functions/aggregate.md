---
menuTitle: "Aggregate Functions"
parent: "FUNCTIONS"
order: 10
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Aggregate Functions

Ten functions that turn many rows into one: `count`, `count_distinct`, `sum`, `avg`, `min`, `max`,
`string_agg`, `median`, `quantile_cont` and `quantile_disc`. You never write `GROUP BY` — using one
of these in a projection is what makes the query grouped, and the columns you also fetch become the
grouping key.

## count() and count(x) ask different questions

`count()` counts **rows**. `count(x)` counts the rows where *x* is not blank, which is how you ask
"how many orders actually have a shipping date". The two differ exactly by the blanks, and that is
usually the point of writing the second one.

    count()                 →  830
    count(o.shipped_date)   →  809   -- 21 orders never shipped

`count()` becomes `COUNT(*)`, not `count()`. Only DuckDB, SQLite and Trino accept the empty
parentheses; PostgreSQL, SQL Server, MariaDB, Oracle and Snowflake reject them outright with *"must
be used to call a parameterless aggregate function"*. That the reference dialect is one of the three
permissive ones is why it went unnoticed for a while.

`count_distinct(x)` is its own name rather than a modifier on `count`, because `DISTINCT` inside a
function call is SQL syntax that not every engine allows in every position — see the window note
below.

## Counting different rows takes the whole key

`count_distinct` also takes **several** values, and then counts how many different *combinations*
there are. That exists because a row is not always identified by one column — naming the entity
counts order **lines**:

    FIND order_details od FETCH count_distinct(od) n

| what you write | what it counts | |
|---|---|---|
| `count_distinct(od)` | order lines | 2155 |
| `count_distinct(od.order_id)` | orders | 830 |

An order line is identified by order **and** product together. Both are legitimate questions; they
are just different ones, and writing the entity asks the first.

Engines disagree about how to spell it, and koryki hides that:

| | rendered as |
|---|---|
| DuckDB, PostgreSQL, Trino | `COUNT(DISTINCT (a, b))` |
| MariaDB, Snowflake | `COUNT(DISTINCT a, b)` |
| Oracle, SQL Server, SQLite | refused — the engine has neither form |

Where it is refused, the message names the way out: concatenate the values yourself.

    FIND order_details od FETCH count_distinct(concat(od.order_id, '#', od.product_id)) n

**Choose that separator carefully, and do not omit it.** Without one, `(1, 23)` and `(12, 3)` both
become `123` and count as the same combination; with one that occurs in the data, the same collision
comes back for the rows that contain it. koryki does not pick the separator for you precisely
because no character can be guaranteed absent from your values — that decision has to be yours.

## sum keeps the type, avg does not

`sum`, `min` and `max` return whatever the argument was: a sum of integers is an integer, a sum of
money is money. `avg` always comes back fractional, because an average of whole numbers usually
isn't one.

That distinction is worth stating because an engine can disagree: Trino used to return the average
of a `DECIMAL(10,2)` column as `DECIMAL(10,2)` as well, so the mean of three prices came out
`11.92` where every other engine said `11.916667`. The average of money is not money, and it is
levelled now.

## string_agg needs its separator

    string_agg(o.ship_city, ', ')

There is no default — the separator is a required argument, because the engines disagree on what a
missing one means. Each spells the function differently (`GROUP_CONCAT`, `LISTAGG`,
`array_join(array_agg(…))`), which is invisible until you try to use it as a window function.

## Not every aggregate can be a window function

`string_agg` has no `OVER` form on MariaDB, SQL Server, Oracle, Snowflake or Trino, and
`count_distinct` has none on MariaDB, SQL Server, PostgreSQL or SQLite. DuckDB is the only engine
that allows both. Where the engine cannot, the query is refused before it runs, with a message
naming the function and pointing at it — not a driver error after the fact.

## median and the quantile functions are not everywhere

`median`, `quantile_cont` and `quantile_disc` read the *sorted* values rather than reduce them
arithmetically, which not every engine can express as a plain aggregate. DuckDB, PostgreSQL, Oracle
and Snowflake support all three natively; MariaDB, SQLite, SQL Server and Trino refuse them
outright, with a message naming the function, before the query ever runs.

`quantile_cont(fraction, x)` interpolates between the two nearest sorted values when *fraction*
falls between them, so the result need not be one of the inputs — `quantile_cont(0.5, x)` is
`median(x)`. `quantile_disc(fraction, x)` never interpolates: the result is always one of the
actual input values, which is also why it, alone among the three, keeps its argument's own type
rather than always coming back fractional.

## count

`count([value: any])` → BIGINT *(aggregate)*

Number of input rows, or of non-null values when an expression is given.

Operands: any type.

Sample query:

```kql
// count: number of orders.
FIND orders o
FETCH count(o.order_id) order_count
```

### Generated SQL

**all dialects**

```sql
-- count: number of orders.
SELECT
  count(o.order_id) AS order_count
FROM
 orders o
```


## count_distinct

`count_distinct(value: any)` → BIGINT *(aggregate)*

Number of distinct non-null values — how many *different* customers, say, rather than how many rows.

Operands: any type.

`count_distinct(value: any, more: any, ...)` → BIGINT *(aggregate)*

Number of distinct combinations of the given values — how many *different* order lines, say, when a line is identified by order and product together.

Operands: any type.

Sample query:

```kql
// count_distinct: how many different customers ordered from each country — not how many orders,
// which is what plain count would give. The one function every BI tool exposes under its own name.
FIND customers c, orders o
FETCH c.country, count_distinct(c.customer_id) customers, count(o.order_id) orders
```

### Generated SQL

**all dialects**

```sql
-- count_distinct: how many different customers ordered from each country — not how many orders,
-- which is what plain count would give. The one function every BI tool exposes under its own name.
SELECT
  c.country
, COUNT(DISTINCT c.customer_id) AS customers
, count(o.order_id) AS orders
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
GROUP BY
  c.country
```


## avg

`avg(value: numeric | duration)` → FLOAT *(aggregate)*

Average of the input values.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| duration | the numeric values to average |

Sample query:

```kql
// avg: average line-item unit price.
FIND order_details od
FETCH avg(od.unit_price) avg_price
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · sqlite**

```sql
-- avg: average line-item unit price.
SELECT
  avg(od.unit_price) AS avg_price
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| trino | `avg(CAST(od.unit_price AS DOUBLE)) AS avg_price` |


## sum

`sum(value: numeric | duration)` → argument-dependent *(aggregate)*

Sum of the input values.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| duration | the values to add together |

Sample query:

```kql
// sum: total quantity ordered across all lines.
FIND order_details od
FETCH sum(od.quantity) total_quantity
```

### Generated SQL

**all dialects**

```sql
-- sum: total quantity ordered across all lines.
SELECT
  sum(od.quantity) AS total_quantity
FROM
 order_details od
```


## min

`min(value: any)` → argument-dependent *(aggregate)*

Minimum input value.

Operands: any type.

Sample query:

```kql
// min: earliest order date.
FIND orders o
FETCH min(o.order_date) earliest
```

### Generated SQL

**all dialects**

```sql
-- min: earliest order date.
SELECT
  min(o.order_date) AS earliest
FROM
 orders o
```


## max

`max(value: any)` → argument-dependent *(aggregate)*

Maximum input value.

Operands: any type.

Sample query:

```kql
// max: latest order date.
FIND orders o
FETCH max(o.order_date) latest
```

### Generated SQL

**all dialects**

```sql
-- max: latest order date.
SELECT
  max(o.order_date) AS latest
FROM
 orders o
```


## string_agg

`string_agg(value: any, separator: TEXT)` → TEXT *(aggregate)*

Concatenates non-null input values into a string, separated by *separator*. The order is **unspecified**: no engine promises one for an aggregate without an explicit sort, so the same query may answer differently on another dialect, another plan, or another run. Pass *order_by* to fix it.

| Argument | Type | Description |
|---|---|---|
| value | any | the values to concatenate |
| separator | TEXT | text placed between consecutive values |

`string_agg(value: any, separator: TEXT, order_by: any)` → TEXT *(aggregate)*

Concatenates non-null input values into a string, separated by *separator*, in ascending order of *order_by*. The two-argument form leaves the order to the engine.

| Argument | Type | Description |
|---|---|---|
| value | any | the values to concatenate |
| separator | TEXT | text placed between consecutive values |
| order_by | any | the expression the values are sorted by |

Sample query:

```kql
// string_agg: the product names of a category, in one cell, in a fixed order.
//
// Sorted by product_id rather than by name on purpose: a text sort depends on the engine's
// collation, so the same names come back in a different order on PostgreSQL and Oracle than on
// DuckDB. An integer sort is the same everywhere.
FIND products p
FETCH p.category_id category, string_agg(p.product_name, ', ', p.product_id) names
```

### Generated SQL

**duckdb · postgresql · sqlite**

```sql
-- string_agg: the product names of a category, in one cell, in a fixed order.
-- Sorted by product_id rather than by name on purpose: a text sort depends on the engine's
-- collation, so the same names come back in a different order on PostgreSQL and Oracle than on
-- DuckDB. An integer sort is the same everywhere.
SELECT
  p.category_id AS category
, string_agg(p.product_name, ', ' ORDER BY p.product_id) AS names
FROM
 products p
GROUP BY
  p.category_id
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · snowflake | `, LISTAGG(p.product_name, ', ') WITHIN GROUP (ORDER BY p.product_id) AS names` |
| mariadb | `, GROUP_CONCAT(p.product_name ORDER BY p.product_id SEPARATOR ', ') AS names` |
| mssql | `, STRING_AGG(p.product_name, ', ') WITHIN GROUP (ORDER BY p.product_id) AS names` |
| trino | `, array_join(array_agg(p.product_name ORDER BY p.product_id), ', ') AS names` |


## median

`median(value: numeric)` → FLOAT *(aggregate)*

Middle value of the inputs once sorted -- the average of the two middle values when there is an even number of them, so the result need not be one of the inputs. Prefer it to `avg` for a value skewed by outliers: a few near-zero denominators drag the average away but move the median only if they are actually in the middle.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the numbers to take the middle of |

Sample query:

```kql
// median: the middle unit price of each category's products, once sorted.
FIND products p, p categories c
FETCH c.category_name, median(p.unit_price) median_price
```

### Generated SQL

**duckdb · oracle · snowflake**

```sql
-- median: the middle unit price of each category's products, once sorted.
SELECT
  c.category_name
, MEDIAN(p.unit_price) AS median_price
FROM
 products p
  INNER JOIN categories c ON
   p.category_id = c.category_id
GROUP BY
  c.category_name
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY p.unit_price) AS median_price` |

Unsupported: **mariadb**, **mssql**, **sqlite**, **trino**


## quantile_cont

`quantile_cont(fraction: numeric, value: numeric)` → FLOAT *(aggregate)*

The value at *fraction* of the way through the sorted inputs, interpolating between the two nearest when it falls between them -- so the result need not be one of the inputs. `quantile_cont(0.5, x)` is `median(x)`; `quantile_cont(0.25, x)` the lower quartile.

| Argument | Type | Description |
|---|---|---|
| fraction | numeric | where in the sorted values to read, from 0 (the first) to 1 (the last); 0.5 is the median |
| value | numeric | the numbers to take the quantile of |

Sample query:

```kql
// quantile_cont: the price a quarter of the way up each category's sorted unit prices, interpolated.
FIND products p, p categories c
FETCH c.category_name, quantile_cont(0.25, p.unit_price) lower_quartile
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql**

```sql
-- quantile_cont: the price a quarter of the way up each category's sorted unit prices, interpolated.
SELECT
  c.category_name
, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY p.unit_price) AS lower_quartile
FROM
 products p
  INNER JOIN categories c ON
   p.category_id = c.category_id
GROUP BY
  c.category_name
```

Unsupported: **mariadb**, **mssql**, **sqlite**, **trino**

> Results differ from the other dialects on **duckdb**.


## quantile_disc

`quantile_disc(fraction: numeric, value: ORDERED)` → argument-dependent *(aggregate)*

Like `quantile_cont`, but never interpolates: the result is always one of the input values, the one that sits at or just past *fraction* of the way through. Works on any value that can be ordered, not only numbers -- a quantile of dates, say -- which `quantile_cont` cannot do.

| Argument | Type | Description |
|---|---|---|
| fraction | numeric | where in the sorted values to read, from 0 (the first) to 1 (the last) |
| value | ORDERED | the values to take the quantile of |

Sample query:

```kql
// quantile_disc: like quantile_cont, but always one of the actual prices, never interpolated.
FIND products p, p categories c
FETCH c.category_name, quantile_disc(0.25, p.unit_price) lower_quartile
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql**

```sql
-- quantile_disc: like quantile_cont, but always one of the actual prices, never interpolated.
SELECT
  c.category_name
, PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY p.unit_price) AS lower_quartile
FROM
 products p
  INNER JOIN categories c ON
   p.category_id = c.category_id
GROUP BY
  c.category_name
```

Unsupported: **mariadb**, **mssql**, **sqlite**, **trino**

