---
menuTitle: "Date/Time Functions"
parent: "FUNCTIONS"
order: 5
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Date/Time Functions

## now

`now()` → TIMESTAMP

Current timestamp at statement execution.

Standard SQL: `CURRENT_TIMESTAMP`


## clock_now

`clock_now()` → TIMESTAMP

Current timestamp at the time of the call.

Standard SQL: `CURRENT_TIMESTAMP`


## today

`today()` → DATE

Current date.

Standard SQL: `CURRENT_DATE`


## date

`date(value: any)` → DATE

Casts *value* to a DATE, discarding any time component — the type-named cast; `to_date` is the portable form with explicit ISO-8601 text rules.

Standard SQL: `date(value)`

Example: `date(at_zone(o.ts, 'Europe/Berlin'))`


## time

`time(value: any)` → TIME

Casts *value* to a TIME, discarding the date part.

Standard SQL: `time(value)`

Example: `time(o.order_ts)`


## timestamp

`timestamp(value: any)` → TIMESTAMP

Casts *value* to a TIMESTAMP; a DATE is extended with midnight (00:00:00).

Standard SQL: `timestamp(value)`

Example: `timestamp(o.order_date)`


## year

`year(value: date/time)` → INTEGER

Extracts the year part of a date or timestamp.

Standard SQL: `year(value)`


## month

`month(value: date/time)` → INTEGER

Extracts the month part of a date or timestamp.

Standard SQL: `month(value)`


## day

`day(value: date/time)` → INTEGER

Extracts the day part of a date or timestamp.

Standard SQL: `day(value)`


## hour

`hour(value: date/time)` → INTEGER

Extracts the hour part of a date or timestamp.

Standard SQL: `hour(value)`


## minute

`minute(value: date/time)` → INTEGER

Extracts the minute part of a date or timestamp.

Standard SQL: `minute(value)`


## second

`second(value: date/time)` → INTEGER

Extracts the second part of a date or timestamp.

Standard SQL: `second(value)`


## date_trunc

`date_trunc(part: TEXT, value: date/time)` → argument-dependent

Truncates *value* to the given precision, e.g. date_trunc('month', o.order_date).

Standard SQL: `date_trunc(part, value)`

Example: `date_trunc('month', o.order_date)`


## make_date

`make_date(year: INTEGER, month: INTEGER, day: INTEGER)` → DATE

Builds a date from year, month and day.

Standard SQL: `make_date(year, month, day)`

Example: `make_date(2024, 1, 31)`


## make_time

`make_time(hour: INTEGER, minute: INTEGER, second: INTEGER)` → TIME

Builds a time from hour, minute and second.

Standard SQL: `make_time(hour, minute, second)`

Example: `make_time(14, 30, 0)`


## make_timestamp

`make_timestamp(year: INTEGER, month: INTEGER, day: INTEGER, hour: INTEGER, minute: INTEGER, second: INTEGER)` → TIMESTAMP

Builds a timestamp from its six components.

Standard SQL: `make_timestamp(year, month, day, hour, minute, second)`

Example: `make_timestamp(2024, 1, 31, 14, 30, 0)`


## days_between

`days_between(start: date/time, end: date/time)` → INTEGER

Signed number of whole days from *start* to *end*.

Standard SQL: `date_diff('day', start, end)`

Example: `days_between(o.order_date, o.shipped_date)`


## months_between

`months_between(start: date/time, end: date/time)` → INTEGER

Signed number of whole months completed from *start* to *end*; months_between('2023-01-31', '2023-03-30') = 1 — the second month is not complete.

Standard SQL: `(CAST(EXTRACT(YEAR FROM age(end, start)) * 12 + EXTRACT(MONTH FROM age(end, start)) AS INTEGER))`

Example: `months_between(o.order_date, o.shipped_date)`


## years_between

`years_between(start: date/time, end: date/time)` → INTEGER

Signed number of whole years completed from *start* to *end*.

Standard SQL: `CAST(EXTRACT(YEAR FROM age(end, start)) AS INTEGER)`

Example: `years_between(c.member_since, today())`


## calendar_distance

`calendar_distance(start: date/time, end: date/time)` → DURATION

Calendar (civil) distance from *start* to *end* as a mixed interval (years/months/days + clock): calendar_distance('2025-01-01', '2026-05-15 12:00') = 1y4mo14d12h. Use ts − ts for a fixed elapsed span.

Standard SQL: `calendar_distance(…) — dialect-specific rendering`

Example: `calendar_distance(o.order_date, o.shipped_date)`


## day_add

`day_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* days; *n* may be any expression.

Standard SQL: `(value + INTERVAL (n) DAY)`

Example: `day_add(o.order_date, o.processing_days)`


## month_add

`month_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* months, clamped to the end of the month: month_add('2023-01-31', 1) = 2023-02-28.

Standard SQL: `(value + INTERVAL (n) MONTH)`

Example: `month_add(o.order_date, 1)`


## year_add

`year_add(value: date/time, n: INTEGER)` → argument-dependent

*value* shifted by *n* years, clamped (Feb 29 + 1 year = Feb 28).

Standard SQL: `(value + INTERVAL (n) YEAR)`

Example: `year_add(c.contract_start, 1)`


## day_begin

`day_begin(value: date/time)` → argument-dependent

Start of the day (time becomes 00:00:00).

Standard SQL: `date_trunc('day', value)`

Example: `day_begin(o.ordered_at)`


## month_begin

`month_begin(value: date/time)` → argument-dependent

First day of the month (pairs with month_end).

Standard SQL: `date_trunc('month', value)`

Example: `month_begin(o.order_date)`


## quarter_begin

`quarter_begin(value: date/time)` → argument-dependent

First day of the quarter.

Standard SQL: `date_trunc('quarter', value)`

Example: `quarter_begin(o.order_date)`


## year_begin

`year_begin(value: date/time)` → argument-dependent

First day of the year.

Standard SQL: `date_trunc('year', value)`

Example: `year_begin(o.order_date)`


## month_end

`month_end(value: date/time)` → DATE

Last day of the month (Excel EOMONTH): month_end('2024-02-10') = 2024-02-29.

Standard SQL: `last_day(value)`

Example: `month_end(o.order_date)`


## quarter_end

`quarter_end(value: date/time)` → DATE

Last day of the quarter.

Standard SQL: `last_day(date_trunc('quarter', value) + INTERVAL 2 MONTH)`

Example: `quarter_end(o.order_date)`


## year_end

`year_end(value: date/time)` → DATE

Last day of the year (December 31).

Standard SQL: `last_day(date_trunc('year', value) + INTERVAL 11 MONTH)`

Example: `year_end(o.order_date)`

