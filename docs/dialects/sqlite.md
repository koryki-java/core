---
menuTitle: "SQLite"
parent: "FUNCTIONS"
order: 27
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: sqlite

What is specific to sqlite: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| ascii | string | scalar | overridden | `unicode(character)` |  |
| asin | math | scalar | standard |  |  |
| at_zone | datetime | scalar | unsupported | `—` |  |
| atan | math | scalar | standard |  |  |
| avg | aggregate | aggregate | standard |  |  |
| bit_length | string | scalar | overridden | `length(CAST(string AS BLOB)) * 8` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | overridden | `length(string)` |  |
| chr | string | scalar | overridden | `char(code)` |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | standard |  |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | overridden | `COUNT(DISTINCT value)`<br>`count_distinct(value, more) — unsupported` | no OVER clause |
| date_trunc | datetime | scalar | overridden | `date_trunc(…) — dialect-specific rendering` |  |
| day | datetime | scalar | overridden | `CAST(strftime('%d', value) AS INTEGER)` |  |
| day_add | datetime | scalar | overridden | `date(value, printf('%+d days', n))` |  |
| day_begin | datetime | scalar | overridden | `datetime(value, 'start of day')` |  |
| dayofweek | datetime | scalar | overridden | `(((CAST(strftime('%w', value) AS INTEGER) + 6) % 7) + 1)` |  |
| dayofyear | datetime | scalar | overridden | `CAST(strftime('%j', value) AS INTEGER)` |  |
| days_between | datetime | scalar | overridden | `CAST(julianday(end) - julianday(start) AS INTEGER)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  | results differ |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | overridden | `max(value, more)` |  |
| hour | datetime | scalar | overridden | `CAST(strftime('%H', value) AS INTEGER)` |  |
| hour_begin | datetime | scalar | overridden | `datetime(strftime('%Y-%m-%d %H:00:00', value))` |  |
| initcap | string | scalar | unsupported | `—` |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | overridden | `min(value, more)` |  |
| left | string | scalar | overridden | `substr(string, 1, n)` |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | standard |  |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | unsupported | `—` |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `date(printf('%04d-%02d-%02d', year, month, day))` |  |
| make_time | datetime | scalar | unsupported | `—` |  |
| make_timestamp | datetime | scalar | unsupported | `—` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | unsupported | `—` |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | overridden | `CAST(strftime('%M', value) AS INTEGER)` |  |
| minute_begin | datetime | scalar | overridden | `datetime(strftime('%Y-%m-%d %H:%M:00', value))` |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | overridden | `CAST(strftime('%m', value) AS INTEGER)` |  |
| month_add | datetime | scalar | overridden | `min(date(value, printf('%+d months', n)), date(value, 'start of month', printf('%+d months', n + 1), '-1 day'))` |  |
| month_begin | datetime | scalar | overridden | `date(value, 'start of month')` |  |
| month_end | datetime | scalar | overridden | `date(value, 'start of month', '+1 month', '-1 day')` |  |
| months_between | datetime | scalar | overridden | `(CASE WHEN ((CAST(strftime('%Y', end) AS INTEGER) * 12 + CAST(strftime('%m', end) AS INTEGER)) - (CAST(strftime('%Y', start) AS INTEGER) * 12 + CAST(strftime('%m', start) AS INTEGER))) > 0 AND CAST(strftime('%d', end) AS INTEGER) < CAST(strftime('%d', start) AS INTEGER) THEN ((CAST(strftime('%Y', end) AS INTEGER) * 12 + CAST(strftime('%m', end) AS INTEGER)) - (CAST(strftime('%Y', start) AS INTEGER) * 12 + CAST(strftime('%m', start) AS INTEGER))) - 1 WHEN ((CAST(strftime('%Y', end) AS INTEGER) * 12 + CAST(strftime('%m', end) AS INTEGER)) - (CAST(strftime('%Y', start) AS INTEGER) * 12 + CAST(strftime('%m', start) AS INTEGER))) < 0 AND CAST(strftime('%d', end) AS INTEGER) > CAST(strftime('%d', start) AS INTEGER) THEN ((CAST(strftime('%Y', end) AS INTEGER) * 12 + CAST(strftime('%m', end) AS INTEGER)) - (CAST(strftime('%Y', start) AS INTEGER) * 12 + CAST(strftime('%m', start) AS INTEGER))) + 1 ELSE ((CAST(strftime('%Y', end) AS INTEGER) * 12 + CAST(strftime('%m', end) AS INTEGER)) - (CAST(strftime('%Y', start) AS INTEGER) * 12 + CAST(strftime('%m', start) AS INTEGER))) END)` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | standard |  |  |
| overlay | string | scalar | unsupported | `—` |  |
| parse_date | datetime | scalar | unsupported | `—` |  |
| parse_time | datetime | scalar | unsupported | `—` |  |
| parse_timestamp | datetime | scalar | unsupported | `—` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | overridden | `instr(str, substr)` |  |
| power | math | scalar | standard |  |  |
| quarter | datetime | scalar | overridden | `((CAST(strftime('%m', value) AS INTEGER) + 2) / 3)` |  |
| quarter_begin | datetime | scalar | overridden | `date(value, 'start of month', printf('%+d months', -((CAST(strftime('%m', value) AS INTEGER) - 1) % 3)))` |  |
| quarter_end | datetime | scalar | overridden | `date(value, 'start of month', printf('%+d months', 3 - ((CAST(strftime('%m', value) AS INTEGER) - 1) % 3)), '-1 day')` |  |
| random | math | scalar | overridden | `(random() / 18446744073709551616.0 + 0.5)` |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | unsupported | `—` |  |
| regexp_like | pattern_matching | scalar | unsupported | `—` |  |
| regexp_replace | pattern_matching | scalar | unsupported | `—` |  |
| regexp_substr | pattern_matching | scalar | unsupported | `—` |  |
| repeat | string | scalar | unsupported | `—` |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | overridden | `substr(string, CASE WHEN n <= 0 THEN length(string) + 1 ELSE max(length(string) - n + 1, 1) END)` |  |
| round | math | scalar | standard |  |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | unsupported | `—` |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | overridden | `CAST(strftime('%S', value) AS INTEGER)` |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | unsupported | `—` |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | overridden | `(substr(string, 1, length(prefix)) = prefix)` |  |
| string_agg | aggregate | aggregate | standard |  |  |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS INTEGER)` |  |
| to_boolean | conversion | scalar | overridden | `(value <> 0)` |  |
| to_char | formatting | scalar | overridden | `to_char(…) — dialect-specific rendering` |  |
| to_date | conversion | scalar | overridden | `date(value)` |  |
| to_decimal | conversion | scalar | overridden | `ROUND(value, scale)` |  |
| to_double | conversion | scalar | standard |  |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | overridden | `printf('%X', number)` |  |
| to_integer | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS INTEGER)` |  |
| to_number | formatting | scalar | unsupported | `—` |  |
| to_smallint | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS INTEGER)` |  |
| to_text | conversion | scalar | standard |  |  |
| to_time | conversion | scalar | overridden | `time(value)` |  |
| to_timestamp | conversion | scalar | overridden | `datetime(value)` |  |
| to_utc | datetime | scalar | unsupported | `—` |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | standard |  |  |
| translate | string | scalar | unsupported | `—` |  |
| trim | string | scalar | standard |  |  |
| trunc | math | scalar | overridden | `trunc(value)`<br>`(CAST(value * pow(10, scale) AS INTEGER) / pow(10, scale))` |  |
| upper | string | scalar | standard |  | results differ |
| week | datetime | scalar | overridden | `((CAST(strftime('%j', date(value, '-3 days', 'weekday 4')) AS INTEGER) - 1) / 7 + 1)` |  |
| week_begin | datetime | scalar | overridden | `date(value, '-6 days', 'weekday 1')` |  |
| week_end | datetime | scalar | overridden | `date(value, 'weekday 0')` |  |
| year | datetime | scalar | overridden | `CAST(strftime('%Y', value) AS INTEGER)` |  |
| year_add | datetime | scalar | overridden | `min(date(value, printf('%+d years', n)), date(value, 'start of month', printf('%+d months', n * 12 + 1), '-1 day'))` |  |
| year_begin | datetime | scalar | overridden | `date(value, 'start of year')` |  |
| year_end | datetime | scalar | overridden | `date(value, 'start of year', '+1 year', '-1 day')` |  |
| year_month | datetime | scalar | overridden | `CAST(strftime('%Y%m', value) AS INTEGER)` |  |
| years_between | datetime | scalar | overridden | `(CASE WHEN (CAST(strftime('%Y', end) AS INTEGER) - CAST(strftime('%Y', start) AS INTEGER)) > 0 AND strftime('%m-%d', end) < strftime('%m-%d', start) THEN (CAST(strftime('%Y', end) AS INTEGER) - CAST(strftime('%Y', start) AS INTEGER)) - 1 WHEN (CAST(strftime('%Y', end) AS INTEGER) - CAST(strftime('%Y', start) AS INTEGER)) < 0 AND strftime('%m-%d', end) > strftime('%m-%d', start) THEN (CAST(strftime('%Y', end) AS INTEGER) - CAST(strftime('%Y', start) AS INTEGER)) + 1 ELSE (CAST(strftime('%Y', end) AS INTEGER) - CAST(strftime('%Y', start) AS INTEGER)) END)` |  |
