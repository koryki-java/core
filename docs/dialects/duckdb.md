---
menuTitle: "DuckDB"
parent: "FUNCTIONS"
order: 21
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: duckdb

What is specific to duckdb: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| bit_count | other | scalar | native | `bit_count(…)` |  |
| bit_length | string | scalar | standard |  |  |
| bit_or | other | aggregate | native | `bit_or(…)` |  |
| bit_xor | other | aggregate | native | `bit_xor(…)` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | standard |  |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | standard |  |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | standard |  |  |
| date_trunc | datetime | scalar | standard |  |  |
| day | datetime | scalar | standard |  |  |
| day_add | datetime | scalar | standard |  |  |
| day_begin | datetime | scalar | standard |  |  |
| dayofweek | datetime | scalar | overridden | `isodow(value)` |  |
| dayofyear | datetime | scalar | standard |  |  |
| days_between | datetime | scalar | standard |  |  |
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
| left | string | scalar | standard |  |  |
| list | other | scalar | native | `[value]` |  |
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
| minute | datetime | scalar | standard |  |  |
| minute_begin | datetime | scalar | standard |  |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | standard |  |  |
| month_add | datetime | scalar | standard |  |  |
| month_begin | datetime | scalar | standard |  |  |
| month_end | datetime | scalar | standard |  |  |
| months_between | datetime | scalar | standard |  |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | overridden | `octet_length(encode(string))` |  |
| overlay | string | scalar | overridden | `overlay(…) — dialect-specific rendering` |  |
| parse_date | datetime | scalar | overridden | `parse_date(…) — dialect-specific rendering` |  |
| parse_time | datetime | scalar | overridden | `parse_time(…) — dialect-specific rendering` |  |
| parse_timestamp | datetime | scalar | overridden | `parse_timestamp(…) — dialect-specific rendering` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | standard |  |  |
| power | math | scalar | standard |  |  |
| printf | other | scalar | native | `printf(…)` |  |
| quarter | datetime | scalar | standard |  |  |
| quarter_begin | datetime | scalar | standard |  |  |
| quarter_end | datetime | scalar | standard |  |  |
| random | math | scalar | standard |  |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | overridden | `regexp_count(…) — dialect-specific rendering` |  |
| regexp_like | pattern_matching | scalar | overridden | `regexp_matches(string, pattern, flags)` |  |
| regexp_replace | pattern_matching | scalar | overridden | `regexp_replace(string, pattern, replacement, 'g')` |  |
| regexp_substr | pattern_matching | scalar | overridden | `regexp_extract(string, pattern)` |  |
| repeat | string | scalar | standard |  |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | standard |  |  |
| round | math | scalar | standard |  |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | standard |  |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | standard |  |  |
| sha256 | other | scalar | native | `sha256(…)` |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split | other | scalar | native | `split(…)` |  |
| split_part | string | scalar | standard |  |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | standard |  |  |
| string_agg | aggregate | aggregate | standard |  |  |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | standard |  |  |
| to_boolean | conversion | scalar | standard |  |  |
| to_char | formatting | scalar | overridden | `to_char(…) — dialect-specific rendering` |  |
| to_date | conversion | scalar | overridden | `CAST(value AS DATE)`<br>`to_date(…) — dialect-specific rendering`<br>`MAKE_DATE(year, month, day)` |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | standard |  |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | standard |  |  |
| to_integer | conversion | scalar | standard |  |  |
| to_interval | other | scalar | native | `to_interval(…) — dialect-specific rendering`<br>`to_years(years) + to_months(months) + to_days(days) + to_hours(hours) + to_minutes(minutes) + to_seconds(seconds)` |  |
| to_number | formatting | scalar | unsupported | `—` |  |
| to_smallint | conversion | scalar | standard |  |  |
| to_text | conversion | scalar | standard |  |  |
| to_time | conversion | scalar | overridden | `CAST(value AS TIME)`<br>`to_time(…) — dialect-specific rendering`<br>`MAKE_TIME(hour, minute, second::DOUBLE)` |  |
| to_timestamp | conversion | scalar | overridden | `CAST(value AS TIMESTAMP)`<br>`to_timestamp(…) — dialect-specific rendering`<br>`to_timestamp(…) — dialect-specific rendering`<br>`MAKE_TIMESTAMP(year, month, day, hour, minute, second)`<br>`timezone(tz, MAKE_TIMESTAMP(year, month, day, hour, minute, second)::TIMESTAMPTZ)` |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | overridden | `today()` |  |
| translate | string | scalar | standard |  |  |
| trim | string | scalar | standard |  |  |
| trunc | math | scalar | standard |  |  |
| truncate | other | scalar | native | `trunc(value)` |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | standard |  |  |
| week_begin | datetime | scalar | standard |  |  |
| week_end | datetime | scalar | standard |  |  |
| year | datetime | scalar | standard |  |  |
| year_add | datetime | scalar | standard |  |  |
| year_begin | datetime | scalar | standard |  |  |
| year_end | datetime | scalar | standard |  |  |
| year_month | datetime | scalar | standard |  |  |
| years_between | datetime | scalar | standard |  |  |
