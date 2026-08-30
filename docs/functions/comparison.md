---
menuTitle: "Comparison Operators"
parent: "FUNCTIONS"
order: 2
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Comparison Operators

A comparison is a **predicate**: it yields true, false, or null, and it is what `FILTER` and
`HAVING` take. See the *Logical Operators* page for how predicates combine with `AND`, `OR` and
`NOT`, and [TEMPORAL](TEMPORAL.md) for the temporal comparison rules.

## NULL never compares equal

Null means *unknown*, so every comparison with a null operand yields null rather than true or
false — including `null = null`. A `FILTER` keeps only rows whose predicate is **true**, so a
row with a null operand is dropped by a comparison *and* by its negation:

    FIND orders o
    FILTER o.shipped_date < "1998-01-01"
    FETCH o.order_id

returns no unshipped order, and neither would the opposite comparison. This is why `ISNULL`
exists: it is the only way to test for null, since `= NULL` can never match.

    FIND orders o
    FILTER o.shipped_date ISNULL
    FETCH o.order_id

Its negation, "is not null", is written `NOT o.shipped_date ISNULL`.

### Comparing two values that may both be blank

`ISNULL` tests one value. To compare *two* columns that may each be empty, use `DISTINCT`, which
counts a missing value as a difference and two missing values as equal — so it always answers true
or false, never unknown:

    FIND customers c, orders o
    FILTER c.country = 'France' AND NOT c.region DISTINCT o.ship_region
    FETCH c.customer_id, c.region

This is the same distinctness `FETCH DISTINCT` applies when it collapses duplicate rows: there too,
two blanks count as the same value. So `a DISTINCT b` asks "are these two genuinely different
values", and `NOT a DISTINCT b` asks "are they the same, blanks included".

Here is an example, French customers have no region recorded, and neither do their orders. Written with `<>` the query
above finds no differences to exclude and `=` matches nothing, because comparing two missing values
is unknown rather than true. With `DISTINCT` the blanks compare as equal and the orders come back.

**Prefer `=` and `<>` when the columns cannot be empty.** A null-safe comparison is not a plain
equality test, so on some engines it can stop an index being used where `=` would use one; if you
are filtering a large table it is worth checking that on your own data before making `DISTINCT` a
habit.

## Ordered comparisons need an order

`<`, `<=`, `>`, `>=` and `BETWEEN` require operands with a total order. Numbers, text, dates,
timestamps and times all have one. Durations only have one **within a unit class** — clock
(`h m s ms`), days (`d`), or months (`mo q y`) — so ordering `1mo` against `30d`, or using a
mixed duration like `1d2h`, is rejected: there is no anchor-independent answer. A
string-encoded interval is not orderable at all. Compare those with `=` or `<>` instead, or
store the value as a numeric `INTERVAL:<unit>` (see [TEMPORAL](TEMPORAL.md)).

## Ranges over dates and timestamps

`value BETWEEN low AND high` is inclusive on both ends — but when *high* is a DATE or TIMESTAMP
**literal** it is rewritten in SQL to `value >= low AND value < high + 1d`. That is what makes a range
ending on a day include the whole day: a plain SQL `BETWEEN … AND '1996-12-31'` on a TIMESTAMP
column stops at `00:00:00` and silently drops almost all of the last day. The BETWEEN sample
below shows the rewrite in its generated SQL.

The rewrite only fires for a **literal** upper bound. With a computed one — `month_end(...)`,
say — no rewrite happens and the half-open form has to be written out by hand (see
[TEMPORAL](TEMPORAL.md)).

## Membership tests

    FILTER c.country IN ('USA', 'Canada')

    FILTER NOT c.customer_id IN (
        FIND orders o
        FETCH o.customer_id
    )

Beware the second form with `NOT`: if the subquery yields a single null, the membership test is
unknown for every row and the result is empty. `NOT EXISTS` is immune and is usually what you
want.

## Pattern matching with LIKE

`%` matches any run of characters, `_` exactly one. Both operands must be TEXT.

Whether `LIKE` is case-sensitive is **not** decided by KQL — it follows the column's collation,
so the same query can match differently on different databases (and on the same database with a
different collation). Where it matters, normalise explicitly with `upper()` or `lower()` on both
sides rather than relying on the server default. "Does not match" is written `NOT x LIKE '…'`.

## Custom operators

The grammar accepts any lowercase identifier as an infix operator, and one that is not in the
catalog is passed through to the generated SQL verbatim. That is a deliberate escape hatch for
dialect-specific operators, but it is unchecked: the query works on the database it was written
against and may fail on every other one. Transpiling reports it as a warning.

## =

`left = right` → BOOLEAN

True if *left* equals *right*.

Operands: any type.

Sample query:

```kql
// = : orders shipped to a specific city.
FIND orders o
FILTER o.ship_city = 'London'
FETCH o.order_id, o.ship_city
```

### Generated SQL

**all dialects**

```sql
-- = : orders shipped to a specific city.
SELECT
  o.order_id
, o.ship_city
FROM
 orders o
WHERE
  o.ship_city = 'London'
```


## <>

`left <> right` → BOOLEAN

True if *left* does not equal *right*. NULL on either side yields NULL, not TRUE — a row with a NULL operand is not returned.

Operands: any type.

Sample query:

```kql
// <> : orders shipped anywhere but one city.
FIND orders o
FILTER o.ship_city <> 'London'
FETCH o.order_id, o.ship_city
```

### Generated SQL

**all dialects**

```sql
-- <> : orders shipped anywhere but one city.
SELECT
  o.order_id
, o.ship_city
FROM
 orders o
WHERE
  o.ship_city <> 'London'
```


## <

`left < right` → BOOLEAN

True if *left* is less than *right*.

| Argument | Type | Description |
|---|---|---|
| left | ORDERED | the value being tested |
| right | ORDERED | the threshold it must stay below |

Sample query:

```kql
// < : order lines priced below a threshold.
FIND order_details od
FILTER od.unit_price < 10
FETCH od.order_id, od.unit_price
```

### Generated SQL

**all dialects**

```sql
-- < : order lines priced below a threshold.
SELECT
  od.order_id
, od.unit_price
FROM
 order_details od
WHERE
  od.unit_price < 10
```


## <=

`left <= right` → BOOLEAN

True if *left* is less than or equal to *right*.

| Argument | Type | Description |
|---|---|---|
| left | ORDERED | the value being tested |
| right | ORDERED | the threshold it must not exceed |

Sample query:

```kql
// <= : order lines at or below a discount ceiling.
FIND order_details od
FILTER od.discount <= 0.1
FETCH od.order_id, od.discount
```

### Generated SQL

**all dialects**

```sql
-- <= : order lines at or below a discount ceiling.
SELECT
  od.order_id
, od.discount
FROM
 order_details od
WHERE
  od.discount <= 0.1
```


## >

`left > right` → BOOLEAN

True if *left* is greater than *right*.

| Argument | Type | Description |
|---|---|---|
| left | ORDERED | the value being tested |
| right | ORDERED | the threshold it must exceed |

Sample query:

```kql
// > : orders with freight above a threshold.
FIND orders o
FILTER o.freight > 100
FETCH o.order_id, o.freight
```

### Generated SQL

**all dialects**

```sql
-- > : orders with freight above a threshold.
SELECT
  o.order_id
, o.freight
FROM
 orders o
WHERE
  o.freight > 100
```


## >=

`left >= right` → BOOLEAN

True if *left* is greater than or equal to *right*.

| Argument | Type | Description |
|---|---|---|
| left | ORDERED | the value being tested |
| right | ORDERED | the threshold it must reach or exceed |

Sample query:

```kql
// >= : order lines at or above a quantity threshold.
FIND order_details od
FILTER od.quantity >= 10
FETCH od.order_id, od.quantity
```

### Generated SQL

**all dialects**

```sql
-- >= : order lines at or above a quantity threshold.
SELECT
  od.order_id
, od.quantity
FROM
 order_details od
WHERE
  od.quantity >= 10
```


## LIKE

`string LIKE pattern` → BOOLEAN

True if *string* matches the SQL LIKE *pattern* (`%` and `_` wildcards). Both operands must be TEXT.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text being matched |
| pattern | TEXT | the SQL LIKE pattern with `%` and `_` wildcards |

Sample query:

```kql
// LIKE : customers whose company name starts with 'A'.
FIND customers c
FILTER c.company_name LIKE 'A%'
FETCH c.customer_id, c.company_name
```

### Generated SQL

**all dialects**

```sql
-- LIKE : customers whose company name starts with 'A'.
SELECT
  c.customer_id
, c.company_name
FROM
 customers c
WHERE
  c.company_name LIKE 'A%'
```


## BETWEEN

`value BETWEEN low AND high` → BOOLEAN

True if *value* lies within the inclusive range [*low*, *high*]. The `AND` between the bounds belongs to the range and binds tighter than the logical `AND`, so `a BETWEEN 1 AND 2 AND b` is a range test combined with the predicate *b*. With a DATE or TIMESTAMP *literal* as the upper bound this is rewritten to the half-open form `value >= low AND value < high + 1d`, so a range ending on a day includes that whole day rather than stopping at midnight (see docs/TEMPORAL.md).

| Argument | Type | Description |
|---|---|---|
| value | ORDERED | the value being tested |
| low | ORDERED | the inclusive lower bound of the range |
| high | ORDERED | the inclusive upper bound of the range |

Sample query:

```kql
// BETWEEN : orders in a date range — a temporal upper bound renders as a half-open interval.
FIND orders o
FILTER o.order_date BETWEEN "1996-07-01" AND "1996-12-31"
FETCH o.order_id, o.order_date
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · trino**

```sql
-- BETWEEN : orders in a date range — a temporal upper bound renders as a half-open interval.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date >= DATE '1996-07-01'
 AND
  o.order_date < DATE '1997-01-01'
```

**mssql**

```sql
-- BETWEEN : orders in a date range — a temporal upper bound renders as a half-open interval.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date >= CAST('1996-07-01' AS DATE)
 AND
  o.order_date < CAST('1997-01-01' AS DATE)
```

**sqlite**

```sql
-- BETWEEN : orders in a date range — a temporal upper bound renders as a half-open interval.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date >= '1996-07-01'
 AND
  o.order_date < '1997-01-01'
```


## IN

`value IN (items, …)` → BOOLEAN

True if *value* equals any of the listed *items*.

Operands: any type.

Sample query:

```kql
// IN : orders shipped to any of a set of cities.
FIND orders o
FILTER o.ship_city IN ('London', 'Paris')
FETCH o.order_id, o.ship_city
```

### Generated SQL

**all dialects**

```sql
-- IN : orders shipped to any of a set of cities.
SELECT
  o.order_id
, o.ship_city
FROM
 orders o
WHERE
  o.ship_city IN ('London', 'Paris')
```


## DISTINCT

`left DISTINCT right` → BOOLEAN

True if *left* and *right* hold different values, **counting a missing value as a difference** — and two missing values as equal. Unlike `<>`, the result is never unknown, so a row is never dropped just because a value is absent.

This is the same distinctness `FETCH DISTINCT` applies when it collapses duplicate rows: there too, two blanks count as the same value. Write `NOT a DISTINCT b` for the opposite question — do these match, treating two blanks as matching. Prefer `=` and `<>` when the columns cannot be empty: a null-safe comparison is not a plain equality test, so on some engines it can stop an index being used where `=` would use one — worth checking on your own data before reaching for it by default.

Operands: any type.

Sample query:

```kql
// DISTINCT : null-safe difference — a missing value counts as a difference, and two missing values
// count as equal. The same distinctness FETCH DISTINCT uses when it collapses duplicate rows.
// Negated here to ask the opposite: which French orders ship to the customer's own region? Both are
// blank for every French customer, so <> would return nothing — two unknowns are never "different".
FIND customers c, orders o
FILTER c.country = 'France' AND NOT c.region DISTINCT o.ship_region
FETCH c.customer_id, c.region customer_region, o.ship_region order_region
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · sqlite · trino**

```sql
-- DISTINCT : null-safe difference — a missing value counts as a difference, and two missing values
-- count as equal. The same distinctness FETCH DISTINCT uses when it collapses duplicate rows.
-- Negated here to ask the opposite: which French orders ship to the customer's own region? Both are
-- blank for every French customer, so <> would return nothing — two unknowns are never "different".
SELECT
  c.customer_id
, c.region AS customer_region
, o.ship_region AS order_region
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
WHERE
  c.country = 'France'
 AND
  c.region IS NOT DISTINCT FROM o.ship_region
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `c.region <=> o.ship_region` |
| oracle | `DECODE(c.region, o.ship_region, 0, 1) = 0` |


## ISNULL

`value ISNULL` → BOOLEAN

True if *value* is NULL.

Operands: any type.

Sample query:

```kql
// ISNULL : orders not yet shipped.
FIND orders o
FILTER o.shipped_date ISNULL
FETCH o.order_id, o.shipped_date
```

### Generated SQL

**all dialects**

```sql
-- ISNULL : orders not yet shipped.
SELECT
  o.order_id
, o.shipped_date
FROM
 orders o
WHERE
  o.shipped_date IS NULL
```

