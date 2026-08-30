---
menuTitle: "Snowflake"
parent: "FUNCTIONS"
order: 23
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: snowflake

What is specific to snowflake: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| ascii | string | scalar | standard |  |  |
| asin | math | scalar | standard |  |  |
| at_zone | datetime | scalar | standard |  |  |
| atan | math | scalar | standard |  |  |
| avg | aggregate | aggregate | standard |  |  |
| bit_length | string | scalar | standard |  |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | overridden | `LENGTH(string)` |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | overridden | `ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT(value), '')` |  |
| concat_ws | string | scalar | overridden | `ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT(), separator)` |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | overridden | `COUNT(DISTINCT value)`<br>`COUNT(DISTINCT value, more)` |  |
| date_trunc | datetime | scalar | standard |  |  |
| day | datetime | scalar | standard |  |  |
| day_add | datetime | scalar | overridden | `DATEADD('day', n, value)` |  |
| day_begin | datetime | scalar | standard |  |  |
| dayofweek | datetime | scalar | overridden | `DAYOFWEEKISO(value)` |  |
| dayofyear | datetime | scalar | standard |  |  |
| days_between | datetime | scalar | overridden | `TRUNC(DATEDIFF(second, start, end) / 86400)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  | results differ |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | standard |  |  |
| hour_begin | datetime | scalar | standard |  |  |
| initcap | string | scalar | standard |  |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | standard |  |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | overridden | `LOG(10, value)` |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | standard |  |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `DATE_FROM_PARTS(year, month, day)` |  |
| make_time | datetime | scalar | overridden | `TIME_FROM_PARTS(hour, minute, second)` |  |
| make_timestamp | datetime | scalar | overridden | `TIMESTAMP_NTZ_FROM_PARTS(year, month, day, hour, minute, second)` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | standard |  |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | standard |  |  |
| minute_begin | datetime | scalar | standard |  |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | standard |  |  |
| month_add | datetime | scalar | overridden | `DATEADD('month', n, value)` |  |
| month_begin | datetime | scalar | standard |  |  |
| month_end | datetime | scalar | standard |  |  |
| months_between | datetime | scalar | overridden | `(DATEDIFF(month, start, end) - CASE WHEN DAY(end) < DAY(start) THEN 1 ELSE 0 END)` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | standard |  |  |
| overlay | string | scalar | overridden | `INSERT(string, start, length, replacement)` |  |
| parse_date | datetime | scalar | overridden | `TO_DATE(value, format)` |  |
| parse_time | datetime | scalar | overridden | `TO_TIME(value, format)` |  |
| parse_timestamp | datetime | scalar | overridden | `TO_TIMESTAMP(value, format)` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | standard |  |  |
| power | math | scalar | standard |  |  |
| quarter | datetime | scalar | standard |  |  |
| quarter_begin | datetime | scalar | standard |  |  |
| quarter_end | datetime | scalar | overridden | `LAST_DAY(value, 'quarter')` |  |
| random | math | scalar | overridden | `UNIFORM(0::float, 1::float, RANDOM())` |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | standard |  |  |
| regexp_like | pattern_matching | scalar | overridden | `(REGEXP_INSTR(string, pattern) > 0)` |  |
| regexp_replace | pattern_matching | scalar | standard |  |  |
| regexp_substr | pattern_matching | scalar | standard |  |  |
| repeat | string | scalar | standard |  |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | standard |  |  |
| round | math | scalar | standard |  |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | standard |  |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | standard |  |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | standard |  |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | overridden | `STARTSWITH(string, prefix)` |  |
| string_agg | aggregate | aggregate | overridden | `LISTAGG(value, separator)`<br>`LISTAGG(value, separator) WITHIN GROUP (ORDER BY order_by)` | no OVER clause |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | standard |  |  |
| to_boolean | conversion | scalar | standard |  |  |
| to_char | formatting | scalar | standard |  |  |
| to_date | conversion | scalar | standard |  |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | standard |  |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | unsupported | `—` |  |
| to_integer | conversion | scalar | standard |  |  |
| to_number | formatting | scalar | standard |  |  |
| to_smallint | conversion | scalar | standard |  |  |
| to_text | conversion | scalar | standard |  |  |
| to_time | conversion | scalar | standard |  |  |
| to_timestamp | conversion | scalar | standard |  |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | standard |  |  |
| translate | string | scalar | standard |  |  |
| trim | string | scalar | standard |  |  |
| trunc | math | scalar | standard |  |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | overridden | `WEEKISO(value)` |  |
| week_begin | datetime | scalar | overridden | `DATEADD(day, -(DAYOFWEEKISO(value) - 1), value)` |  |
| week_end | datetime | scalar | overridden | `DATEADD(day, 7 - DAYOFWEEKISO(value), value)` |  |
| year | datetime | scalar | standard |  |  |
| year_add | datetime | scalar | overridden | `DATEADD('year', n, value)` |  |
| year_begin | datetime | scalar | standard |  |  |
| year_end | datetime | scalar | overridden | `LAST_DAY(value, 'year')` |  |
| year_month | datetime | scalar | standard |  |  |
| years_between | datetime | scalar | overridden | `FLOOR(MONTHS_BETWEEN(end, start) / 12)` |  |
