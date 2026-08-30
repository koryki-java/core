---
menuTitle: "Data Type Formatting Functions"
parent: "FUNCTIONS"
order: 7
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Data Type Formatting Functions

Two functions, and both are really about the mask: `to_char` turns a date or timestamp into text,
`to_number` reads a number back out of text.

## The datetime mask is written once

Unlike a regular-expression pattern, a format mask **is** translated. You write it in KQL's
vocabulary and each database receives its own spelling — strftime codes for DuckDB and SQLite,
`DATE_FORMAT` codes for MariaDB and Trino, date-part expressions for SQL Server. PostgreSQL and
Oracle need no rewriting because the vocabulary is theirs to begin with.

    YYYY  YY          year
    MM                month
    DD                day
    HH24  HH12  HH    hour — HH is the 12-hour clock
    MI    SS          minute, second
    AM    PM          meridiem

    to_char(o.order_date, 'YYYY-MM')        →  2024-07
    to_char(o.delivered_at, 'DD.MM.YYYY HH24:MI')

Tokens are matched exactly as written: `YYYY`, not `yyyy`. Text that should survive untouched goes
in double quotes — `'YYYY "week" WW'`.

## There are no month or weekday names

Deliberately. They were in the vocabulary once and gave five different answers for the same day:
`July` on three databases, `JULY` padded to nine characters on PostgreSQL, `JULI` on Oracle, `Juli`
on Trino — and an empty column on SQLite, whose strftime has no such code at all. Two of them
answered in the language of whoever ran the query, not in one the query chose.

A mask containing `MONTH`, `MON`, `DAY` or `DY` is now rejected with a message that says so. Use
`MM` and `DD` for the numbers and render the name where you know the language you are writing in —
which is the application, not the database.

## to_number is a different vocabulary, and not portable

`to_number` parses text into a number using a **numeric** template — `9` for a digit, `S` for a
sign — and that mask is *not* translated; it goes to the database as written. Only PostgreSQL,
Oracle and Snowflake offer the function at all; the other five declare it unsupported.

    to_number('  42', '9999')   →  42
    to_number('-17', 'S99')     →  -17

It earns its place where the text carries formatting a plain cast would choke on. If the text is
already a bare number, `to_integer` or `to_decimal` is the simpler and portable answer.

## to_char

`to_char(value: any, format: TEXT)` → TEXT

Formats a date or timestamp as text using the *format* mask.

The mask is written **once**, in KQL's vocabulary, and translated for each database — you do not write the target database's codes:

| | |
|---|---|
| `YYYY` `YY` | year |
| `MM` | month |
| `DD` | day |
| `HH24` `HH12` `HH` | hour, 24- or 12-clock (`HH` is the 12-clock) |
| `MI` `SS` | minute, second |
| `AM` `PM` | meridiem indicator |

There are deliberately **no name tokens** for months or weekdays. Measured, they gave five different answers for the same day — `July`, `JULY` padded to nine characters, `JULI`, `Juli` — and an empty column on SQLite, whose strftime has no such code at all. A mask containing one is rejected rather than answered five ways; render the name in the application, which knows the language it writes in.

Text to keep verbatim goes in double quotes: `'YYYY "week" WW'`. Tokens are matched **exactly as written** — `YYYY`, not `yyyy` — and anything unrecognised is passed through untouched, so a mistyped token becomes literal text instead of raising an error.

| Argument | Type | Description |
|---|---|---|
| value | any | the date or timestamp to format |
| format | TEXT | the format mask, written once in KQL's own vocabulary |

Sample query:

```kql
// to_char: format the order date as YYYY-MM.
FIND orders o
FETCH to_char(o.order_date, 'YYYY-MM') ym
```

### Generated SQL

**oracle · snowflake · postgresql**

```sql
-- to_char: format the order date as YYYY-MM.
SELECT
  to_char(o.order_date, 'YYYY-MM') AS ym
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb | `strftime(o.order_date, '%Y-%m') AS ym` |
| mariadb | `DATE_FORMAT(o.order_date, '%Y-%m') AS ym` |
| mssql | `CONCAT(CAST(YEAR(o.order_date) AS VARCHAR(4)), '-', RIGHT('0' + CAST(MONTH(o.order_date) AS VARCHAR(2)), 2)) AS ym` |
| sqlite | `strftime('%Y-%m', o.order_date) AS ym` |
| trino | `date_format(o.order_date, '%Y-%m') AS ym` |


## to_number

`to_number(value: TEXT, format: TEXT)` → DECIMAL

Parses a string into a number using a numeric *format* mask.

This is **not** the datetime vocabulary above and is not translated: the mask goes to the database as written. Only PostgreSQL, Oracle and Snowflake offer the function at all — DuckDB, MariaDB, SQL Server, SQLite and Trino declare it unsupported, so a query using it is not portable.

| Argument | Type | Description |
|---|---|---|
| value | TEXT | the text to parse into a number |
| format | TEXT | a numeric template such as `999D99` |

Sample query:

```kql
// to_number: read a number out of formatted text — leading blanks and a sign belong to the mask.
// Only PostgreSQL, Oracle and Snowflake offer the function; the other five declare it unsupported.
FIND customers c
FILTER c.customer_id = 'ALFKI'
FETCH to_number('1234', '9999') plain, to_number('  42', '9999') padded,
      to_number('-17', 'S99') signed
```

### Generated SQL

**oracle · snowflake · postgresql**

```sql
-- to_number: read a number out of formatted text — leading blanks and a sign belong to the mask.
-- Only PostgreSQL, Oracle and Snowflake offer the function; the other five declare it unsupported.
SELECT
  to_number('1234', '9999') AS plain
, to_number('  42', '9999') AS padded
, to_number('-17', 'S99') AS signed
FROM
 customers c
WHERE
  c.customer_id = 'ALFKI'
```

Unsupported: **duckdb**, **mariadb**, **mssql**, **sqlite**, **trino**

