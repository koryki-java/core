---
menuTitle: "Trino"
parent: "FUNCTIONS"
order: 28
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: trino

What is specific to trino: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

| Function | Category | Kind | Status | Dialect rendering | Notes |
|---|---|---|---|---|---|
| < | comparison | scalar | standard |  |  |
| <= | comparison | scalar | standard |  |  |
| <> | comparison | scalar | standard |  |  |
| = | comparison | scalar | standard |  |  |
| > | comparison | scalar | standard |  |  |
| >= | comparison | scalar | standard |  |  |
| AND | logical | scalar | standard |  |  |
| BETWEEN | comparison | scalar | standard |  |  |
| DISTINCT | comparison | scalar | standard |  |  |
| IN | comparison | scalar | standard |  |  |
| ISNULL | comparison | scalar | standard |  |  |
| LIKE | comparison | scalar | standard |  |  |
| NOT | logical | scalar | standard |  |  |
| OR | logical | scalar | standard |  |  |
| abs | math | scalar | standard |  |  |
| acos | math | scalar | standard |  |  |
| add | arithmetic | scalar | standard |  |  |
| ascii | string | scalar | overridden | `codepoint(cast(substr(character, 1, 1) as varchar(1)))` |  |
| asin | math | scalar | standard |  |  |
| at_zone | datetime | scalar | standard |  |  |
| atan | math | scalar | standard |  |  |
| avg | aggregate | aggregate | overridden | `avg(CAST(value AS DOUBLE))` |  |
| bit_length | string | scalar | overridden | `length(to_utf8(string)) * 8` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | overridden | `length(string)` |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | overridden | `concat_ws('', value)` |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | standard |  |  |
| date_trunc | datetime | scalar | standard |  |  |
| day | datetime | scalar | standard |  |  |
| day_add | datetime | scalar | overridden | `date_add('day', n, value)` |  |
| day_begin | datetime | scalar | standard |  |  |
| dayofweek | datetime | scalar | overridden | `day_of_week(value)` |  |
| dayofyear | datetime | scalar | overridden | `day_of_year(value)` |  |
| days_between | datetime | scalar | overridden | `(date_diff('second', CAST(start AS TIMESTAMP), CAST(end AS TIMESTAMP)) / 86400)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  |  |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | standard |  |  |
| hour_begin | datetime | scalar | standard |  |  |
| initcap | string | scalar | unsupported | `—` |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | overridden | `substr(string, 1, n)` |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | standard |  |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | standard |  |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `date(format('%04d-%02d-%02d', year, month, day))` |  |
| make_time | datetime | scalar | unsupported | `—` |  |
| make_timestamp | datetime | scalar | unsupported | `—` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | unsupported | `—` |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | standard |  |  |
| minute_begin | datetime | scalar | standard |  |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | standard |  |  |
| month_add | datetime | scalar | overridden | `date_add('month', n, value)` |  |
| month_begin | datetime | scalar | standard |  |  |
| month_end | datetime | scalar | overridden | `last_day_of_month(value)` |  |
| months_between | datetime | scalar | overridden | `date_diff('month', start, end)` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | overridden | `length(to_utf8(string))` |  |
| overlay | string | scalar | overridden | `overlay(…) — dialect-specific rendering` |  |
| parse_date | datetime | scalar | overridden | `parse_date(…) — dialect-specific rendering` |  |
| parse_time | datetime | scalar | overridden | `parse_time(…) — dialect-specific rendering` |  |
| parse_timestamp | datetime | scalar | overridden | `parse_timestamp(…) — dialect-specific rendering` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | standard |  |  |
| power | math | scalar | standard |  |  |
| quarter | datetime | scalar | standard |  |  |
| quarter_begin | datetime | scalar | standard |  |  |
| quarter_end | datetime | scalar | overridden | `last_day_of_month(date_trunc('quarter', value) + INTERVAL '2' MONTH)` |  |
| random | math | scalar | standard |  |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | standard |  |  |
| regexp_like | pattern_matching | scalar | standard |  |  |
| regexp_replace | pattern_matching | scalar | standard |  |  |
| regexp_substr | pattern_matching | scalar | overridden | `regexp_extract(string, pattern)` |  |
| repeat | string | scalar | overridden | `array_join(repeat(string, number), '')` |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | overridden | `substr(string, CASE WHEN n <= 0 THEN length(string) + 1 ELSE GREATEST(length(string) - n + 1, 1) END)` |  |
| round | math | scalar | standard |  |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | standard |  |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | standard |  |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | standard |  |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | standard |  |  |
| string_agg | aggregate | aggregate | overridden | `array_join(array_agg(value), separator)`<br>`array_join(array_agg(value ORDER BY order_by), separator)` | no OVER clause |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | standard |  |  |
| to_boolean | conversion | scalar | standard |  |  |
| to_char | formatting | scalar | overridden | `to_char(…) — dialect-specific rendering` |  |
| to_date | conversion | scalar | standard |  |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | standard |  |  |
| to_float | conversion | scalar | overridden | `CAST(value AS REAL)` |  |
| to_hex | string | scalar | overridden | `upper(to_base(number, 16))` |  |
| to_integer | conversion | scalar | standard |  |  |
| to_number | formatting | scalar | unsupported | `—` |  |
| to_smallint | conversion | scalar | standard |  |  |
| to_text | conversion | scalar | overridden | `CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)`<br>`CAST(value AS VARCHAR)` |  |
| to_time | conversion | scalar | standard |  |  |
| to_timestamp | conversion | scalar | standard |  |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | standard |  |  |
| translate | string | scalar | standard |  |  |
| trim | string | scalar | standard |  |  |
| trunc | math | scalar | overridden | `truncate(value)`<br>`truncate(CAST(value AS DECIMAL(38,10)), scale)` |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | standard |  |  |
| week_begin | datetime | scalar | standard |  |  |
| week_end | datetime | scalar | overridden | `CAST(date_trunc('week', value) + INTERVAL '6' DAY AS DATE)` |  |
| year | datetime | scalar | standard |  |  |
| year_add | datetime | scalar | overridden | `date_add('year', n, value)` |  |
| year_begin | datetime | scalar | standard |  |  |
| year_end | datetime | scalar | overridden | `last_day_of_month(date_trunc('year', value) + INTERVAL '11' MONTH)` |  |
| year_month | datetime | scalar | standard |  |  |
| years_between | datetime | scalar | overridden | `date_diff('year', start, end)` |  |
