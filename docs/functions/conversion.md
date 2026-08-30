---
menuTitle: "Type Conversion"
parent: "FUNCTIONS"
order: 11
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Type Conversion

Turning one type into another. There is a function per target type — `to_date`, `to_integer`,
`to_text` — rather than one `cast(value, type)`, because the target is not a value: it cannot come
from a column or be passed in, so it belongs in the name.

## The one that surprises people: integers round

    to_integer(1.7)     →   2
    to_integer(-1.7)    →  -2
    to_integer(2.5)     →   3

**Rounded, not cut off**, and half-way values go away from zero. `to_bigint` and `to_smallint`
behave the same. If a value must never be inflated — a fee, a quota, an invoice line — apply
`trunc` first and convert afterwards:

    to_integer(trunc(od.unit_price))

This is worth stating plainly because SQL itself does not agree with itself here: left alone,
SQLite and SQL Server cut the fraction off while every other engine rounds it. KQL levels that, so
the same query gives the same number everywhere.

## Text in, text out

`to_text` converts anything to text; the reverse direction is where it gets particular. `to_date`,
`to_time` and `to_timestamp` accept text only in ISO form — `"2023-01-31"`, `"14:30:00"`,
`"2023-01-31 14:30:00"`. Anything else is up to the database, which is a polite way of saying it may
work on one and fail on the next. For a text layout that is not ISO, use `parse_date`,
`parse_time` or `parse_timestamp` with an explicit mask; that is what they are for.

## Where you must say how wide

Two conversions take a size, and it is not pedantry:

    to_decimal(value, 10, 2)      10 significant digits, 2 after the point
    to_varchar(value, 40)         at most 40 characters

A decimal without a scale and a varchar without a length mean different things on different
databases — different default precision, different silent truncation. Asking is better than
guessing on the user's behalf.

## One caveat on booleans

`to_boolean` treats `0` as false and anything else as true, everywhere. What comes *back* differs:
databases with a native boolean type answer `true`/`false`, while MariaDB and SQLite have no such
type and answer `1`/`0`. The truth value agrees; only the notation does not. Compare the result
rather than matching it against the text `'true'`.

## to_date

`to_date(value: date/time | text)` → DATE

Converts a timestamp or date-compatible value to a DATE, discarding any time component. Text input must be ISO 8601 (`YYYY-MM-DD`); other formats are dialect-dependent — use `parse_date` for an explicit format mask.

| Argument | Type | Description |
|---|---|---|
| value | date/time \| text | the value to convert |

Sample query:

```kql
// to_date: drop the time part of a TIMESTAMP.
FIND check_type c
FETCH to_date(c.type_timestamp) as_date
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · trino**

```sql
-- to_date: drop the time part of a TIMESTAMP.
SELECT
  CAST(c.type_timestamp AS DATE) AS as_date
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite | `date(c.type_timestamp) AS as_date` |


## to_time

`to_time(value: date/time | text)` → TIME

Converts a timestamp or time-compatible value to a TIME, discarding the date part. Text input must be in `HH:MM:SS` format.

| Argument | Type | Description |
|---|---|---|
| value | date/time \| text | the value to convert |

Sample query:

```kql
// to_time: keep the time part of a TIMESTAMP.
FIND check_type c
FETCH to_time(c.type_timestamp) as_time
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · mariadb · trino**

```sql
-- to_time: keep the time part of a TIMESTAMP.
SELECT
  CAST(c.type_timestamp AS TIME) AS as_time
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle | `TO_CHAR(c.type_timestamp, 'HH24:MI:SS') AS as_time` |
| sqlite | `time(c.type_timestamp) AS as_time` |


## to_timestamp

`to_timestamp(value: date/time | text)` → TIMESTAMP

Converts a date or text value to a TIMESTAMP. Text input must be ISO 8601 (`YYYY-MM-DD HH:MM:SS`); a DATE is extended with midnight.

| Argument | Type | Description |
|---|---|---|
| value | date/time \| text | the value to convert |

Sample query:

```kql
// to_timestamp: extend a DATE to midnight.
FIND check_type c
FETCH to_timestamp(c.type_date) as_ts
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · trino**

```sql
-- to_timestamp: extend a DATE to midnight.
SELECT
  CAST(c.type_date AS TIMESTAMP) AS as_ts
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(c.type_date AS DATETIME) AS as_ts` |
| mssql | `CAST(c.type_date AS DATETIME2) AS as_ts` |
| sqlite | `datetime(c.type_date) AS as_ts` |


## to_boolean

`to_boolean(value: numeric | text)` → BOOLEAN

Converts a numeric value to BOOLEAN: `0` is false, anything else is true.

**What comes back is not spelled the same everywhere.** Databases with a native boolean type return `true`/`false`; MariaDB and SQLite have none and return `1`/`0` instead. The truth value is the same, the notation is not — so compare the result rather than matching it against the text `'true'`.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_boolean: zero is false, non-zero is true.
FIND check_type c
FETCH to_boolean(c.type_integer) flag
```

### Generated SQL

**duckdb · oracle · snowflake · trino**

```sql
-- to_boolean: zero is false, non-zero is true.
SELECT
  CAST(c.type_integer AS BOOLEAN) AS flag
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql · mariadb · sqlite | `(c.type_integer <> 0) AS flag` |
| mssql | `CAST(c.type_integer AS BIT) AS flag` |


## to_text

`to_text(value: BLOB)` → TEXT

Converts a BLOB *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | BLOB | the value to convert to text |

`to_text(value: BOOLEAN)` → TEXT

Converts a BOOLEAN *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | BOOLEAN | the value to convert to text |

`to_text(value: DATE)` → TEXT

Converts a DATE *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | DATE | the value to convert to text |

`to_text(value: DECIMAL)` → TEXT

Converts a DECIMAL *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | DECIMAL | the value to convert to text |

`to_text(value: FLOAT)` → TEXT

Converts a FLOAT *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | FLOAT | the value to convert to text |

`to_text(value: INTEGER)` → TEXT

Converts a INTEGER *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | INTEGER | the value to convert to text |

`to_text(value: TIME)` → TEXT

Converts a TIME *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | TIME | the value to convert to text |

`to_text(value: DURATION)` → TEXT

Converts a DURATION *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | DURATION | the value to convert to text |

`to_text(value: TIMESTAMP)` → TEXT

Converts a TIMESTAMP *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | TIMESTAMP | the value to convert to text |

`to_text(value: TEXT)` → TEXT

Converts a TEXT *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | TEXT | the value to convert to text |

`to_text(value: JSON)` → TEXT

Converts a JSON *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | JSON | the value to convert to text |

`to_text(value: UUID)` → TEXT

Converts a UUID *value* to TEXT.

| Argument | Type | Description |
|---|---|---|
| value | UUID | the value to convert to text |

Sample query:

```kql
// to_text: render an integer column as text.
FIND check_type c
FETCH to_text(c.type_integer) integer_text
```

### Generated SQL

**duckdb · snowflake · postgresql · sqlite**

```sql
-- to_text: render an integer column as text.
SELECT
  CAST(c.type_integer AS TEXT) AS integer_text
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(c.type_integer AS CHAR) AS integer_text` |
| mssql | `CAST(c.type_integer AS VARCHAR(MAX)) AS integer_text` |
| oracle | `TO_CHAR(c.type_integer, 'TM9', 'NLS_NUMERIC_CHARACTERS = ''.,''') AS integer_text` |
| trino | `CAST(c.type_integer AS VARCHAR) AS integer_text` |


## to_float

`to_float(value: numeric | text)` → FLOAT

Converts a numeric or text value to single-precision float. Fractional precision may be lost relative to the source.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_float: to single-precision floating point.
FIND check_type c
FETCH to_float(c.type_decimal) as_float
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · sqlite**

```sql
-- to_float: to single-precision floating point.
SELECT
  CAST(c.type_decimal AS FLOAT) AS as_float
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| trino | `CAST(c.type_decimal AS REAL) AS as_float` |


## to_double

`to_double(value: numeric | text)` → DOUBLE

Converts a numeric or text value to double-precision float.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_double: to double-precision floating point.
FIND check_type c
FETCH to_double(c.type_decimal) as_double
```

### Generated SQL

**duckdb · snowflake · mariadb · sqlite · trino**

```sql
-- to_double: to double-precision floating point.
SELECT
  CAST(c.type_decimal AS DOUBLE) AS as_double
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CAST(c.type_decimal AS FLOAT) AS as_double` |
| oracle | `CAST(c.type_decimal AS BINARY_DOUBLE) AS as_double` |
| postgresql | `CAST(c.type_decimal AS DOUBLE PRECISION) AS as_double` |


## to_integer

`to_integer(value: numeric | text)` → INTEGER

Converts a numeric or text value to a 32-bit integer. A fractional part is **rounded**, not truncated, and a half-way value rounds away from zero — `1.7` becomes `2`, `-1.7` becomes `-2`, `2.5` becomes `3`. Use `trunc` first if the value must never be inflated. Overflow behaviour is dialect-defined.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_integer: truncate a decimal to a 32-bit integer.
FIND check_type c
FETCH to_integer(c.type_decimal) as_int
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · trino**

```sql
-- to_integer: truncate a decimal to a 32-bit integer.
SELECT
  CAST(c.type_decimal AS INTEGER) AS as_int
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CAST(ROUND(c.type_decimal, 0) AS INT) AS as_int` |
| sqlite | `CAST(ROUND(c.type_decimal, 0) AS INTEGER) AS as_int` |


## to_bigint

`to_bigint(value: numeric | text)` → BIGINT

Converts a numeric or text value to a 64-bit integer. Rounds like `to_integer`; use it instead when values may exceed ±2 147 483 647.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_bigint: to a 64-bit integer.
FIND check_type c
FETCH to_bigint(c.type_integer) as_bigint
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- to_bigint: to a 64-bit integer.
SELECT
  CAST(c.type_integer AS BIGINT) AS as_bigint
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(c.type_integer AS SIGNED) AS as_bigint` |
| mssql | `CAST(ROUND(c.type_integer, 0) AS BIGINT) AS as_bigint` |
| oracle | `CAST(c.type_integer AS NUMBER(19)) AS as_bigint` |
| sqlite | `CAST(ROUND(c.type_integer, 0) AS INTEGER) AS as_bigint` |


## to_smallint

`to_smallint(value: numeric | text)` → SMALLINT

Converts a numeric or text value to a 16-bit integer (range −32 768 to 32 767). Rounds like `to_integer`. Overflow behaviour is dialect-defined.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert |

Sample query:

```kql
// to_smallint: to a 16-bit integer.
FIND check_type c
FETCH to_smallint(c.type_smallint) as_smallint
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · trino**

```sql
-- to_smallint: to a 16-bit integer.
SELECT
  CAST(c.type_smallint AS SMALLINT) AS as_smallint
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(c.type_smallint AS SIGNED) AS as_smallint` |
| mssql | `CAST(ROUND(c.type_smallint, 0) AS SMALLINT) AS as_smallint` |
| sqlite | `CAST(ROUND(c.type_smallint, 0) AS INTEGER) AS as_smallint` |


## to_decimal

`to_decimal(value: numeric | text, precision: INTEGER, scale: INTEGER)` → argument-dependent

Converts *value* to a fixed-point decimal. *precision* is the total number of significant digits, *scale* the digits after the point — `to_decimal(value, 10, 2)` holds up to 99 999 999.99.

| Argument | Type | Description |
|---|---|---|
| value | numeric \| text | the value to convert to a decimal |
| precision | INTEGER | total number of significant digits |
| scale | INTEGER | number of digits after the decimal point |

Sample query:

```kql
// to_decimal: fixed-point with precision and scale.
FIND check_type c
FETCH to_decimal(c.type_double, 10, 2) as_money
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb · trino**

```sql
-- to_decimal: fixed-point with precision and scale.
SELECT
  CAST(c.type_double AS DECIMAL(10, 2)) AS as_money
FROM
 check_type c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite | `ROUND(c.type_double, 2) AS as_money` |


## to_varchar

`to_varchar(value: any, length: INTEGER)` → TEXT

Converts *value* to a variable-length string with an explicit maximum length — useful when the target column has a defined width.

| Argument | Type | Description |
|---|---|---|
| value | any | the value to convert to text |
| length | INTEGER | maximum length of the resulting string |

Sample query:

```kql
// to_varchar: render a value as bounded text.
FIND check_type c
FETCH to_varchar(c.type_integer, 40) as_text
```

### Generated SQL

**all dialects**

```sql
-- to_varchar: render a value as bounded text.
SELECT
  CAST(c.type_integer AS VARCHAR(40)) AS as_text
FROM
 check_type c
```

