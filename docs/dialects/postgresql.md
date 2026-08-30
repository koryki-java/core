---
menuTitle: "PostgreSQL"
parent: "FUNCTIONS"
order: 25
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: postgresql

What is specific to postgresql: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| bit_and | other | aggregate | native | `bit_and(…)` |  |
| bit_length | string | scalar | standard |  |  |
| bit_or | other | aggregate | native | `bit_or(…)` |  |
| bit_xor | other | aggregate | native | `bit_xor(…)` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | standard |  |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | overridden | `clock_timestamp()` |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | standard |  |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | standard |  | no OVER clause |
| date_trunc | datetime | scalar | standard |  |  |
| day | datetime | scalar | overridden | `EXTRACT(DAY FROM value)` |  |
| day_add | datetime | scalar | overridden | `(value + n * INTERVAL '1 day')` |  |
| day_begin | datetime | scalar | standard |  |  |
| dayofweek | datetime | scalar | overridden | `EXTRACT(ISODOW FROM value)` |  |
| dayofyear | datetime | scalar | overridden | `EXTRACT(DOY FROM value)` |  |
| days_between | datetime | scalar | overridden | `CAST(trunc(EXTRACT(EPOCH FROM (CAST(end AS timestamp) - CAST(start AS timestamp))) / 86400.0) AS INTEGER)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  |  |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| format | other | scalar | native | `format(…)` |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | overridden | `EXTRACT(HOUR FROM value)` |  |
| hour_begin | datetime | scalar | standard |  |  |
| initcap | string | scalar | standard |  |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | standard |  |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | standard |  |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | standard |  |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | standard |  |  |
| make_time | datetime | scalar | standard |  |  |
| make_timestamp | datetime | scalar | standard |  |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | standard |  |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | overridden | `EXTRACT(MINUTE FROM value)` |  |
| minute_begin | datetime | scalar | standard |  |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | overridden | `EXTRACT(MONTH FROM value)` |  |
| month_add | datetime | scalar | overridden | `(value + n * INTERVAL '1 month')` |  |
| month_begin | datetime | scalar | standard |  |  |
| month_end | datetime | scalar | overridden | `CAST(date_trunc('month', value) + INTERVAL '1 month - 1 day' AS DATE)` |  |
| months_between | datetime | scalar | standard |  |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | overridden | `statement_timestamp()` |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | standard |  |  |
| overlay | string | scalar | standard |  |  |
| parse_date | datetime | scalar | overridden | `TO_DATE(value, format)` |  |
| parse_time | datetime | scalar | overridden | `TO_TIMESTAMP(value, format)::TIME` |  |
| parse_timestamp | datetime | scalar | overridden | `TO_TIMESTAMP(value, format)::TIMESTAMP` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | standard |  |  |
| power | math | scalar | standard |  | results differ |
| quarter | datetime | scalar | overridden | `EXTRACT(QUARTER FROM value)` |  |
| quarter_begin | datetime | scalar | standard |  |  |
| quarter_end | datetime | scalar | overridden | `CAST(date_trunc('quarter', value) + INTERVAL '3 months - 1 day' AS DATE)` |  |
| random | math | scalar | standard |  |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | standard |  |  |
| regexp_like | pattern_matching | scalar | standard |  |  |
| regexp_replace | pattern_matching | scalar | overridden | `regexp_replace(string, pattern, replacement, 'g')` |  |
| regexp_substr | pattern_matching | scalar | standard |  |  |
| repeat | string | scalar | standard |  |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | standard |  |  |
| round | math | scalar | overridden | `round(value)`<br>`round(CAST(value AS numeric), scale)` |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | standard |  |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | overridden | `CAST(trunc(EXTRACT(SECOND FROM value)) AS INTEGER)` |  |
| sha256 | other | scalar | native | `sha256(…)` |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | standard |  |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | standard |  |  |
| string_agg | aggregate | aggregate | standard |  |  |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | standard |  |  |
| to_boolean | conversion | scalar | overridden | `(value <> 0)` |  |
| to_char | formatting | scalar | standard |  |  |
| to_date | conversion | scalar | standard |  |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | overridden | `CAST(value AS DOUBLE PRECISION)` |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | overridden | `upper(to_hex(number))` |  |
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
| trunc | math | scalar | overridden | `trunc(value)`<br>`trunc(CAST(value AS numeric), scale)` |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | overridden | `EXTRACT(WEEK FROM value)` |  |
| week_begin | datetime | scalar | standard |  |  |
| week_end | datetime | scalar | overridden | `CAST(date_trunc('week', value) + INTERVAL '6 days' AS DATE)` |  |
| year | datetime | scalar | overridden | `EXTRACT(YEAR FROM value)` |  |
| year_add | datetime | scalar | overridden | `(value + n * INTERVAL '1 year')` |  |
| year_begin | datetime | scalar | standard |  |  |
| year_end | datetime | scalar | overridden | `CAST(date_trunc('year', value) + INTERVAL '1 year - 1 day' AS DATE)` |  |
| year_month | datetime | scalar | overridden | `CAST(EXTRACT(YEAR FROM value) * 100 + EXTRACT(MONTH FROM value) AS INTEGER)` |  |
| years_between | datetime | scalar | standard |  |  |
