---
menuTitle: "Date/Time Functions"
parent: "FUNCTIONS"
order: 8
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Date/Time Functions

The largest category, and the one where the databases agree least. KQL works with four temporal
kinds — `DATE`, `TIME`, `TIMESTAMP` and `INSTANT` — described in `docs/TEMPORAL.md`; this page is
about the functions over them.

## Where the engines disagreed, the answer is pinned

Most functions here render straight through. Two do not, and they are worth knowing about:

`dayofweek` counts **Monday as 1** and Sunday as 7, the ISO way. Left alone, the engines gave three
different numbers for the same Sunday — 0, 1 and 7 — and several took the answer from a session
setting (`NLS_TERRITORY`, `DATEFIRST`, `WEEK_START`), so the same query could differ between two
connections to the *same* database. `week` is ISO-8601 for the same reason: week 1 is the one
containing the first Thursday, which is why early January sometimes falls in week 52 or 53 of the
previous year.

Where an engine's own answer depends on a session setting, the dialect computes it from a fixed
anchor instead of asking the session.

## Prefer the named boundaries to date_trunc

`day_begin`, `week_begin`, `month_begin`, `quarter_begin`, `year_begin` and their `_end` partners
are the portable way to snap a value to a period. `date_trunc(part, value)` does the same for
`'day'`, `'month'`, `'quarter'` and `'year'` — and only those four, because that is the set every
dialect can express. Ask for `'hour'` and SQLite used to answer with a blank column rather than an
error; it now fails by name.

The `_end` functions return the last **day** of the period, always a date. That is deliberate:
"the last instant" has no value independent of precision. For a timestamp range, filter half-open
with `begin` and the next `begin`.

## year_month is the month as a key, month_begin is the month as a date

Two ways to bucket by month, and they are not interchangeable. `year_month` packs the two parts
into one number — `202401` — which sorts chronologically by itself, so a `GROUP year_month(...)`
needs no second column and no tie-break. `month_begin` answers with the first of the month, a real
date: use that one when the bucket has to join to a calendar table or take further date arithmetic.

What you should not do is group by `year` and `month` side by side. It needs two columns, and it
sorts wrong the moment one of them is left out of the ordering.

## Distances count whole units, toward zero

`days_between`, `months_between` and `years_between` follow `java.time`'s `ChronoUnit`: complete
units only, and the remainder is dropped in the direction of the sign.

    months_between('2023-01-31', '2023-03-30')  →  1    -- the second month is not complete

## Durations are literals, not function calls

    2d4h        two days and four hours
    1y2mo       one year and two months
    -2d4h       *both* components negated — 52 hours back, not 2 days back and 4 hours forward

Units are `ms s min h d w mo q y`, and they must be written largest-first (`1y2mo15d`, not
`15d2mo1y`); the other order is a positioned error rather than a silent reordering. A leading minus
applies to the whole literal, so mixed signs cannot be written — that is the one shape the notation
does not have.

Oracle is the exception worth naming: it keeps `YEAR TO MONTH` and `DAY TO SECOND` in separate
types, so a duration mixing the two (`1y2mo3d`) has no value there. Adding it to a date works —
that expands into steps — but using it *as* a value does not.

## now, clock_now and today

`now` is the statement's timestamp and does not move within it. `clock_now` reads the wall clock at
the moment of the call, so it may advance mid-statement on the engines that distinguish the two
(PostgreSQL `clock_timestamp()`, MariaDB `SYSDATE()`); elsewhere the two are equal. `today` is the
current date.

## Parsing, formatting and zones

`parse_date`, `parse_time` and `parse_timestamp` read text with a format mask, `to_char` writes it.
The mask is written once in KQL's vocabulary and translated per dialect — the tokens are on the
*Data Type Formatting Functions* page. SQLite has no mask-based parsing at all and SQL Server takes
culture or style codes rather than masks, so both declare the three unsupported.

`at_zone` and `to_utc` cross named time zones. SQLite has no time-zone database and declares both
unsupported; the other seven support them.

## now

`now()` → TIMESTAMP

Statement timestamp — fixed for the whole statement (PostgreSQL `statement_timestamp()`, otherwise `CURRENT_TIMESTAMP`).

Sample query:

```kql
// now: the current timestamp.
FIND orders o
FETCH now() current_ts
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · mariadb · sqlite · trino**

```sql
-- now: the current timestamp.
SELECT
  CURRENT_TIMESTAMP AS current_ts
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| postgresql | `statement_timestamp() AS current_ts` |


## clock_now

`clock_now()` → TIMESTAMP

Wall-clock timestamp read at the moment of the call. Unlike `now`, it may advance within a single statement on dialects that distinguish the two (PostgreSQL `clock_timestamp()`, MySQL/MariaDB `SYSDATE()`); elsewhere it equals `now`.

Sample query:

```kql
// clock_now: the current wall-clock timestamp.
FIND orders o
FETCH clock_now() wall_clock_ts
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · sqlite · trino**

```sql
-- clock_now: the current wall-clock timestamp.
SELECT
  CURRENT_TIMESTAMP AS wall_clock_ts
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `SYSDATE() AS wall_clock_ts` |
| postgresql | `clock_timestamp() AS wall_clock_ts` |


## today

`today()` → DATE

Current date.

Sample query:

```kql
// today: the current date.
FIND orders o
FETCH today() todays_date
```

### Generated SQL

**oracle · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- today: the current date.
SELECT
  CURRENT_DATE AS todays_date
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb | `today() AS todays_date` |
| mssql | `CAST(GETDATE() AS DATE) AS todays_date` |


## year

`year(value: date/time)` → INTEGER

Extracts the year part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// year: the year component of the order date.
FIND orders o
FETCH year(o.order_date) order_year
```

### Generated SQL

**duckdb · snowflake · mssql · mariadb · trino**

```sql
-- year: the year component of the order date.
SELECT
  year(o.order_date) AS order_year
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · postgresql | `EXTRACT(YEAR FROM o.order_date) AS order_year` |
| sqlite | `CAST(strftime('%Y', o.order_date) AS INTEGER) AS order_year` |


## month

`month(value: date/time)` → INTEGER

Extracts the month part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// month: the month component of the order date.
FIND orders o
FETCH month(o.order_date) order_month
```

### Generated SQL

**duckdb · snowflake · mssql · mariadb · trino**

```sql
-- month: the month component of the order date.
SELECT
  month(o.order_date) AS order_month
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · postgresql | `EXTRACT(MONTH FROM o.order_date) AS order_month` |
| sqlite | `CAST(strftime('%m', o.order_date) AS INTEGER) AS order_month` |


## day

`day(value: date/time)` → INTEGER

Extracts the day part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// day: the day-of-month of the order date.
FIND orders o
FETCH day(o.order_date) order_day
```

### Generated SQL

**duckdb · snowflake · mssql · mariadb · trino**

```sql
-- day: the day-of-month of the order date.
SELECT
  day(o.order_date) AS order_day
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · postgresql | `EXTRACT(DAY FROM o.order_date) AS order_day` |
| sqlite | `CAST(strftime('%d', o.order_date) AS INTEGER) AS order_day` |


## hour

`hour(value: date/time)` → INTEGER

Extracts the hour part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// hour: the hour component of a TIMESTAMP.
FIND check_temporal c
FETCH hour(c.timestamp_timestamp) hour_of_day
```

### Generated SQL

**duckdb · snowflake · mariadb · trino**

```sql
-- hour: the hour component of a TIMESTAMP.
SELECT
  hour(c.timestamp_timestamp) AS hour_of_day
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · postgresql | `EXTRACT(HOUR FROM c.timestamp_timestamp) AS hour_of_day` |
| mssql | `DATEPART(HOUR, c.timestamp_timestamp) AS hour_of_day` |
| sqlite | `CAST(strftime('%H', c.timestamp_timestamp) AS INTEGER) AS hour_of_day` |


## minute

`minute(value: date/time)` → INTEGER

Extracts the minute part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// minute: the minute component of a TIMESTAMP.
FIND check_temporal c
FETCH minute(c.timestamp_timestamp) minute_of_hour
```

### Generated SQL

**duckdb · snowflake · mariadb · trino**

```sql
-- minute: the minute component of a TIMESTAMP.
SELECT
  minute(c.timestamp_timestamp) AS minute_of_hour
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · postgresql | `EXTRACT(MINUTE FROM c.timestamp_timestamp) AS minute_of_hour` |
| mssql | `DATEPART(MINUTE, c.timestamp_timestamp) AS minute_of_hour` |
| sqlite | `CAST(strftime('%M', c.timestamp_timestamp) AS INTEGER) AS minute_of_hour` |


## second

`second(value: date/time)` → INTEGER

Extracts the second part of a date or timestamp.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// second: the second component of a TIMESTAMP.
FIND check_temporal c
FETCH second(c.timestamp_timestamp) second_of_minute
```

### Generated SQL

**duckdb · snowflake · mariadb · trino**

```sql
-- second: the second component of a TIMESTAMP.
SELECT
  second(c.timestamp_timestamp) AS second_of_minute
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEPART(SECOND, c.timestamp_timestamp) AS second_of_minute` |
| oracle | `TRUNC(EXTRACT(SECOND FROM c.timestamp_timestamp)) AS second_of_minute` |
| postgresql | `CAST(trunc(EXTRACT(SECOND FROM c.timestamp_timestamp)) AS INTEGER) AS second_of_minute` |
| sqlite | `CAST(strftime('%S', c.timestamp_timestamp) AS INTEGER) AS second_of_minute` |


## quarter

`quarter(value: date/time)` → INTEGER

Quarter of the year, 1-4.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// quarter: which quarter of the year the order falls in.
FIND orders o
FETCH quarter(o.order_date) order_quarter
```

### Generated SQL

**duckdb · snowflake · mariadb · trino**

```sql
-- quarter: which quarter of the year the order falls in.
SELECT
  quarter(o.order_date) AS order_quarter
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEPART(quarter, o.order_date) AS order_quarter` |
| oracle | `TO_NUMBER(TO_CHAR(o.order_date, 'Q')) AS order_quarter` |
| postgresql | `EXTRACT(QUARTER FROM o.order_date) AS order_quarter` |
| sqlite | `((CAST(strftime('%m', o.order_date) AS INTEGER) + 2) / 3) AS order_quarter` |


## week

`week(value: date/time)` → INTEGER

ISO-8601 week of the year, 1-53. Week 1 is the one containing the first Thursday, so early January can fall in week 52 or 53 of the previous year.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// week: the ISO-8601 week number of the order date.
FIND orders o
FETCH week(o.order_date) order_week
```

### Generated SQL

**duckdb · trino**

```sql
-- week: the ISO-8601 week number of the order date.
SELECT
  week(o.order_date) AS order_week
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `WEEKOFYEAR(o.order_date) AS order_week` |
| mssql | `DATEPART(iso_week, o.order_date) AS order_week` |
| oracle | `TO_NUMBER(TO_CHAR(o.order_date, 'IW')) AS order_week` |
| postgresql | `EXTRACT(WEEK FROM o.order_date) AS order_week` |
| snowflake | `WEEKISO(o.order_date) AS order_week` |
| sqlite | `((CAST(strftime('%j', date(o.order_date, '-3 days', 'weekday 4')) AS INTEGER) - 1) / 7 + 1) AS order_week` |


## dayofweek

`dayofweek(value: date/time)` → INTEGER

Day of the week the ISO way: **Monday is 1** and Sunday is 7. Engines disagree wildly here — some count Sunday as 0, some as 1 — so this is normalised, and the same query gives the same number on every database.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// dayofweek: which weekday the order was placed on, Monday = 1.
FIND orders o
FETCH dayofweek(o.order_date) order_weekday
```

### Generated SQL

**duckdb**

```sql
-- dayofweek: which weekday the order was placed on, Monday = 1.
SELECT
  isodow(o.order_date) AS order_weekday
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(WEEKDAY(o.order_date) + 1) AS order_weekday` |
| mssql | `((DATEDIFF(day, '19000101', o.order_date) % 7) + 1) AS order_weekday` |
| oracle | `(TRUNC(o.order_date) - TRUNC(o.order_date, 'IW') + 1) AS order_weekday` |
| postgresql | `EXTRACT(ISODOW FROM o.order_date) AS order_weekday` |
| snowflake | `DAYOFWEEKISO(o.order_date) AS order_weekday` |
| sqlite | `(((CAST(strftime('%w', o.order_date) AS INTEGER) + 6) % 7) + 1) AS order_weekday` |
| trino | `day_of_week(o.order_date) AS order_weekday` |


## dayofyear

`dayofyear(value: date/time)` → INTEGER

Day of the year, 1-366.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to extract from |

Sample query:

```kql
// dayofyear: how far into the year the order date is.
FIND orders o
FETCH dayofyear(o.order_date) order_dayofyear
```

### Generated SQL

**duckdb · snowflake · mariadb**

```sql
-- dayofyear: how far into the year the order date is.
SELECT
  dayofyear(o.order_date) AS order_dayofyear
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEPART(dayofyear, o.order_date) AS order_dayofyear` |
| oracle | `TO_NUMBER(TO_CHAR(o.order_date, 'DDD')) AS order_dayofyear` |
| postgresql | `EXTRACT(DOY FROM o.order_date) AS order_dayofyear` |
| sqlite | `CAST(strftime('%j', o.order_date) AS INTEGER) AS order_dayofyear` |
| trino | `day_of_year(o.order_date) AS order_dayofyear` |


## year_month

`year_month(value: date/time)` → INTEGER

Year and month packed into one number, `YYYYMM`: year_month('2024-01-15') = 202401.

A compact bucket key for monthly grouping: it sorts chronologically on its own, so `GROUP year_month(o.order_date)` needs neither a second column nor a tie-break. Use `month_begin` where the bucket has to stay a date — for joining to a calendar table, or for further date arithmetic.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to take the year and month of |

Sample query:

```kql
// year_month: the order's month as a sortable YYYYMM key.
FIND orders o
FETCH year_month(o.order_date) order_year_month
```

### Generated SQL

**duckdb · snowflake · mssql · mariadb · trino**

```sql
-- year_month: the order's month as a sortable YYYYMM key.
SELECT
  (year(o.order_date) * 100 + month(o.order_date)) AS order_year_month
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle | `TO_NUMBER(TO_CHAR(o.order_date, 'YYYYMM')) AS order_year_month` |
| postgresql | `CAST(EXTRACT(YEAR FROM o.order_date) * 100 + EXTRACT(MONTH FROM o.order_date) AS INTEGER) AS order_year_month` |
| sqlite | `CAST(strftime('%Y%m', o.order_date) AS INTEGER) AS order_year_month` |


## parse_date

`parse_date(value: TEXT, format: TEXT)` → DATE

Parses *value* into a date using the *format* mask.

The *format* mask is written in KQL's own vocabulary and translated per dialect, like `to_char`'s — see the *Formatting Functions* page for the tokens.

| Argument | Type | Description |
|---|---|---|
| value | TEXT | the text to parse |
| format | TEXT | the format mask |

### Generated SQL

Unsupported: **mssql**, **sqlite**


## parse_time

`parse_time(value: TEXT, format: TEXT)` → TIME

Parses *value* into a time using the *format* mask.

The *format* mask is written in KQL's own vocabulary and translated per dialect, like `to_char`'s — see the *Formatting Functions* page for the tokens.

| Argument | Type | Description |
|---|---|---|
| value | TEXT | the text to parse |
| format | TEXT | the format mask |

### Generated SQL

Unsupported: **mssql**, **sqlite**


## parse_timestamp

`parse_timestamp(value: TEXT, format: TEXT)` → TIMESTAMP

Parses *value* into a timestamp using the *format* mask.

The *format* mask is written in KQL's own vocabulary and translated per dialect, like `to_char`'s — see the *Formatting Functions* page for the tokens.

| Argument | Type | Description |
|---|---|---|
| value | TEXT | the text to parse |
| format | TEXT | the format mask |

### Generated SQL

Unsupported: **mssql**, **sqlite**


## date_trunc

`date_trunc(part: TEXT, value: date/time)` → argument-dependent

Truncates *value* to the given precision, e.g. date_trunc('month', o.order_date).

| Argument | Type | Description |
|---|---|---|
| part | TEXT | the precision to truncate to, e.g. 'month' |
| value | date/time | the date or timestamp to truncate |

Sample query:

```kql
// date_trunc: truncate the order date to the month.
FIND orders o
FETCH date_trunc('month', o.order_date) month_start
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- date_trunc: truncate the order date to the month.
SELECT
  date_trunc('month', o.order_date) AS month_start
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(MAKEDATE(YEAR(o.order_date), 1) + INTERVAL (MONTH(o.order_date) - 1) MONTH) AS month_start` |
| mssql | `DATEADD(MONTH, DATEDIFF(MONTH, 0, o.order_date), 0) AS month_start` |
| oracle | `TRUNC(o.order_date, 'MM') AS month_start` |
| sqlite | `date(o.order_date, 'start of month') AS month_start` |


## make_date

`make_date(year: INTEGER, month: INTEGER, day: INTEGER)` → DATE

Builds a date from year, month and day.

| Argument | Type | Description |
|---|---|---|
| year | INTEGER | the calendar year |
| month | INTEGER | the month of year, 1-12 |
| day | INTEGER | the day of month, 1-31 |

Sample query:

```kql
// make_date: build a DATE from year, month and day.
FIND orders o
FETCH make_date(2024, 1, 31) built
```

### Generated SQL

**duckdb · postgresql**

```sql
-- make_date: build a DATE from year, month and day.
SELECT
  make_date(2024, 1, 31) AS built
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `STR_TO_DATE(CONCAT(2024, '-', 1, '-', 31), '%Y-%m-%d') AS built` |
| mssql | `DATEFROMPARTS(2024, 1, 31) AS built` |
| oracle | `TO_DATE(2024 \|\| '-' \|\| 1 \|\| '-' \|\| 31, 'YYYY-MM-DD') AS built` |
| snowflake | `DATE_FROM_PARTS(2024, 1, 31) AS built` |
| sqlite | `date(printf('%04d-%02d-%02d', 2024, 1, 31)) AS built` |
| trino | `date(format('%04d-%02d-%02d', 2024, 1, 31)) AS built` |


## make_time

`make_time(hour: INTEGER, minute: INTEGER, second: INTEGER)` → TIME

Builds a time from hour, minute and second.

| Argument | Type | Description |
|---|---|---|
| hour | INTEGER | the hour of day, 0-23 |
| minute | INTEGER | the minute of hour, 0-59 |
| second | INTEGER | the second of minute, 0-59 |

Sample query:

```kql
// make_time: build a TIME from hour, minute and second.
FIND orders o
FETCH make_time(14, 30, 0) built
```

### Generated SQL

**duckdb · postgresql**

```sql
-- make_time: build a TIME from hour, minute and second.
SELECT
  make_time(14, 30, 0) AS built
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `MAKETIME(14, 30, 0) AS built` |
| mssql | `TIMEFROMPARTS(14, 30, 0, 0, 0) AS built` |
| oracle | `LPAD(14, 2, '0') \|\| ':' \|\| LPAD(30, 2, '0') \|\| ':' \|\| LPAD(0, 2, '0') AS built` |
| snowflake | `TIME_FROM_PARTS(14, 30, 0) AS built` |

Unsupported: **sqlite**, **trino**


## make_timestamp

`make_timestamp(year: INTEGER, month: INTEGER, day: INTEGER, hour: INTEGER, minute: INTEGER, second: INTEGER)` → TIMESTAMP

Builds a timestamp from its six components.

| Argument | Type | Description |
|---|---|---|
| year | INTEGER | the calendar year |
| month | INTEGER | the month of year, 1-12 |
| day | INTEGER | the day of month, 1-31 |
| hour | INTEGER | the hour of day, 0-23 |
| minute | INTEGER | the minute of hour, 0-59 |
| second | INTEGER | the second of minute, 0-59 |

Sample query:

```kql
// make_timestamp: build a TIMESTAMP from its parts.
FIND orders o
FETCH make_timestamp(2024, 1, 31, 14, 30, 0) built
```

### Generated SQL

**duckdb · postgresql**

```sql
-- make_timestamp: build a TIMESTAMP from its parts.
SELECT
  make_timestamp(2024, 1, 31, 14, 30, 0) AS built
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `STR_TO_DATE(CONCAT(2024, '-', 1, '-', 31, ' ', 14, ':', 30, ':', 0), '%Y-%m-%d %H:%i:%s') AS built` |
| mssql | `DATETIME2FROMPARTS(2024, 1, 31, 14, 30, 0, 0, 0) AS built` |
| oracle | `TO_TIMESTAMP(2024 \|\| '-' \|\| 1 \|\| '-' \|\| 31 \|\| ' ' \|\| 14 \|\| ':' \|\| 30 \|\| ':' \|\| 0, 'YYYY-MM-DD HH24:MI:SS') AS built` |
| snowflake | `TIMESTAMP_NTZ_FROM_PARTS(2024, 1, 31, 14, 30, 0) AS built` |

Unsupported: **sqlite**, **trino**


## days_between

`days_between(start: date/time, end: date/time)` → INTEGER

Signed number of **complete days** from *start* to *end* — whole 24-hour periods that fit inside the interval, not midnights crossed.

For two dates the two readings coincide, which is why the difference only shows with a time of day: `days_between('2023-01-01 23:00', '2023-01-02 01:00')` is **0**, because two hours is not a day, even though a midnight lies between them.

Measured before this was settled, the engines split five to three: DuckDB, PostgreSQL, SQL Server, Snowflake and SQLite counted the boundary, MariaDB, Oracle and Trino the elapsed day. All eight now count the elapsed day, matching `ChronoUnit.DAYS`.

| Argument | Type | Description |
|---|---|---|
| start | date/time | the start of the span |
| end | date/time | the end of the span |

Sample query:

```kql
// days_between: signed day distance between two dates.
FIND check_temporal c
FETCH days_between("2023-03-01", "2023-02-01") signed_minus28
```

### Generated SQL

**duckdb**

```sql
-- days_between: signed day distance between two dates.
SELECT
  CAST(trunc(date_diff('second', DATE '2023-03-01', DATE '2023-02-01') / 86400.0) AS INTEGER) AS signed_minus28
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `TIMESTAMPDIFF(DAY, DATE '2023-03-01', DATE '2023-02-01') AS signed_minus28` |
| mssql | `(DATEDIFF_BIG(second, CAST('2023-03-01' AS DATE), CAST('2023-02-01' AS DATE)) / 86400) AS signed_minus28` |
| oracle | `TRUNC(CAST(DATE '2023-02-01' AS DATE) - CAST(DATE '2023-03-01' AS DATE)) AS signed_minus28` |
| postgresql | `CAST(trunc(EXTRACT(EPOCH FROM (CAST(DATE '2023-02-01' AS timestamp) - CAST(DATE '2023-03-01' AS timestamp))) / 86400.0) AS INTEGER) AS signed_minus28` |
| snowflake | `TRUNC(DATEDIFF(second, DATE '2023-03-01', DATE '2023-02-01') / 86400) AS signed_minus28` |
| sqlite | `CAST(julianday('2023-02-01') - julianday('2023-03-01') AS INTEGER) AS signed_minus28` |
| trino | `(date_diff('second', CAST(DATE '2023-03-01' AS TIMESTAMP), CAST(DATE '2023-02-01' AS TIMESTAMP)) / 86400) AS signed_minus28` |


## months_between

`months_between(start: date/time, end: date/time)` → INTEGER

Signed number of whole months completed from *start* to *end*; months_between('2023-01-31', '2023-03-30') = 1 — the second month is not complete.

| Argument | Type | Description |
|---|---|---|
| start | date/time | the start of the span |
| end | date/time | the end of the span |

Sample query:

```kql
// months_between: whole completed months between two dates.
FIND check_temporal c
FETCH months_between("2023-01-31", "2023-03-31") whole_two
```

### Generated SQL

**duckdb · postgresql**

```sql
-- months_between: whole completed months between two dates.
SELECT
  (CAST(EXTRACT(YEAR FROM age(DATE '2023-03-31', DATE '2023-01-31')) * 12 + EXTRACT(MONTH FROM age(DATE '2023-03-31', DATE '2023-01-31')) AS INTEGER)) AS whole_two
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `TIMESTAMPDIFF(MONTH, DATE '2023-01-31', DATE '2023-03-31') AS whole_two` |
| mssql | `(DATEDIFF(month, CAST('2023-01-31' AS DATE), CAST('2023-03-31' AS DATE)) - CASE WHEN DAY(CAST('2023-03-31' AS DATE)) < DAY(CAST('2023-01-31' AS DATE)) THEN 1 ELSE 0 END) AS whole_two` |
| oracle | `TRUNC(MONTHS_BETWEEN(DATE '2023-03-31', DATE '2023-01-31')) AS whole_two` |
| snowflake | `(DATEDIFF(month, DATE '2023-01-31', DATE '2023-03-31') - CASE WHEN DAY(DATE '2023-03-31') < DAY(DATE '2023-01-31') THEN 1 ELSE 0 END) AS whole_two` |
| sqlite | `(CASE WHEN ((CAST(strftime('%Y', '2023-03-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-03-31') AS INTEGER)) - (CAST(strftime('%Y', '2023-01-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-01-31') AS INTEGER))) > 0 AND CAST(strftime('%d', '2023-03-31') AS INTEGER) < CAST(strftime('%d', '2023-01-31') AS INTEGER) THEN ((CAST(strftime('%Y', '2023-03-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-03-31') AS INTEGER)) - (CAST(strftime('%Y', '2023-01-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-01-31') AS INTEGER))) - 1 WHEN ((CAST(strftime('%Y', '2023-03-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-03-31') AS INTEGER)) - (CAST(strftime('%Y', '2023-01-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-01-31') AS INTEGER))) < 0 AND CAST(strftime('%d', '2023-03-31') AS INTEGER) > CAST(strftime('%d', '2023-01-31') AS INTEGER) THEN ((CAST(strftime('%Y', '2023-03-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-03-31') AS INTEGER)) - (CAST(strftime('%Y', '2023-01-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-01-31') AS INTEGER))) + 1 ELSE ((CAST(strftime('%Y', '2023-03-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-03-31') AS INTEGER)) - (CAST(strftime('%Y', '2023-01-31') AS INTEGER) * 12 + CAST(strftime('%m', '2023-01-31') AS INTEGER))) END) AS whole_two` |
| trino | `date_diff('month', DATE '2023-01-31', DATE '2023-03-31') AS whole_two` |


## years_between

`years_between(start: date/time, end: date/time)` → INTEGER

Signed number of whole years completed from *start* to *end*.

| Argument | Type | Description |
|---|---|---|
| start | date/time | the start of the span |
| end | date/time | the end of the span |

Sample query:

```kql
// years_between: whole years from order to shipment.
FIND orders o
FETCH years_between(o.order_date, o.shipped_date) years
```

### Generated SQL

**duckdb · postgresql**

```sql
-- years_between: whole years from order to shipment.
SELECT
  CAST(EXTRACT(YEAR FROM age(o.shipped_date, o.order_date)) AS INTEGER) AS years
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `TIMESTAMPDIFF(YEAR, o.order_date, o.shipped_date) AS years` |
| mssql | `(DATEDIFF(YEAR, o.order_date, o.shipped_date) - CASE WHEN DATEADD(YEAR, DATEDIFF(YEAR, o.order_date, o.shipped_date), o.order_date) > o.shipped_date THEN 1 ELSE 0 END) AS years` |
| oracle | `TRUNC(MONTHS_BETWEEN(o.shipped_date, o.order_date) / 12) AS years` |
| snowflake | `FLOOR(MONTHS_BETWEEN(o.shipped_date, o.order_date) / 12) AS years` |
| sqlite | `(CASE WHEN (CAST(strftime('%Y', o.shipped_date) AS INTEGER) - CAST(strftime('%Y', o.order_date) AS INTEGER)) > 0 AND strftime('%m-%d', o.shipped_date) < strftime('%m-%d', o.order_date) THEN (CAST(strftime('%Y', o.shipped_date) AS INTEGER) - CAST(strftime('%Y', o.order_date) AS INTEGER)) - 1 WHEN (CAST(strftime('%Y', o.shipped_date) AS INTEGER) - CAST(strftime('%Y', o.order_date) AS INTEGER)) < 0 AND strftime('%m-%d', o.shipped_date) > strftime('%m-%d', o.order_date) THEN (CAST(strftime('%Y', o.shipped_date) AS INTEGER) - CAST(strftime('%Y', o.order_date) AS INTEGER)) + 1 ELSE (CAST(strftime('%Y', o.shipped_date) AS INTEGER) - CAST(strftime('%Y', o.order_date) AS INTEGER)) END) AS years` |
| trino | `date_diff('year', o.order_date, o.shipped_date) AS years` |


## calendar_distance

`calendar_distance(start: date/time, end: date/time)` → DURATION

Calendar (civil) distance from *start* to *end* as a mixed interval (years/months/days + clock): calendar_distance('2025-01-01', '2026-05-15 12:00') = 1y4mo14d12h. Use ts − ts for a fixed elapsed span.

| Argument | Type | Description |
|---|---|---|
| start | date/time | the start of the span |
| end | date/time | the end of the span |

Sample query:

```kql
// calendar_distance: calendar span from order to shipment (NULL when not yet shipped).
FIND orders o
FETCH calendar_distance(o.order_date, o.shipped_date) span
```

### Generated SQL

**duckdb · postgresql**

```sql
-- calendar_distance: calendar span from order to shipment (NULL when not yet shipped).
SELECT
  (CAST(CAST(EXTRACT(EPOCH FROM (o.order_date)) AS BIGINT) AS VARCHAR) || ';' || CAST(CAST(EXTRACT(EPOCH FROM (o.shipped_date)) AS BIGINT) AS VARCHAR)) AS span
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CONCAT(UNIX_TIMESTAMP(o.order_date), ';', UNIX_TIMESTAMP(o.shipped_date)) AS span` |
| mssql | `CONCAT(DATEDIFF_BIG(SECOND, '1970-01-01', o.order_date), ';', DATEDIFF_BIG(SECOND, '1970-01-01', o.shipped_date)) AS span` |
| oracle | `(TO_CHAR(ROUND((CAST(o.order_date AS DATE) - DATE '1970-01-01') * 86400)) \|\| ';' \|\| TO_CHAR(ROUND((CAST(o.shipped_date AS DATE) - DATE '1970-01-01') * 86400))) AS span` |
| snowflake | `(CAST(DATE_PART(EPOCH_SECOND, o.order_date) AS VARCHAR) \|\| ';' \|\| CAST(DATE_PART(EPOCH_SECOND, o.shipped_date) AS VARCHAR)) AS span` |
| sqlite | `(CAST(CAST(strftime('%s', o.order_date) AS INTEGER) AS VARCHAR) \|\| ';' \|\| CAST(CAST(strftime('%s', o.shipped_date) AS INTEGER) AS VARCHAR)) AS span` |
| trino | `(CAST(CAST(to_unixtime(o.order_date) AS BIGINT) AS VARCHAR) \|\| ';' \|\| CAST(CAST(to_unixtime(o.shipped_date) AS BIGINT) AS VARCHAR)) AS span` |


## day_add

`day_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* days; *n* may be any expression.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to shift |
| n | INTEGER | the number of days to add |

Sample query:

```kql
// day_add: seven days after the order date.
FIND orders o
FETCH day_add(o.order_date, 7) plus_week
```

### Generated SQL

**duckdb · mariadb**

```sql
-- day_add: seven days after the order date.
SELECT
  (o.order_date + INTERVAL (7) DAY) AS plus_week
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEADD(DAY, 7, o.order_date) AS plus_week` |
| oracle | `(o.order_date + NUMTODSINTERVAL(7, 'DAY')) AS plus_week` |
| postgresql | `(o.order_date + 7 * INTERVAL '1 day') AS plus_week` |
| snowflake | `DATEADD('day', 7, o.order_date) AS plus_week` |
| sqlite | `date(o.order_date, printf('%+d days', 7)) AS plus_week` |
| trino | `date_add('day', 7, o.order_date) AS plus_week` |


## month_add

`month_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* months, clamped to the end of the month: month_add('2023-01-31', 1) = 2023-02-28.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to shift |
| n | INTEGER | the number of months to add |

Sample query:

```kql
// month_add: one month after the order date.
FIND orders o
FETCH month_add(o.order_date, 1) next_month
```

### Generated SQL

**duckdb · mariadb**

```sql
-- month_add: one month after the order date.
SELECT
  (o.order_date + INTERVAL (1) MONTH) AS next_month
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEADD(MONTH, 1, o.order_date) AS next_month` |
| oracle | `(ADD_MONTHS(o.order_date, 1) - GREATEST(EXTRACT(DAY FROM ADD_MONTHS(o.order_date, 1)) - EXTRACT(DAY FROM o.order_date), 0)) AS next_month` |
| postgresql | `(o.order_date + 1 * INTERVAL '1 month') AS next_month` |
| snowflake | `DATEADD('month', 1, o.order_date) AS next_month` |
| sqlite | `min(date(o.order_date, printf('%+d months', 1)), date(o.order_date, 'start of month', printf('%+d months', 1 + 1), '-1 day')) AS next_month` |
| trino | `date_add('month', 1, o.order_date) AS next_month` |


## year_add

`year_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* years, clamped (Feb 29 + 1 year = Feb 28).

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to shift |
| n | INTEGER | the number of years to add |

Sample query:

```kql
// year_add: one year after the order date.
FIND orders o
FETCH year_add(o.order_date, 1) next_year
```

### Generated SQL

**duckdb · mariadb**

```sql
-- year_add: one year after the order date.
SELECT
  (o.order_date + INTERVAL (1) YEAR) AS next_year
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `DATEADD(YEAR, 1, o.order_date) AS next_year` |
| oracle | `(ADD_MONTHS(o.order_date, 1 * 12) - GREATEST(EXTRACT(DAY FROM ADD_MONTHS(o.order_date, 1 * 12)) - EXTRACT(DAY FROM o.order_date), 0)) AS next_year` |
| postgresql | `(o.order_date + 1 * INTERVAL '1 year') AS next_year` |
| snowflake | `DATEADD('year', 1, o.order_date) AS next_year` |
| sqlite | `min(date(o.order_date, printf('%+d years', 1)), date(o.order_date, 'start of month', printf('%+d months', 1 * 12 + 1), '-1 day')) AS next_year` |
| trino | `date_add('year', 1, o.order_date) AS next_year` |


## minute_begin

`minute_begin(value: date/time)` → argument-dependent

Start of the minute (seconds become 00).

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the minute |

Sample query:

```kql
// minute_begin: the start of a TIMESTAMP's minute.
FIND check_temporal c
FETCH minute_begin(c.timestamp_timestamp) start_of_minute
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- minute_begin: the start of a TIMESTAMP's minute.
SELECT
  date_trunc('minute', c.timestamp_timestamp) AS start_of_minute
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(DATE_FORMAT(c.timestamp_timestamp, '%Y-%m-%d %H:%i:00') AS DATETIME) AS start_of_minute` |
| mssql | `DATEADD(MINUTE, DATEDIFF(MINUTE, 0, c.timestamp_timestamp), 0) AS start_of_minute` |
| oracle | `TRUNC(CAST(c.timestamp_timestamp AS DATE), 'MI') AS start_of_minute` |
| sqlite | `datetime(strftime('%Y-%m-%d %H:%M:00', c.timestamp_timestamp)) AS start_of_minute` |


## hour_begin

`hour_begin(value: date/time)` → argument-dependent

Start of the hour (minutes and seconds become 00).

The bucket for sub-daily data, and the one the family was missing: with nothing between `minute` (the part, 0-59, which folds every hour of every day together) and `day_begin`, an hourly trend had no portable answer at all. `date_trunc('hour', x)` was not one -- four of the eight engines render that call by hand and knew only day, month, quarter and year.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the hour |

Sample query:

```kql
// hour_begin: the start of a TIMESTAMP's hour -- the bucket an hourly trend groups by.
FIND check_temporal c
FETCH hour_begin(c.timestamp_timestamp) start_of_hour
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- hour_begin: the start of a TIMESTAMP's hour -- the bucket an hourly trend groups by.
SELECT
  date_trunc('hour', c.timestamp_timestamp) AS start_of_hour
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(DATE_FORMAT(c.timestamp_timestamp, '%Y-%m-%d %H:00:00') AS DATETIME) AS start_of_hour` |
| mssql | `DATEADD(HOUR, DATEDIFF(HOUR, 0, c.timestamp_timestamp), 0) AS start_of_hour` |
| oracle | `TRUNC(CAST(c.timestamp_timestamp AS DATE), 'HH') AS start_of_hour` |
| sqlite | `datetime(strftime('%Y-%m-%d %H:00:00', c.timestamp_timestamp)) AS start_of_hour` |


## day_begin

`day_begin(value: date/time)` → argument-dependent

Start of the day (time becomes 00:00:00).

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the day |

Sample query:

```kql
// day_begin: midnight at the start of a TIMESTAMP's day.
FIND check_temporal c
FETCH day_begin(c.timestamp_timestamp) start_of_day
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- day_begin: midnight at the start of a TIMESTAMP's day.
SELECT
  date_trunc('day', c.timestamp_timestamp) AS start_of_day
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CAST(DATE(c.timestamp_timestamp) AS DATETIME) AS start_of_day` |
| mssql | `CAST(CAST(c.timestamp_timestamp AS DATE) AS DATETIME2) AS start_of_day` |
| oracle | `TRUNC(CAST(c.timestamp_timestamp AS DATE)) AS start_of_day` |
| sqlite | `datetime(c.timestamp_timestamp, 'start of day') AS start_of_day` |


## week_begin

`week_begin(value: date/time)` → argument-dependent

Monday of the week (pairs with week_end). Grouping by this is the reliable way to bucket by week: it is a real date, so it sorts, and it has none of the year-boundary trouble of grouping by `year` and `week` together — 2026-12-28 is in week 1, but of 2027.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the week |

Sample query:

```kql
// week_begin: Monday of the order's week — the sortable weekly grouping key.
FIND orders o
FETCH week_begin(o.order_date) week_start
```

### Generated SQL

**duckdb · postgresql · trino**

```sql
-- week_begin: Monday of the order's week — the sortable weekly grouping key.
SELECT
  date_trunc('week', o.order_date) AS week_start
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(DATE(o.order_date) - INTERVAL WEEKDAY(o.order_date) DAY) AS week_start` |
| mssql | `DATEADD(day, -(DATEDIFF(day, 0, o.order_date) % 7), CAST(o.order_date AS DATE)) AS week_start` |
| oracle | `TRUNC(o.order_date, 'IW') AS week_start` |
| snowflake | `DATEADD(day, -(DAYOFWEEKISO(o.order_date) - 1), o.order_date) AS week_start` |
| sqlite | `date(o.order_date, '-6 days', 'weekday 1') AS week_start` |


## month_begin

`month_begin(value: date/time)` → argument-dependent

First day of the month (pairs with month_end).

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the month |

Sample query:

```kql
// month_begin: first day of the order's month.
FIND orders o
FETCH month_begin(o.order_date) m_begin
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- month_begin: first day of the order's month.
SELECT
  date_trunc('month', o.order_date) AS m_begin
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(MAKEDATE(YEAR(o.order_date), 1) + INTERVAL (MONTH(o.order_date) - 1) MONTH) AS m_begin` |
| mssql | `DATEADD(MONTH, DATEDIFF(MONTH, 0, o.order_date), 0) AS m_begin` |
| oracle | `TRUNC(o.order_date, 'MM') AS m_begin` |
| sqlite | `date(o.order_date, 'start of month') AS m_begin` |


## quarter_begin

`quarter_begin(value: date/time)` → argument-dependent

First day of the quarter.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the quarter |

Sample query:

```kql
// quarter_begin: first day of the order's quarter.
FIND orders o
FETCH quarter_begin(o.order_date) q_begin
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- quarter_begin: first day of the order's quarter.
SELECT
  date_trunc('quarter', o.order_date) AS q_begin
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(MAKEDATE(YEAR(o.order_date), 1) + INTERVAL (QUARTER(o.order_date) - 1) * 3 MONTH) AS q_begin` |
| mssql | `DATEADD(QUARTER, DATEDIFF(QUARTER, 0, o.order_date), 0) AS q_begin` |
| oracle | `TRUNC(o.order_date, 'Q') AS q_begin` |
| sqlite | `date(o.order_date, 'start of month', printf('%+d months', -((CAST(strftime('%m', o.order_date) AS INTEGER) - 1) % 3))) AS q_begin` |


## year_begin

`year_begin(value: date/time)` → argument-dependent

First day of the year.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to floor to the year |

Sample query:

```kql
// year_begin: first day of the order's year.
FIND orders o
FETCH year_begin(o.order_date) y_begin
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- year_begin: first day of the order's year.
SELECT
  date_trunc('year', o.order_date) AS y_begin
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `MAKEDATE(YEAR(o.order_date), 1) AS y_begin` |
| mssql | `DATEADD(YEAR, DATEDIFF(YEAR, 0, o.order_date), 0) AS y_begin` |
| oracle | `TRUNC(o.order_date, 'YYYY') AS y_begin` |
| sqlite | `date(o.order_date, 'start of year') AS y_begin` |


## week_end

`week_end(value: date/time)` → DATE

Sunday of the week.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to take the week end of |

Sample query:

```kql
// week_end: Sunday of the order's week.
FIND orders o
FETCH week_end(o.order_date) week_close
```

### Generated SQL

**duckdb**

```sql
-- week_end: Sunday of the order's week.
SELECT
  CAST(date_trunc('week', o.order_date) + INTERVAL 6 DAY AS DATE) AS week_close
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(DATE(o.order_date) - INTERVAL WEEKDAY(o.order_date) DAY + INTERVAL 6 DAY) AS week_close` |
| mssql | `DATEADD(day, 6 - (DATEDIFF(day, 0, o.order_date) % 7), CAST(o.order_date AS DATE)) AS week_close` |
| oracle | `(TRUNC(o.order_date, 'IW') + 6) AS week_close` |
| postgresql | `CAST(date_trunc('week', o.order_date) + INTERVAL '6 days' AS DATE) AS week_close` |
| snowflake | `DATEADD(day, 7 - DAYOFWEEKISO(o.order_date), o.order_date) AS week_close` |
| sqlite | `date(o.order_date, 'weekday 0') AS week_close` |
| trino | `CAST(date_trunc('week', o.order_date) + INTERVAL '6' DAY AS DATE) AS week_close` |


## month_end

`month_end(value: date/time)` → DATE

Last day of the month (Excel EOMONTH): month_end('2024-02-10') = 2024-02-29.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to take the month end of |

Sample query:

```kql
// month_end: last day of the order's month.
FIND orders o
FETCH month_end(o.order_date) m_end
```

### Generated SQL

**duckdb · snowflake**

```sql
-- month_end: last day of the order's month.
SELECT
  last_day(o.order_date) AS m_end
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `LAST_DAY(o.order_date) AS m_end` |
| mssql | `EOMONTH(o.order_date) AS m_end` |
| oracle | `TRUNC(LAST_DAY(o.order_date)) AS m_end` |
| postgresql | `CAST(date_trunc('month', o.order_date) + INTERVAL '1 month - 1 day' AS DATE) AS m_end` |
| sqlite | `date(o.order_date, 'start of month', '+1 month', '-1 day') AS m_end` |
| trino | `last_day_of_month(o.order_date) AS m_end` |


## quarter_end

`quarter_end(value: date/time)` → DATE

Last day of the quarter.

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to take the quarter end of |

Sample query:

```kql
// quarter_end: last day of the order's quarter.
FIND orders o
FETCH quarter_end(o.order_date) q_end
```

### Generated SQL

**duckdb**

```sql
-- quarter_end: last day of the order's quarter.
SELECT
  last_day(date_trunc('quarter', o.order_date) + INTERVAL 2 MONTH) AS q_end
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `LAST_DAY(MAKEDATE(YEAR(o.order_date), 1) + INTERVAL (QUARTER(o.order_date) * 3 - 1) MONTH) AS q_end` |
| mssql | `EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, o.order_date), 0), 2) AS q_end` |
| oracle | `LAST_DAY(ADD_MONTHS(TRUNC(o.order_date, 'Q'), 2)) AS q_end` |
| postgresql | `CAST(date_trunc('quarter', o.order_date) + INTERVAL '3 months - 1 day' AS DATE) AS q_end` |
| snowflake | `LAST_DAY(o.order_date, 'quarter') AS q_end` |
| sqlite | `date(o.order_date, 'start of month', printf('%+d months', 3 - ((CAST(strftime('%m', o.order_date) AS INTEGER) - 1) % 3)), '-1 day') AS q_end` |
| trino | `last_day_of_month(date_trunc('quarter', o.order_date) + INTERVAL '2' MONTH) AS q_end` |


## year_end

`year_end(value: date/time)` → DATE

Last day of the year (December 31).

| Argument | Type | Description |
|---|---|---|
| value | date/time | the date or timestamp to take the year end of |

Sample query:

```kql
// year_end: last day of the order's year.
FIND orders o
FETCH year_end(o.order_date) y_end
```

### Generated SQL

**duckdb**

```sql
-- year_end: last day of the order's year.
SELECT
  last_day(date_trunc('year', o.order_date) + INTERVAL 11 MONTH) AS y_end
FROM
 orders o
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `LAST_DAY(MAKEDATE(YEAR(o.order_date), 1) + INTERVAL 11 MONTH) AS y_end` |
| mssql | `DATEFROMPARTS(YEAR(o.order_date), 12, 31) AS y_end` |
| oracle | `LAST_DAY(ADD_MONTHS(TRUNC(o.order_date, 'YYYY'), 11)) AS y_end` |
| postgresql | `CAST(date_trunc('year', o.order_date) + INTERVAL '1 year - 1 day' AS DATE) AS y_end` |
| snowflake | `LAST_DAY(o.order_date, 'year') AS y_end` |
| sqlite | `date(o.order_date, 'start of year', '+1 year', '-1 day') AS y_end` |
| trino | `last_day_of_month(date_trunc('year', o.order_date) + INTERVAL '11' MONTH) AS y_end` |


## at_zone

`at_zone(value: date/time, zone: TEXT)` → TIMESTAMP

Reads *value* as a model-zone wall-clock value and returns its wall-clock in the named zone (e.g. for day-bucketing: date(at_zone(o.ts, 'Europe/Berlin'))).

| Argument | Type |
|---|---|
| value | date/time |
| zone | TEXT |

Sample query:

```kql
// at_zone: read a model-zone wall-clock value in another zone.
FIND check_temporal c
FETCH at_zone(c.ts_diff_base, 'America/New_York') in_new_york
```

### Generated SQL

**duckdb · postgresql**

```sql
-- at_zone: read a model-zone wall-clock value in another zone.
SELECT
  ((c.ts_diff_base AT TIME ZONE 'UTC') AT TIME ZONE 'America/New_York') AS in_new_york
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CONVERT_TZ(c.ts_diff_base, 'UTC', 'America/New_York') AS in_new_york` |
| mssql | `CAST(((CAST(c.ts_diff_base AS datetime2) AT TIME ZONE 'UTC') AT TIME ZONE 'Eastern Standard Time') AS datetime2) AS in_new_york` |
| oracle | `CAST(FROM_TZ(CAST(c.ts_diff_base AS TIMESTAMP), 'UTC') AT TIME ZONE 'America/New_York' AS TIMESTAMP) AS in_new_york` |
| snowflake | `CONVERT_TIMEZONE('UTC', 'America/New_York', c.ts_diff_base) AS in_new_york` |
| trino | `CAST(with_timezone(c.ts_diff_base, 'UTC') AT TIME ZONE 'America/New_York' AS TIMESTAMP) AS in_new_york` |

Unsupported: **sqlite**


## to_utc

`to_utc(value: date/time, zone: TEXT)` → TIMESTAMP

Inverse of at_zone: reads *value* as a wall-clock in the named zone and returns the model-zone wall-clock value.

| Argument | Type |
|---|---|
| value | date/time |
| zone | TEXT |

Sample query:

```kql
// to_utc: read a zoned wall-clock value and return it in UTC.
FIND check_temporal c
FETCH to_utc(c.ts_diff_base, 'America/New_York') as_utc
```

### Generated SQL

**duckdb · postgresql**

```sql
-- to_utc: read a zoned wall-clock value and return it in UTC.
SELECT
  ((c.ts_diff_base AT TIME ZONE 'America/New_York') AT TIME ZONE 'UTC') AS as_utc
FROM
 check_temporal c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `CONVERT_TZ(c.ts_diff_base, 'America/New_York', 'UTC') AS as_utc` |
| mssql | `CAST(((CAST(c.ts_diff_base AS datetime2) AT TIME ZONE 'Eastern Standard Time') AT TIME ZONE 'UTC') AS datetime2) AS as_utc` |
| oracle | `CAST(FROM_TZ(CAST(c.ts_diff_base AS TIMESTAMP), 'America/New_York') AT TIME ZONE 'UTC' AS TIMESTAMP) AS as_utc` |
| snowflake | `CONVERT_TIMEZONE('America/New_York', 'UTC', c.ts_diff_base) AS as_utc` |
| trino | `CAST(with_timezone(c.ts_diff_base, 'America/New_York') AT TIME ZONE 'UTC' AS TIMESTAMP) AS as_utc` |

Unsupported: **sqlite**

