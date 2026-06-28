---
menuTitle: "SQL Server"
parent: "FUNCTIONS"
order: 24
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: mssql

| Function | Category | Kind | Status | Rendering |
|---|---|---|---|---|
| < | comparison | scalar | standard | `left < right` |
| <= | comparison | scalar | standard | `left <= right` |
| = | comparison | scalar | standard | `left = right` |
| > | comparison | scalar | standard | `left > right` |
| >= | comparison | scalar | standard | `left >= right` |
| AND | logical | scalar | standard | `left AND right` |
| BETWEEN | comparison | scalar | standard | `value BETWEEN low AND high` |
| IN | comparison | scalar | standard | `value IN (items)` |
| ISNULL | comparison | scalar | standard | `value IS NULL` |
| LIKE | comparison | scalar | standard | `string LIKE pattern` |
| NOT | logical | scalar | standard | `NOT operand` |
| OR | logical | scalar | standard | `left OR right` |
| abs | math | scalar | standard | `abs(value)` |
| add | arithmetic | scalar | standard | `add(…) — dialect-specific rendering` |
| ascii | string | scalar | standard | `ascii(character)` |
| at_zone | other | scalar | standard | `at_zone(…) — dialect-specific rendering` |
| avg | aggregate | aggregate | standard | `avg(value)` |
| bit_length | string | scalar | standard | `bit_length(string)` |
| btrim | string | scalar | standard | `btrim(string, characters)` |
| calendar_distance | datetime | scalar | standard | `calendar_distance(…) — dialect-specific rendering` |
| case | conditional | scalar | standard | `case(…) — dialect-specific rendering` |
| ceil | math | scalar | overridden | `CEILING(value)` |
| ceiling | math | scalar | standard | `ceiling(value)` |
| char_length | string | scalar | overridden | `LEN(string)` |
| character_length | string | scalar | overridden | `LEN(string)` |
| chr | string | scalar | standard | `chr(code)` |
| clock_now | datetime | scalar | standard | `CURRENT_TIMESTAMP` |
| coalesce | conditional | scalar | standard | `coalesce(…) — dialect-specific rendering` |
| concat | string | scalar | standard | `concat(value)` |
| concat_ws | string | scalar | standard | `concat_ws(separator)` |
| count | aggregate | aggregate | standard | `count(value)` |
| date | datetime | scalar | standard | `date(value)` |
| date_trunc | datetime | scalar | standard | `date_trunc(part, value)` |
| day | datetime | scalar | standard | `day(value)` |
| day_add | datetime | scalar | standard | `(value + INTERVAL (n) DAY)` |
| day_begin | datetime | scalar | standard | `date_trunc('day', value)` |
| days_between | datetime | scalar | overridden | `DATEDIFF(day, start, end)` |
| divide | arithmetic | scalar | standard | `divide(…) — dialect-specific rendering` |
| floor | math | scalar | standard | `floor(value)` |
| hour | datetime | scalar | standard | `hour(value)` |
| initcap | string | scalar | standard | `initcap(string)` |
| left | string | scalar | standard | `left(string, n)` |
| length | string | scalar | overridden | `LEN(string)` |
| lower | string | scalar | standard | `lower(string)` |
| lpad | string | scalar | standard | `lpad(string, length, fill)` |
| ltrim | string | scalar | standard | `ltrim(string, characters)` |
| make_date | datetime | scalar | standard | `make_date(year, month, day)` |
| make_time | datetime | scalar | standard | `make_time(hour, minute, second)` |
| make_timestamp | datetime | scalar | standard | `make_timestamp(year, month, day, hour, minute, second)` |
| max | aggregate | aggregate | standard | `max(value)` |
| md5 | string | scalar | standard | `md5(string)` |
| min | aggregate | aggregate | standard | `min(value)` |
| minus | arithmetic | scalar | standard | `minus(…) — dialect-specific rendering` |
| minute | datetime | scalar | standard | `minute(value)` |
| mod | math | scalar | standard | `mod(dividend, divisor)` |
| month | datetime | scalar | standard | `month(value)` |
| month_add | datetime | scalar | standard | `(value + INTERVAL (n) MONTH)` |
| month_begin | datetime | scalar | standard | `date_trunc('month', value)` |
| month_end | datetime | scalar | overridden | `EOMONTH(value)` |
| months_between | datetime | scalar | overridden | `(DATEDIFF(month, start, end) - CASE WHEN DAY(end) < DAY(start) THEN 1 ELSE 0 END)` |
| multiply | arithmetic | scalar | standard | `multiply(…) — dialect-specific rendering` |
| negate | arithmetic | scalar | standard | `negate(…) — dialect-specific rendering` |
| now | datetime | scalar | standard | `CURRENT_TIMESTAMP` |
| octet_length | string | scalar | standard | `octet_length(string)` |
| overlay | string | scalar | standard | `overlay(string, replacement, start, length)` |
| pi | math | scalar | standard | `pi()` |
| position | string | scalar | standard | `POSITION(substr IN str)` |
| power | math | scalar | standard | `power(base, exponent)` |
| quarter_begin | datetime | scalar | standard | `date_trunc('quarter', value)` |
| quarter_end | datetime | scalar | overridden | `EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, value), 0), 2)` |
| random | math | scalar | standard | `random()` |
| regexp_count | pattern_matching | scalar | standard | `regexp_count(string, pattern, start)` |
| regexp_like | pattern_matching | scalar | standard | `regexp_like(string, pattern, flags)` |
| regexp_replace | pattern_matching | scalar | standard | `regexp_replace(string, pattern, replacement, flags)` |
| regexp_substr | pattern_matching | scalar | standard | `regexp_substr(string, pattern)` |
| repeat | string | scalar | standard | `repeat(string, number)` |
| replace | string | scalar | standard | `replace(string, from, to)` |
| reverse | string | scalar | standard | `reverse(string)` |
| right | string | scalar | standard | `right(string, n)` |
| round | math | scalar | standard | `round(value, scale)` |
| rpad | string | scalar | standard | `rpad(string, length, fill)` |
| rtrim | string | scalar | standard | `rtrim(string, characters)` |
| second | datetime | scalar | standard | `second(value)` |
| split_part | string | scalar | standard | `split_part(string, delimiter, n)` |
| sqrt | math | scalar | standard | `sqrt(value)` |
| starts_with | string | scalar | standard | `starts_with(string, prefix)` |
| string_agg | aggregate | aggregate | standard | `string_agg(value, separator)` |
| strpos | string | scalar | standard | `strpos(string, substring)` |
| substr | string | scalar | overridden | `SUBSTRING(string, start, length)` |
| substring | string | scalar | standard | `substring(string, start, length)` |
| sum | aggregate | aggregate | standard | `sum(value)` |
| time | datetime | scalar | standard | `time(value)` |
| timestamp | datetime | scalar | standard | `timestamp(value)` |
| to_bigint | conversion | scalar | standard | `CAST(value AS BIGINT)` |
| to_boolean | conversion | scalar | standard | `CAST(value AS BOOLEAN)` |
| to_char | formatting | scalar | standard | `to_char(value, format)` |
| to_date | conversion | scalar | standard | `CAST(value AS DATE)` |
| to_decimal | conversion | scalar | standard | `CAST(value AS DECIMAL(precision, scale))` |
| to_double | conversion | scalar | standard | `CAST(value AS DOUBLE)` |
| to_float | conversion | scalar | standard | `CAST(value AS FLOAT)` |
| to_hex | string | scalar | standard | `to_hex(number)` |
| to_integer | conversion | scalar | standard | `CAST(value AS INTEGER)` |
| to_number | formatting | scalar | standard | `to_number(value, format)` |
| to_smallint | conversion | scalar | standard | `CAST(value AS SMALLINT)` |
| to_text | conversion | scalar | overridden | `CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))` |
| to_time | conversion | scalar | standard | `CAST(value AS TIME)` |
| to_timestamp | conversion | scalar | standard | `CAST(value AS TIMESTAMP)` |
| to_utc | other | scalar | standard | `to_utc(…) — dialect-specific rendering` |
| to_varchar | conversion | scalar | standard | `CAST(value AS VARCHAR(length))` |
| today | datetime | scalar | standard | `CURRENT_DATE` |
| translate | string | scalar | standard | `translate(string, from, to)` |
| trim | string | scalar | standard | `trim(string, characters)` |
| upper | string | scalar | standard | `upper(string)` |
| year | datetime | scalar | standard | `year(value)` |
| year_add | datetime | scalar | standard | `(value + INTERVAL (n) YEAR)` |
| year_begin | datetime | scalar | standard | `date_trunc('year', value)` |
| year_end | datetime | scalar | overridden | `DATEFROMPARTS(YEAR(value), 12, 31)` |
| years_between | datetime | scalar | standard | `CAST(EXTRACT(YEAR FROM age(end, start)) AS INTEGER)` |
