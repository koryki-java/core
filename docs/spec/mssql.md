<!-- Generated from the function catalog and runnable KQL samples — do not edit. -->

# Function & operator specification: mssql

Each first-level chapter is a function category; each second-level section a function or operator. Every sample is a real KQL query run against the *typecheck*, *temporal* or *northwind* demo database and transpiled to mssql SQL.

# Mathematical Functions

## abs

Absolute value.

**`abs(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |

Rendering: `abs(value)`

_No runnable sample yet._

## ceil

Nearest integer greater than or equal to *value*.

**`ceil(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |

Rendering: `CEILING(value)`

_No runnable sample yet._

## ceiling

Nearest integer greater than or equal to *value* (alias of ceil).

**`ceiling(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |

Rendering: `ceiling(value)`

_No runnable sample yet._

## floor

Nearest integer less than or equal to *value*.

**`floor(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |

Rendering: `floor(value)`

_No runnable sample yet._

## mod

Remainder of *dividend* / *divisor*.

**`mod(dividend, divisor)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | dividend | NUMERIC | required |
| 2 | divisor | NUMERIC | required |

Rendering: `mod(dividend, divisor)`

_No runnable sample yet._

## pi

Approximate value of π.

**`pi()`** → FLOAT

Arguments: none.

Rendering: `pi()`

_No runnable sample yet._

## power

*base* raised to the power of *exponent*.

**`power(base, exponent)`** → FLOAT

| # | argument | family | required |
|---|---|---|---|
| 1 | base | NUMERIC | required |
| 2 | exponent | NUMERIC | required |

Rendering: `power(base, exponent)`

_No runnable sample yet._

## random

Random value in the range 0.0 <= x < 1.0.

**`random()`** → FLOAT

Arguments: none.

Rendering: `random()`

_No runnable sample yet._

## round

Rounds to *scale* decimal places (default 0).

Half-way rounding of binary-float inputs is engine-defined: a value such as `0.15` is stored as `0.149999…`, so engines may round it up or down.

See: [What Every Computer Scientist Should Know About Floating-Point Arithmetic](https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html).

**`round(value [, scale])`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |
| 2 | scale | INTEGER | optional |

Rendering: `round(value, scale)`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.customer_id = 'TOMSP'
FETCH round(o.freight, 1) freight_rounded
```

```sql
-- unknown sample database: northwind
```

## sqrt

Square root.

**`sqrt(value)`** → FLOAT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | NUMERIC | required |

Rendering: `sqrt(value)`

_No runnable sample yet._

# String Functions

## ascii

Numeric code of the first character.

**`ascii(character)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | character | TEXT | required |

Rendering: `ascii(character)`

_No runnable sample yet._

## bit_length

Number of bits in the string.

**`bit_length(string)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `bit_length(string)`

_No runnable sample yet._

## btrim

Removes *characters* (default spaces) from both ends (alias of trim).

**`btrim(string [, characters])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | characters | TEXT | optional |

Rendering: `btrim(string, characters)`

_No runnable sample yet._

## char_length

Number of characters in the string.

**`char_length(string)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `LEN(string)`

_No runnable sample yet._

## character_length

Number of characters in the string.

**`character_length(string)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `LEN(string)`

_No runnable sample yet._

## chr

Character with the given numeric code.

**`chr(code)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | code | INTEGER | required |

Rendering: `chr(code)`

_No runnable sample yet._

## concat

Concatenates the text form of all arguments; null arguments are ignored.

**`concat(value, ...)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `concat(value)`

_No runnable sample yet._

## concat_ws

Concatenates all arguments after the first, separated by *separator*.

**`concat_ws(separator, ...)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | separator | ANY | required |

Rendering: `concat_ws(separator)`

_No runnable sample yet._

## initcap

Capitalizes the first letter of each word.

**`initcap(string)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `initcap(string)`

_No runnable sample yet._

## left

First *n* characters.

**`left(string, n)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | n | INTEGER | required |

Rendering: `left(string, n)`

_No runnable sample yet._

## length

Number of characters in the string.

**`length(string)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `LEN(string)`

_No runnable sample yet._

## lower

Converts the string to lower case.

**`lower(string)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `lower(string)`

_No runnable sample yet._

## lpad

Pads the string on the left to *length* using *fill* (default space).

**`lpad(string, length [, fill])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | length | INTEGER | required |
| 3 | fill | TEXT | optional |

Rendering: `lpad(string, length, fill)`

_No runnable sample yet._

## ltrim

Removes *characters* (default spaces) from the start.

**`ltrim(string [, characters])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | characters | TEXT | optional |

Rendering: `ltrim(string, characters)`

_No runnable sample yet._

## md5

MD5 hash as a hexadecimal string.

**`md5(string)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `md5(string)`

_No runnable sample yet._

## octet_length

Number of bytes in the string.

**`octet_length(string)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `octet_length(string)`

_No runnable sample yet._

## overlay

Replaces a substring (dialect-specific syntax).

**`overlay(string, replacement, start [, length])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | replacement | TEXT | required |
| 3 | start | INTEGER | required |
| 4 | length | INTEGER | optional |

Rendering: `overlay(string, replacement, start, length)`

_No runnable sample yet._

## position

Position of the first occurrence of *substr* in *str* (1-based, 0 if absent).

**`position(substr, str)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | substr | TEXT | required |
| 2 | str | TEXT | required |

Rendering: `POSITION(substr IN str)`

_No runnable sample yet._

## repeat

Repeats the string *number* times.

**`repeat(string, number)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | number | INTEGER | required |

Rendering: `repeat(string, number)`

_No runnable sample yet._

## replace

Replaces all occurrences of *from* with *to*.

**`replace(string, from, to)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | from | TEXT | required |
| 3 | to | TEXT | required |

Rendering: `replace(string, from, to)`

_No runnable sample yet._

## reverse

Reverses the string.

**`reverse(string)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `reverse(string)`

_No runnable sample yet._

## right

Last *n* characters.

**`right(string, n)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | n | INTEGER | required |

Rendering: `right(string, n)`

_No runnable sample yet._

## rpad

Pads the string on the right to *length* using *fill* (default space).

**`rpad(string, length [, fill])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | length | INTEGER | required |
| 3 | fill | TEXT | optional |

Rendering: `rpad(string, length, fill)`

_No runnable sample yet._

## rtrim

Removes *characters* (default spaces) from the end.

**`rtrim(string [, characters])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | characters | TEXT | optional |

Rendering: `rtrim(string, characters)`

_No runnable sample yet._

## split_part

Splits at *delimiter* and returns the *n*-th field (1-based).

**`split_part(string, delimiter, n)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | delimiter | TEXT | required |
| 3 | n | INTEGER | required |

Rendering: `split_part(string, delimiter, n)`

_No runnable sample yet._

## starts_with

True if the string begins with *prefix*.

**`starts_with(string, prefix)`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | prefix | TEXT | required |

Rendering: `starts_with(string, prefix)`

_No runnable sample yet._

## strpos

Position of the first occurrence of *substring* (1-based, 0 if absent).

**`strpos(string, substring)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | substring | TEXT | required |

Rendering: `strpos(string, substring)`

_No runnable sample yet._

## substr

Extracts the substring starting at *start* (1-based), optionally limited to *length* characters.

**`substr(string, start [, length])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | start | INTEGER | required |
| 3 | length | INTEGER | optional |

Rendering: `SUBSTRING(string, start, length)`

**Sample** — `northwind` database

```kql
FIND customers c
FETCH substr(c.company_name, 1, 3) abbrev
```

```sql
-- unknown sample database: northwind
```

## substring

Extracts the substring starting at *start* (1-based), optionally limited to *length* characters.

**`substring(string, start [, length])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | start | INTEGER | required |
| 3 | length | INTEGER | optional |

Rendering: `substring(string, start, length)`

_No runnable sample yet._

## to_hex

Hexadecimal representation of the number.

**`to_hex(number)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | number | INTEGER | required |

Rendering: `to_hex(number)`

_No runnable sample yet._

## translate

Replaces each character in *from* with the corresponding character in *to*.

**`translate(string, from, to)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | from | TEXT | required |
| 3 | to | TEXT | required |

Rendering: `translate(string, from, to)`

_No runnable sample yet._

## trim

Removes *characters* (default spaces) from both ends.

**`trim(string [, characters])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | characters | TEXT | optional |

Rendering: `trim(string, characters)`

_No runnable sample yet._

## upper

Converts the string to upper case.

**`upper(string)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |

Rendering: `upper(string)`

_No runnable sample yet._

# Pattern Matching

## regexp_count

Number of matches of *pattern* in the string.

**`regexp_count(string, pattern [, start])`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | pattern | TEXT | required |
| 3 | start | INTEGER | optional |

Rendering: `regexp_count(string, pattern, start)`

_No runnable sample yet._

## regexp_like

True if the string matches the regular expression *pattern*.

**`regexp_like(string, pattern [, flags])`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | pattern | TEXT | required |
| 3 | flags | TEXT | optional |

Rendering: `regexp_like(string, pattern, flags)`

_No runnable sample yet._

## regexp_replace

Replaces substrings matching the regular expression *pattern* with *replacement*.

**`regexp_replace(string, pattern, replacement [, flags])`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | pattern | TEXT | required |
| 3 | replacement | TEXT | required |
| 4 | flags | TEXT | optional |

Rendering: `regexp_replace(string, pattern, replacement, flags)`

_No runnable sample yet._

## regexp_substr

First substring matching the regular expression *pattern*.

**`regexp_substr(string, pattern)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | pattern | TEXT | required |

Rendering: `regexp_substr(string, pattern)`

_No runnable sample yet._

# Data Type Formatting Functions

## to_char

Formats a number, date or timestamp using the dialect-native *format* mask.

**`to_char(value, format)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | format | TEXT | required |

Rendering: `to_char(value, format)`

_No runnable sample yet._

## to_number

Parses a string into a number using the dialect-native *format* mask.

**`to_number(value, format)`** → DECIMAL

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEXT | required |
| 2 | format | TEXT | required |

Rendering: `to_number(value, format)`

_No runnable sample yet._

# Date/Time Functions

## calendar_distance

Calendar (civil) distance from *start* to *end* as a mixed interval (years/months/days + clock): calendar_distance('2025-01-01', '2026-05-15 12:00') = 1y4mo14d12h. Use ts − ts for a fixed elapsed span.

**`calendar_distance(start, end)`** → INTERVAL

| # | argument | family | required |
|---|---|---|---|
| 1 | start | TEMPORAL | required |
| 2 | end | TEMPORAL | required |

Rendering: `calendar_distance(…) — dialect-specific rendering`

_No runnable sample yet._

## clock_now

Current timestamp at the time of the call.

**`clock_now()`** → TIMESTAMP

Arguments: none.

Rendering: `CURRENT_TIMESTAMP`

_No runnable sample yet._

## date

Casts *value* to a DATE, discarding any time component — the type-named cast; `to_date` is the portable form with explicit ISO-8601 text rules.

**`date(value)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `date(value)`

_No runnable sample yet._

## date_trunc

Truncates *value* to the given precision, e.g. date_trunc('month', o.order_date).

**`date_trunc(part, value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | part | TEXT | required |
| 2 | value | TEMPORAL | required |

Rendering: `date_trunc(part, value)`

_No runnable sample yet._

## day

Extracts the day part of a date or timestamp.

**`day(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `day(value)`

_No runnable sample yet._

## day_add

*value* shifted by *n* days; *n* may be any expression.

**`day_add(value, n)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |
| 2 | n | INTEGER | required |

Rendering: `(value + INTERVAL (n) DAY)`

_No runnable sample yet._

## day_begin

Start of the day (time becomes 00:00:00).

**`day_begin(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `date_trunc('day', value)`

_No runnable sample yet._

## days_between

Signed number of whole days from *start* to *end*.

**`days_between(start, end)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | start | TEMPORAL | required |
| 2 | end | TEMPORAL | required |

Rendering: `DATEDIFF(day, start, end)`

**Sample** — `temporal` database

```kql
FIND check_temporal c
FETCH days_between("2023-03-01", "2023-02-01") signed_minus28
```

```sql
-- days_between: signed day distance between two dates.
-- ignore=sqlite
SELECT
  DATEDIFF(day, CAST('2023-03-01' AS DATE), CAST('2023-02-01' AS DATE)) AS signed_minus28
FROM
 check_temporal c
```

## hour

Extracts the hour part of a date or timestamp.

**`hour(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `hour(value)`

_No runnable sample yet._

## make_date

Builds a date from year, month and day.

**`make_date(year, month, day)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | year | INTEGER | required |
| 2 | month | INTEGER | required |
| 3 | day | INTEGER | required |

Rendering: `make_date(year, month, day)`

_No runnable sample yet._

## make_time

Builds a time from hour, minute and second.

**`make_time(hour, minute, second)`** → TIME

| # | argument | family | required |
|---|---|---|---|
| 1 | hour | INTEGER | required |
| 2 | minute | INTEGER | required |
| 3 | second | INTEGER | required |

Rendering: `make_time(hour, minute, second)`

_No runnable sample yet._

## make_timestamp

Builds a timestamp from its six components.

**`make_timestamp(year, month, day, hour, minute, second)`** → TIMESTAMP

| # | argument | family | required |
|---|---|---|---|
| 1 | year | INTEGER | required |
| 2 | month | INTEGER | required |
| 3 | day | INTEGER | required |
| 4 | hour | INTEGER | required |
| 5 | minute | INTEGER | required |
| 6 | second | INTEGER | required |

Rendering: `make_timestamp(year, month, day, hour, minute, second)`

_No runnable sample yet._

## minute

Extracts the minute part of a date or timestamp.

**`minute(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `minute(value)`

_No runnable sample yet._

## month

Extracts the month part of a date or timestamp.

**`month(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `month(value)`

_No runnable sample yet._

## month_add

*value* shifted by *n* months, clamped to the end of the month: month_add('2023-01-31', 1) = 2023-02-28.

**`month_add(value, n)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |
| 2 | n | INTEGER | required |

Rendering: `(value + INTERVAL (n) MONTH)`

_No runnable sample yet._

## month_begin

First day of the month (pairs with month_end).

**`month_begin(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `date_trunc('month', value)`

_No runnable sample yet._

## month_end

Last day of the month (Excel EOMONTH): month_end('2024-02-10') = 2024-02-29.

**`month_end(value)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `EOMONTH(value)`

_No runnable sample yet._

## months_between

Signed number of whole months completed from *start* to *end*; months_between('2023-01-31', '2023-03-30') = 1 — the second month is not complete.

**`months_between(start, end)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | start | TEMPORAL | required |
| 2 | end | TEMPORAL | required |

Rendering: `(DATEDIFF(month, start, end) - CASE WHEN DAY(end) < DAY(start) THEN 1 ELSE 0 END)`

**Sample** — `temporal` database

```kql
FIND check_temporal c
FETCH months_between("2023-01-31", "2023-03-31") whole_two
```

```sql
-- months_between: whole completed months between two dates.
-- ignore=sqlite
SELECT
  (DATEDIFF(month, CAST('2023-01-31' AS DATE), CAST('2023-03-31' AS DATE)) - CASE WHEN DAY(CAST('2023-03-31' AS DATE)) < DAY(CAST('2023-01-31' AS DATE)) THEN 1 ELSE 0 END) AS whole_two
FROM
 check_temporal c
```

## now

Current timestamp at statement execution.

**`now()`** → TIMESTAMP

Arguments: none.

Rendering: `CURRENT_TIMESTAMP`

_No runnable sample yet._

## quarter_begin

First day of the quarter.

**`quarter_begin(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `date_trunc('quarter', value)`

_No runnable sample yet._

## quarter_end

Last day of the quarter.

**`quarter_end(value)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, value), 0), 2)`

_No runnable sample yet._

## second

Extracts the second part of a date or timestamp.

**`second(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `second(value)`

_No runnable sample yet._

## time

Casts *value* to a TIME, discarding the date part.

**`time(value)`** → TIME

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `time(value)`

_No runnable sample yet._

## timestamp

Casts *value* to a TIMESTAMP; a DATE is extended with midnight (00:00:00).

**`timestamp(value)`** → TIMESTAMP

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `timestamp(value)`

_No runnable sample yet._

## today

Current date.

**`today()`** → DATE

Arguments: none.

Rendering: `CURRENT_DATE`

_No runnable sample yet._

## year

Extracts the year part of a date or timestamp.

**`year(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `year(value)`

_No runnable sample yet._

## year_add

*value* shifted by *n* years, clamped (Feb 29 + 1 year = Feb 28).

**`year_add(value, n)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |
| 2 | n | INTEGER | required |

Rendering: `(value + INTERVAL (n) YEAR)`

_No runnable sample yet._

## year_begin

First day of the year.

**`year_begin(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `date_trunc('year', value)`

_No runnable sample yet._

## year_end

Last day of the year (December 31).

**`year_end(value)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |

Rendering: `DATEFROMPARTS(YEAR(value), 12, 31)`

_No runnable sample yet._

## years_between

Signed number of whole years completed from *start* to *end*.

**`years_between(start, end)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | start | TEMPORAL | required |
| 2 | end | TEMPORAL | required |

Rendering: `CAST(EXTRACT(YEAR FROM age(end, start)) AS INTEGER)`

_No runnable sample yet._

# Conditional Expressions

## case

Searched CASE: case(cond1, result1, ..., [else]) -> CASE WHEN cond1 THEN result1 ... [ELSE else] END.

**`case(condition, result, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | condition | BOOLEAN | required |
| 2 | result | ANY | required |

Rendering: `case(…) — dialect-specific rendering`

_No runnable sample yet._

## coalesce

First non-null argument.

**`coalesce(value, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `coalesce(…) — dialect-specific rendering`

_No runnable sample yet._

# Aggregate Functions

## avg

Average of the input values.

*Aggregate function.*

**`avg(value)`** → FLOAT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ADDITIVE | required |

Rendering: `avg(value)`

_No runnable sample yet._

## count

Number of input rows or non-null values.

*Aggregate function.*

**`count([, value])`** → BIGINT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | optional |

Rendering: `count(value)`

_No runnable sample yet._

## max

Maximum input value.

*Aggregate function.*

**`max(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `max(value)`

_No runnable sample yet._

## min

Minimum input value.

*Aggregate function.*

**`min(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `min(value)`

_No runnable sample yet._

## string_agg

Concatenates non-null input values into a string, separated by *separator*.

*Aggregate function.*

**`string_agg(value, separator)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | separator | TEXT | required |

Rendering: `string_agg(value, separator)`

_No runnable sample yet._

## sum

Sum of the input values.

*Aggregate function.*

**`sum(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ADDITIVE | required |

Rendering: `sum(value)`

_No runnable sample yet._

# Type Conversion

## to_bigint

Converts a numeric or text value to a 64-bit integer. Use instead of `to_integer` when values may exceed ±2 147 483 647.

**`to_bigint(value)`** → BIGINT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS BIGINT)`

_No runnable sample yet._

## to_boolean

Converts a numeric value to BOOLEAN (`0` = false, non-zero = true).

**`to_boolean(value)`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS BOOLEAN)`

_No runnable sample yet._

## to_date

Converts a timestamp or date-compatible value to a DATE, discarding any time component. Text input must be ISO 8601 (`YYYY-MM-DD`); other formats are dialect-dependent — use `parse_date` for an explicit format mask.

**`to_date(value)`** → DATE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS DATE)`

_No runnable sample yet._

## to_decimal

Converts *value* to a fixed-point decimal. *precision* is the total number of significant digits, *scale* the digits after the point — `to_decimal(value, 10, 2)` holds up to 99 999 999.99.

**`to_decimal(value, precision, scale)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | precision | INTEGER | required |
| 3 | scale | INTEGER | required |

Rendering: `CAST(value AS DECIMAL(precision, scale))`

_No runnable sample yet._

## to_double

Converts a numeric or text value to double-precision float.

**`to_double(value)`** → DOUBLE

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS DOUBLE)`

_No runnable sample yet._

## to_float

Converts a numeric or text value to single-precision float. Fractional precision may be lost relative to the source.

**`to_float(value)`** → FLOAT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS FLOAT)`

_No runnable sample yet._

## to_integer

Converts a numeric or text value to a 32-bit integer. The fractional part is truncated, not rounded; overflow behaviour is dialect-defined.

**`to_integer(value)`** → INTEGER

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS INTEGER)`

_No runnable sample yet._

## to_smallint

Converts a numeric or text value to a 16-bit integer (range −32 768 to 32 767). Overflow behaviour is dialect-defined.

**`to_smallint(value)`** → SMALLINT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS SMALLINT)`

_No runnable sample yet._

## to_text

Converts a BLOB *value* to TEXT.

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | BLOB | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | BOOLEAN | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | DATE | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | DECIMAL | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | FLOAT | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | INTEGER | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TIME | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | INTERVAL | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TIMESTAMP | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEXT | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | JSON | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**`to_text(value)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | UUID | required |

Rendering: `CAST(value AS VARCHAR(MAX))`

**Sample** — `typecheck` database

```kql
FIND check_type c
FETCH to_text(c.type_integer) integer_text
```

```sql
-- to_text: render an integer column as text.
SELECT
  CAST(c.type_integer AS VARCHAR(MAX)) AS integer_text
FROM
 check_type c
```

## to_time

Converts a timestamp or time-compatible value to a TIME, discarding the date part. Text input must be in `HH:MM:SS` format.

**`to_time(value)`** → TIME

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS TIME)`

_No runnable sample yet._

## to_timestamp

Converts a date or text value to a TIMESTAMP. Text input must be ISO 8601 (`YYYY-MM-DD HH:MM:SS`); a DATE is extended with midnight.

**`to_timestamp(value)`** → TIMESTAMP

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `CAST(value AS TIMESTAMP)`

_No runnable sample yet._

## to_varchar

Converts *value* to a variable-length string with an explicit maximum length — useful when the target column has a defined width.

**`to_varchar(value, length)`** → TEXT

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | length | INTEGER | required |

Rendering: `CAST(value AS VARCHAR(length))`

_No runnable sample yet._

# Arithmetic Operators

## add

Addition (`+`): numeric addition, or temporal arithmetic per the result-type algebra — DATE/TIMESTAMP/TIME + DURATION, DATE + TIME, and DURATION + DURATION (see docs/TEMPORAL.md).

**`add(left, right, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `add(…) — dialect-specific rendering`

_No runnable sample yet._

## divide

Division (`/`): numeric division; the result is decimal/double. Dividing a DURATION is not defined.

**`divide(left, right, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `divide(…) — dialect-specific rendering`

_No runnable sample yet._

## minus

Subtraction (`-`): numeric subtraction, or temporal — DATE − DATE and TIMESTAMP − TIMESTAMP yield a DURATION, and a temporal value − DURATION shifts it (see docs/TEMPORAL.md).

**`minus(left, right, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `minus(…) — dialect-specific rendering`

_No runnable sample yet._

## multiply

Multiplication (`*`): numeric multiplication, or DURATION × integer scaled component-wise (see docs/TEMPORAL.md).

**`multiply(left, right, ...)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `multiply(…) — dialect-specific rendering`

_No runnable sample yet._

## negate

Arithmetic negation (`-x`): negates a number, or flips the sign of every component of a DURATION.

**`negate(value)`** → argument-dependent

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `negate(…) — dialect-specific rendering`

_No runnable sample yet._

# Comparison Operators

## <

True if *left* is less than *right*.

**`left < right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `left < right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight < 20
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## <=

True if *left* is less than or equal to *right*.

**`left <= right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `left <= right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight <= 20
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## =

True if *left* equals *right*.

**`left = right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `left = right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.ship_city = 'London'
FETCH o.order_id, o.ship_city
```

```sql
-- unknown sample database: northwind
```

## >

True if *left* is greater than *right*.

**`left > right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `left > right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight > 100
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## >=

True if *left* is greater than or equal to *right*.

**`left >= right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | ANY | required |
| 2 | right | ANY | required |

Rendering: `left >= right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight >= 100
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## BETWEEN

True if *value* lies within the inclusive range [*low*, *high*].

**`value BETWEEN low AND high`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | low | ANY | required |
| 3 | high | ANY | required |

Rendering: `value BETWEEN low AND high`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight BETWEEN 10 AND 50
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## IN

True if *value* equals any of the listed *items*.

**`value IN (items, …)`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |
| 2 | items | ANY | required |

Rendering: `value IN (items)`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.ship_city IN ('London', 'Paris')
FETCH o.order_id, o.ship_city
```

```sql
-- unknown sample database: northwind
```

## ISNULL

True if *value* is NULL.

**`value ISNULL`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | value | ANY | required |

Rendering: `value IS NULL`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.shipped_date ISNULL
FETCH o.order_id, o.shipped_date
```

```sql
-- unknown sample database: northwind
```

## LIKE

True if *string* matches the SQL LIKE *pattern* (`%` and `_` wildcards). Both operands must be TEXT.

**`string LIKE pattern`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | string | TEXT | required |
| 2 | pattern | TEXT | required |

Rendering: `string LIKE pattern`

**Sample** — `northwind` database

```kql
FIND customers c
FILTER c.company_name LIKE 'A%'
FETCH c.customer_id, c.company_name
```

```sql
-- unknown sample database: northwind
```

# Logical Operators

## AND

True if both operands are true.

**`left AND right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | BOOLEAN | required |
| 2 | right | BOOLEAN | required |

Rendering: `left AND right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.freight > 10 AND o.freight < 100
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## NOT

Negates the operand.

**`NOT operand`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | operand | BOOLEAN | required |

Rendering: `NOT operand`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER NOT o.freight > 100
FETCH o.order_id, o.freight
```

```sql
-- unknown sample database: northwind
```

## OR

True if either operand is true.

**`left OR right`** → BOOLEAN

| # | argument | family | required |
|---|---|---|---|
| 1 | left | BOOLEAN | required |
| 2 | right | BOOLEAN | required |

Rendering: `left OR right`

**Sample** — `northwind` database

```kql
FIND orders o
FILTER o.ship_city = 'London' OR o.ship_city = 'Paris'
FETCH o.order_id, o.ship_city
```

```sql
-- unknown sample database: northwind
```

# Other Functions

## at_zone

Reads *value* as a model-zone wall-clock value and returns its wall-clock in the named zone (e.g. for day-bucketing: date(at_zone(o.ts, 'Europe/Berlin'))).

**`at_zone(value, zone)`** → TIMESTAMP

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |
| 2 | zone | TEXT | required |

Rendering: `at_zone(…) — dialect-specific rendering`

_No runnable sample yet._

## to_utc

Inverse of at_zone: reads *value* as a wall-clock in the named zone and returns the model-zone wall-clock value.

**`to_utc(value, zone)`** → TIMESTAMP

| # | argument | family | required |
|---|---|---|---|
| 1 | value | TEMPORAL | required |
| 2 | zone | TEXT | required |

Rendering: `to_utc(…) — dialect-specific rendering`

_No runnable sample yet._
