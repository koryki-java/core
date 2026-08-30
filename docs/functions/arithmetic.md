---
menuTitle: "Arithmetic Operators"
parent: "FUNCTIONS"
order: 3
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Arithmetic Operators

The five arithmetic operators work on **numbers, dates and durations** — not on text. `+` is
addition, never string concatenation; use `concat` for that. Anything that is not a number, a
date/time value or a duration is rejected when the query is checked, rather than being coerced into
whatever that particular database happens to do with it.

    FIND order_details od
    FETCH od.unit_price * od.quantity gross

## Precedence

`*` and `/` bind tighter than `+` and `-`, as in ordinary arithmetic, and parentheses override that:

    a + b * c        →  a + (b * c)
    (a + b) * c      →  grouped as written

The grouping you write is the grouping that reaches the database. Parentheses are preserved through
the whole pipeline, so a query cannot be quietly re-associated on the way to SQL.

## Division

**Division always produces a decimal**, including when both operands are whole numbers:

    7 / 2   →   3.5

This is worth stating because SQL itself does not agree with it. Left to their own devices,
PostgreSQL, SQL Server, Trino and SQLite answer `3` — integer division truncates — while Oracle,
MariaDB and DuckDB answer `3.5`. The same query would give two different numbers
depending on where it ran, so KQL settles it: the result is decimal everywhere. Use `floor` or `round` when you
want a whole number back.

**Dividing by zero gives a blank, not an error.** A row whose divisor is zero yields no value for
that expression, and the rest of the report is unaffected:

    FIND order_details od
    FETCH od.unit_price / od.quantity unit_cost

If `quantity` is zero for some line, `unit_cost` is blank for that line only. Here too the databases
disagree natively — four raise an error, two return a blank, one returns infinity — so KQL settles
on the blank. That means a divide-by-zero will not fail a report, but it will not announce itself
either: if a zero divisor means your data is wrong, filter for it explicitly (`FILTER od.quantity >
0`) rather than relying on the query to complain.

Writing `/ 0` literally is a different matter and is rejected outright, since it can never have been
intended.

## Dates and durations

Arithmetic on dates and times follows its own algebra, described in full in `docs/TEMPORAL.md`. The
short version:

    o.order_date + 30d           →  a date, 30 days later
    o.shipped_date - o.order_date →  a duration in whole days
    3 * 2h                        →  6h

Subtracting two dates gives **whole days**, and subtracting two timestamps gives an exact elapsed
span in clock units — never calendar months, whose length is not fixed. When the calendar breakdown
is what you want, that is `calendar_distance`, not `-`. Multiplying a duration by a whole number
scales each of its components; dividing a duration is not defined.

## Decimal places

How many decimal places come back is the database's decision, not KQL's: the same product may print
as `77`, `77.0` or `77.000000000000` depending on the engine's own scale rules. The value is the
same. Use `round(x, 2)` when the presentation matters.

## Not here

There is no `%` operator — the remainder is the `mod` function, on the *Mathematical Functions*
page, along with `abs`, `floor`, `ceil`, `round`, `power` and `sqrt`.

## negate

`negate(value: ARITHMETIC)` → argument-dependent

Arithmetic negation (`-x`): negates a number, or flips the sign of every component of a DURATION.

| Argument | Type | Description |
|---|---|---|
| value | ARITHMETIC | the number or DURATION to negate |

Sample query:

```kql
// negate (-): arithmetic negation of freight.
FIND orders o
FETCH -o.freight neg_freight
```

### Generated SQL

**all dialects**

```sql
-- negate (-): arithmetic negation of freight.
SELECT
  -o.freight AS neg_freight
FROM
 orders o
```


## add

`add(left: ARITHMETIC, right: ARITHMETIC, ...)` → argument-dependent

Addition (`+`): numeric addition, or temporal arithmetic per the result-type algebra — DATE/TIMESTAMP/TIME + DURATION, DATE + TIME, and DURATION + DURATION (see docs/TEMPORAL.md).

| Argument | Type | Description |
|---|---|---|
| left | ARITHMETIC | the left-hand operand |
| right | ARITHMETIC | the right-hand operand |

Sample query:

```kql
// add (+): 30 days after the order date.
FIND orders o
FETCH o.order_date + 30d due_date
```

### Generated SQL

**duckdb · snowflake · postgresql**

```sql
-- add (+): 30 days after the order date.
SELECT
  o.order_date + INTERVAL '30 day' AS due_date
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb · trino | `o.order_date + INTERVAL '30' DAY AS due_date` |
| mssql | `DATEADD(DAY, 30, o.order_date) AS due_date` |
| oracle | `o.order_date + NUMTODSINTERVAL(30, 'DAY') AS due_date` |
| sqlite | `date(o.order_date, '+30 days') AS due_date` |


## minus

`minus(left: ARITHMETIC, right: ARITHMETIC, ...)` → argument-dependent

Subtraction (`-`): numeric subtraction, or temporal — DATE − DATE and TIMESTAMP − TIMESTAMP yield a DURATION, and a temporal value − DURATION shifts it (see docs/TEMPORAL.md).

| Argument | Type | Description |
|---|---|---|
| left | ARITHMETIC | the left-hand operand |
| right | ARITHMETIC | the right-hand operand |

Sample query:

```kql
// minus (-): shipping delay in whole days.
FIND orders o
FETCH o.shipped_date - o.order_date ship_delay
```

### Generated SQL

**duckdb · snowflake · postgresql**

```sql
-- minus (-): shipping delay in whole days.
SELECT
  o.shipped_date - o.order_date AS ship_delay
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `TIMESTAMPDIFF(DAY, o.order_date, o.shipped_date) AS ship_delay` |
| mssql | `DATEDIFF(DAY, o.order_date, o.shipped_date) AS ship_delay` |
| oracle | `TRUNC(o.shipped_date - o.order_date) AS ship_delay` |
| sqlite | `CAST(julianday(o.shipped_date) - julianday(o.order_date) AS INTEGER) AS ship_delay` |
| trino | `date_diff('day', o.order_date, o.shipped_date) AS ship_delay` |


## multiply

`multiply(left: ARITHMETIC, right: ARITHMETIC, ...)` → argument-dependent

Multiplication (`*`): numeric multiplication, or DURATION × integer scaled component-wise (see docs/TEMPORAL.md).

| Argument | Type | Description |
|---|---|---|
| left | ARITHMETIC | the left-hand operand |
| right | ARITHMETIC | the right-hand operand |

Sample query:

```kql
// multiply (*): gross amount of a line item.
FIND order_details od
FETCH round(od.unit_price * od.quantity, 2) gross
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- multiply (*): gross amount of a line item.
SELECT
  round(od.unit_price * od.quantity, 2) AS gross
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(od.unit_price * od.quantity AS numeric), 2) AS gross` |


## divide

`divide(left: ARITHMETIC, right: ARITHMETIC, ...)` → argument-dependent

Division (`/`): the result is always decimal, even when both operands are whole numbers (`7 / 2` is 3.5, never 3). A zero divisor yields no value rather than an error. Dividing a DURATION is not defined.

| Argument | Type | Description |
|---|---|---|
| left | ARITHMETIC | the left-hand operand |
| right | ARITHMETIC | the right-hand operand |

Sample query:

```kql
// divide (/): unit cost of a line item.
FIND order_details od
FETCH od.unit_price / od.quantity unit_cost
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · trino**

```sql
-- divide (/): unit cost of a line item.
SELECT
  od.unit_price / NULLIF(od.quantity, 0) AS unit_cost
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite | `CAST(od.unit_price AS REAL) / NULLIF(od.quantity, 0) AS unit_cost` |

> Results differ from the other dialects on **snowflake**, **sqlite**.

