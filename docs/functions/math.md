---
menuTitle: "Mathematical Functions"
parent: "FUNCTIONS"
order: 4
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Mathematical Functions

The everyday numeric toolkit: rounding, roots and powers, logarithms, and the row-wise `greatest` /
`least`. Operators (`+`, `-`, `*`, `/`) live on the *Arithmetic Operators* page; everything here is
written as a function call, deliberately — `mod(x, 2)` reads better than `x % 2` for an audience that
reads `%` as "percent".

## Rounding: round, floor, ceil, trunc

Four ways to drop decimals, and the difference matters:

    round(19.99)      →  20     nearest
    floor(19.99)      →  19     always down
    ceil(19.01)       →  20     always up
    trunc(19.99)      →  19     always toward zero

`floor` and `trunc` agree on positive numbers and part company on negatives: `floor(-1.5)` is `-2`
(further down), `trunc(-1.5)` is `-1` (toward zero). Use `trunc` when a value must never be inflated
— a fee, a quota — and `round` when it should simply be nearest.

`round` and `trunc` both take an optional number of decimal places: `round(x, 2)`, `trunc(x, 2)`.

**One caveat, unavoidable.** Half-way rounding of values held as binary floats is decided by the
database, not by KQL: `0.15` is stored as `0.149999…`, so an engine may round it either way. Where
that matters, keep the value a decimal rather than a float.

## Logarithms: ln, log10, log

There are three, and the naming is deliberate:

    ln(x)          natural logarithm, base e
    log10(x)       base 10
    log(base, x)   any base — log(2, 8) is 3

**There is no one-argument `log`,** and that is not an omission. Every engine accepts `log(100)` and
they do not agree what it means: PostgreSQL and SQLite read it as base 10 and return `2`, MariaDB and
SQL Server read it as the natural log and return `4.605`. A name that means two things cannot be
fixed by translating it, only by not offering it — so say which one you want, and `ln` or `log10`
will mean the same thing on every database.

With two arguments the **base comes first**, as it reads aloud: "log base 2 of 8".

## greatest and least

These compare **across the columns of one row** — not down a column:

    FIND order_details od
    FETCH greatest(od.unit_price, 20.0) at_least_twenty

That is the row's own price, or 20 if the price is lower. The functions that collapse many rows into
one are `max` and `min`, on the *Aggregate Functions* page. It is an easy pair to confuse, and the
two answer completely different questions.

**One caveat, and this one is not cosmetic.** As soon as an argument can be blank, the engines part
company. With `greatest(NULL, 3)`, PostgreSQL, DuckDB and SQL Server answer `3` — they ignore blanks and take the
largest of the rest — while MariaDB, Oracle, Snowflake, SQLite and Trino answer blank, because the SQL
standard lets a blank win the moment it appears. Both camps are defensible; PostgreSQL documents its
deviation openly.

KQL does not paper over it. Levelling it would mean rewriting one camp's SQL, and with a variable
number of arguments that needs a replacement construct rather than a translation — a lot of
machinery to hide a difference the query itself can state. So say which you mean: wrap each argument
in `coalesce` with a neutral value to ignore blanks, or keep the blanks in the call and let them win
everywhere. If the arguments cannot be blank, none of this applies and the functions are portable.

## Division and remainders

`mod(x, y)` is the remainder. Like `/`, **a zero divisor yields a blank rather than an error** — so a
report will not fail on one bad row. This is worth knowing because SQL itself is wildly inconsistent
here: left alone, three of the supported engines raise an error, three return a blank, and Oracle
returns the dividend unchanged, which looks like a real answer and is not one.

## Trigonometry

`sin`, `cos`, `tan`, `asin`, `acos` and `atan` are present and take **radians**. They are not the
common case for this audience, and the wider set — hyperbolics, `cot`, `atan2`, `degrees`/`radians`
— is deliberately not included; see `math-review.md` for the measurements behind that.

## abs

`abs(value: numeric)` → argument-dependent

Absolute value.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to take the absolute value of |

Sample query:

```kql
// abs: absolute value of a number.
FIND orders o
FETCH abs(-17.4) magnitude
```

### Generated SQL

**all dialects**

```sql
-- abs: absolute value of a number.
SELECT
  abs(-17.4) AS magnitude
FROM
 orders o
```


## ceil

`ceil(value: numeric)` → argument-dependent

Nearest integer greater than or equal to *value*.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to round up |

Sample query:

```kql
// ceil: round freight up to a whole number.
FIND orders o
FETCH ceil(o.freight) rounded_up
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- ceil: round freight up to a whole number.
SELECT
  ceil(o.freight) AS rounded_up
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CEILING(o.freight) AS rounded_up` |


## floor

`floor(value: numeric)` → argument-dependent

Nearest integer less than or equal to *value*.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to round down |

Sample query:

```kql
// floor: round freight down to a whole number.
FIND orders o
FETCH floor(o.freight) rounded_down
```

### Generated SQL

**all dialects**

```sql
-- floor: round freight down to a whole number.
SELECT
  floor(o.freight) AS rounded_down
FROM
 orders o
```


## round

`round(value: numeric)` → argument-dependent

Rounds to the nearest whole number.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to round |

`round(value: numeric, scale: INTEGER)` → argument-dependent

Rounds to *scale* decimal places.

Half-way rounding of binary-float inputs is engine-defined: a value such as `0.15` is stored as `0.149999…`, so engines may round it up or down.

See: [What Every Computer Scientist Should Know About Floating-Point Arithmetic](https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to round |
| scale | INTEGER | number of decimal places to keep |

Sample query:

```kql
// round: order freight rounded to one decimal place.

FIND orders o
FILTER o.customer_id = 'TOMSP'
FETCH round(o.freight, 1) freight_rounded
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- round: order freight rounded to one decimal place.
SELECT
  round(o.freight, 1) AS freight_rounded
FROM
 orders o
WHERE
  o.customer_id = 'TOMSP'
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(o.freight AS numeric), 1) AS freight_rounded` |


## trunc

`trunc(value: numeric)` → argument-dependent

Truncates toward zero — 1.99 becomes 1, and -1.99 becomes -1. Unlike `round` it never carries, so use it where a value must not be inflated and `round` where it should be nearest.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to truncate |

`trunc(value: numeric, scale: INTEGER)` → argument-dependent

Truncates toward zero, keeping *scale* decimal places.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to truncate |
| scale | INTEGER | number of decimal places to keep |

Sample query:

```kql
// trunc: truncate toward zero, never rounding up.
FIND order_details od
FETCH trunc(od.unit_price) whole_price, trunc(19.99, 1) one_decimal
```

### Generated SQL

**duckdb · oracle · snowflake**

```sql
-- trunc: truncate toward zero, never rounding up.
SELECT
  trunc(od.unit_price) AS whole_price
, trunc(19.99, 1) AS one_decimal
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `, trunc(CAST(19.99 AS numeric), 1) AS one_decimal` |
| sqlite | `, (CAST(19.99 * pow(10, 1) AS INTEGER) / pow(10, 1)) AS one_decimal` |

**mariadb**

```sql
-- trunc: truncate toward zero, never rounding up.
SELECT
  TRUNCATE(od.unit_price, 0) AS whole_price
, TRUNCATE(19.99, 1) AS one_decimal
FROM
 order_details od
```

**mssql**

```sql
-- trunc: truncate toward zero, never rounding up.
SELECT
  ROUND(od.unit_price, 0, 1) AS whole_price
, ROUND(19.99, 1, 1) AS one_decimal
FROM
 order_details od
```

**trino**

```sql
-- trunc: truncate toward zero, never rounding up.
SELECT
  truncate(od.unit_price) AS whole_price
, truncate(CAST(19.99 AS DECIMAL(38,10)), 1) AS one_decimal
FROM
 order_details od
```


## mod

`mod(dividend: numeric, divisor: numeric)` → argument-dependent

Remainder of *dividend* / *divisor*. A zero *divisor* yields no value rather than an error, as with `/`.

| Argument | Type | Description |
|---|---|---|
| dividend | numeric | the number being divided |
| divisor | numeric | the number to divide by |

Sample query:

```kql
// mod: order id modulo 7.
FIND orders o
FETCH mod(o.order_id, 7) bucket
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- mod: order id modulo 7.
SELECT
  mod(o.order_id, NULLIF(7, 0)) AS bucket
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `((o.order_id) % NULLIF((7), 0)) AS bucket` |


## sign

`sign(value: numeric)` → INTEGER

Sign of the argument: -1, 0 or 1.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number whose sign is taken |


## greatest

`greatest(value: ORDERED, more: ORDERED, ...)` → argument-dependent

Largest of the arguments, compared across the columns of one row — not to be confused with `max`, which collapses rows into one.

**Not portable when an argument can be NULL.** The SQL standard lets NULL win: the result is NULL as soon as any argument is NULL. PostgreSQL deviates and ignores NULL arguments, returning the largest (or smallest) of the rest.

With one NULL argument — `greatest(NULL, 3)`:

| Result | Dialects |
|---|---|
| `3` — NULL ignored | duckdb, mssql, postgresql |
| `NULL` — NULL wins | mariadb, oracle, snowflake, sqlite, trino |

koryki does not paper over this: unifying it would mean rewriting one side, and with a variable number of arguments only through a replacement construct. Make the intent explicit instead — wrap each argument in `coalesce` with a neutral value to ignore NULLs, or keep them out of the call to let NULL win everywhere.

See: [PostgreSQL — GREATEST and LEAST](https://www.postgresql.org/docs/current/functions-conditional.html).

| Argument | Type | Description |
|---|---|---|
| value | ORDERED | the values to compare |
| more | ORDERED | further values to compare against |

Sample query:

```kql
// greatest: the larger of two values in the same row.
FIND order_details od
FETCH round(greatest(od.unit_price, 20.0), 2) at_least_twenty
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · trino**

```sql
-- greatest: the larger of two values in the same row.
SELECT
  round(greatest(od.unit_price, 20), 2) AS at_least_twenty
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(greatest(od.unit_price, 20) AS numeric), 2) AS at_least_twenty` |
| sqlite | `round(max(od.unit_price, 20), 2) AS at_least_twenty` |


## least

`least(value: ORDERED, more: ORDERED, ...)` → argument-dependent

Smallest of the arguments, compared across the columns of one row — the mirror of `greatest`, and not to be confused with `min`.

**Not portable when an argument can be NULL.** The SQL standard lets NULL win: the result is NULL as soon as any argument is NULL. PostgreSQL deviates and ignores NULL arguments, returning the largest (or smallest) of the rest.

With one NULL argument — `greatest(NULL, 3)`:

| Result | Dialects |
|---|---|
| `3` — NULL ignored | duckdb, mssql, postgresql |
| `NULL` — NULL wins | mariadb, oracle, snowflake, sqlite, trino |

koryki does not paper over this: unifying it would mean rewriting one side, and with a variable number of arguments only through a replacement construct. Make the intent explicit instead — wrap each argument in `coalesce` with a neutral value to ignore NULLs, or keep them out of the call to let NULL win everywhere.

See: [PostgreSQL — GREATEST and LEAST](https://www.postgresql.org/docs/current/functions-conditional.html).

| Argument | Type | Description |
|---|---|---|
| value | ORDERED | the values to compare |
| more | ORDERED | further values to compare against |

Sample query:

```kql
// least: the smaller of two values in the same row.
FIND order_details od
FETCH least(od.quantity, 10) capped_quantity
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · trino**

```sql
-- least: the smaller of two values in the same row.
SELECT
  least(od.quantity, 10) AS capped_quantity
FROM
 order_details od
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite | `min(od.quantity, 10) AS capped_quantity` |


## power

`power(base: numeric, exponent: numeric)` → FLOAT

*base* raised to the power of *exponent*.

| Argument | Type | Description |
|---|---|---|
| base | numeric | the number to raise |
| exponent | numeric | the power to raise the base to |

Sample query:

```kql
// power: freight squared.
FIND orders o
FETCH power(o.freight, 2) squared
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- power: freight squared.
SELECT
  power(o.freight, 2) AS squared
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `POWER(CAST(o.freight AS FLOAT), 2) AS squared` |

> Results differ from the other dialects on **oracle**, **postgresql**.


## sqrt

`sqrt(value: numeric)` → FLOAT

Square root.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to take the square root of |

Sample query:

```kql
// sqrt: square root of freight.
FIND orders o
FETCH sqrt(o.freight) root
```

### Generated SQL

**all dialects**

```sql
-- sqrt: square root of freight.
SELECT
  sqrt(o.freight) AS root
FROM
 orders o
```


## exp

`exp(value: numeric)` → FLOAT

Exponential — e raised to *value*.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the exponent to raise e to |


## ln

`ln(value: numeric)` → FLOAT

Natural logarithm — the logarithm to base e.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to take the natural logarithm of |

Sample query:

```kql
// ln: natural logarithm.
FIND orders o
FETCH round(ln(100), 6) natural_log
```

### Generated SQL

**duckdb · oracle · snowflake · mariadb · sqlite · trino**

```sql
-- ln: natural logarithm.
SELECT
  round(ln(100), 6) AS natural_log
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `round(LOG(100), 6) AS natural_log` |
| postgresql | `round(CAST(ln(100) AS numeric), 6) AS natural_log` |


## log10

`log10(value: numeric)` → FLOAT

Base-10 logarithm.

| Argument | Type | Description |
|---|---|---|
| value | numeric | the number to take the base-10 logarithm of |

Sample query:

```kql
// log10: base-10 logarithm.
FIND orders o
FETCH log10(1000) base_ten_log
```

### Generated SQL

**duckdb · mssql · postgresql · mariadb · sqlite · trino**

```sql
-- log10: base-10 logarithm.
SELECT
  log10(1000) AS base_ten_log
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · snowflake | `LOG(10, 1000) AS base_ten_log` |


## log

`log(base: numeric, value: numeric)` → FLOAT

Logarithm of *value* to the given *base*: log(2, 8) is 3. The base comes first, as it reads aloud.

| Argument | Type | Description |
|---|---|---|
| base | numeric | the base of the logarithm |
| value | numeric | the number to take the logarithm of |

Sample query:

```kql
// log: logarithm to an explicit base — the base comes first.
// T-SQL takes LOG(value, base), the other way round from every other engine, so this
// pins the value rather than just the rendering: log(2, 8) must be 3 everywhere.
FIND orders o
FETCH log(2, 8) log_base_two
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- log: logarithm to an explicit base — the base comes first.
-- T-SQL takes LOG(value, base), the other way round from every other engine, so this
-- pins the value rather than just the rendering: log(2, 8) must be 3 everywhere.
SELECT
  log(2, 8) AS log_base_two
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `LOG(8, 2) AS log_base_two` |


## sin

`sin(value: numeric)` → FLOAT

Trigonometric sin (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// sin: trigonometric sine, argument in radians.
FIND orders o
FETCH round(sin(0), 6) sine_of_zero
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- sin: trigonometric sine, argument in radians.
SELECT
  round(sin(0), 6) AS sine_of_zero
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(sin(0) AS numeric), 6) AS sine_of_zero` |


## cos

`cos(value: numeric)` → FLOAT

Trigonometric cos (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// cos: trigonometric cosine, argument in radians.
FIND orders o
FETCH round(cos(0), 6) cosine_of_zero
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- cos: trigonometric cosine, argument in radians.
SELECT
  round(cos(0), 6) AS cosine_of_zero
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(cos(0) AS numeric), 6) AS cosine_of_zero` |


## tan

`tan(value: numeric)` → FLOAT

Trigonometric tan (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// tan: trigonometric tangent, argument in radians.
FIND orders o
FETCH round(tan(0), 6) tangent_of_zero
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- tan: trigonometric tangent, argument in radians.
SELECT
  round(tan(0), 6) AS tangent_of_zero
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(tan(0) AS numeric), 6) AS tangent_of_zero` |


## asin

`asin(value: numeric)` → FLOAT

Trigonometric asin (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// asin: inverse sine, result in radians.
FIND orders o
FETCH round(asin(1), 6) arcsine_of_one
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- asin: inverse sine, result in radians.
SELECT
  round(asin(1), 6) AS arcsine_of_one
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(asin(1) AS numeric), 6) AS arcsine_of_one` |


## acos

`acos(value: numeric)` → FLOAT

Trigonometric acos (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// acos: inverse cosine, result in radians.
FIND orders o
FETCH round(acos(1), 6) arccosine_of_one
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- acos: inverse cosine, result in radians.
SELECT
  round(acos(1), 6) AS arccosine_of_one
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(acos(1) AS numeric), 6) AS arccosine_of_one` |


## atan

`atan(value: numeric)` → FLOAT

Trigonometric atan (argument in radians).

| Argument | Type | Description |
|---|---|---|
| value | numeric | the angle in radians |

Sample query:

```kql
// atan: inverse tangent, result in radians.
FIND orders o
FETCH round(atan(0), 6) arctangent_of_zero
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- atan: inverse tangent, result in radians.
SELECT
  round(atan(0), 6) AS arctangent_of_zero
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `round(CAST(atan(0) AS numeric), 6) AS arctangent_of_zero` |


## pi

`pi()` → FLOAT

Approximate value of π.

Sample query:

```kql
// pi: the mathematical constant pi.
FIND orders o
FETCH pi() pi_value
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · mariadb · sqlite · trino**

```sql
-- pi: the mathematical constant pi.
SELECT
  pi() AS pi_value
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle | `ACOS(-1) AS pi_value` |


## random

`random()` → FLOAT

Random value in the range 0.0 <= x < 1.0.

Sample query:

```kql
// random: a random value in [0, 1).
FIND orders o
FETCH random() r
```

### Generated SQL

**duckdb · postgresql · trino**

```sql
-- random: a random value in [0, 1).
SELECT
  random() AS r
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `RAND() AS r` |
| mssql | `RAND(CHECKSUM(NEWID())) AS r` |
| oracle | `DBMS_RANDOM.VALUE AS r` |
| snowflake | `UNIFORM(0::float, 1::float, RANDOM()) AS r` |
| sqlite | `(random() / 18446744073709551616.0 + 0.5) AS r` |

