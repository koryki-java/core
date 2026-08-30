---
menuTitle: "Oracle"
parent: "FUNCTIONS"
order: 22
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: oracle

What is specific to oracle: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| DISTINCT | comparison | scalar | overridden | `DECODE(left, right, 0, 1) = 1` |  |
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
| bit_length | string | scalar | overridden | `LENGTHB(string) * 8` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | standard |  |  |
| char_length | string | scalar | overridden | `LENGTH(string)` |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | standard |  |  |
| concat_ws | string | scalar | unsupported | `—` |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | overridden | `COUNT(DISTINCT value)`<br>`count_distinct(value, more) — unsupported` |  |
| date_trunc | datetime | scalar | overridden | `date_trunc(…) — dialect-specific rendering` |  |
| day | datetime | scalar | overridden | `EXTRACT(DAY FROM value)` |  |
| day_add | datetime | scalar | overridden | `(value + NUMTODSINTERVAL(n, 'DAY'))` |  |
| day_begin | datetime | scalar | overridden | `TRUNC(CAST(value AS DATE))` |  |
| dayofweek | datetime | scalar | overridden | `(TRUNC(value) - TRUNC(value, 'IW') + 1)` |  |
| dayofyear | datetime | scalar | overridden | `TO_NUMBER(TO_CHAR(value, 'DDD'))` |  |
| days_between | datetime | scalar | overridden | `TRUNC(CAST(end AS DATE) - CAST(start AS DATE))` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  |  |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | overridden | `EXTRACT(HOUR FROM value)` |  |
| hour_begin | datetime | scalar | overridden | `TRUNC(CAST(value AS DATE), 'HH')` |  |
| initcap | string | scalar | standard |  |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | overridden | `SUBSTR(string, 1, n)` |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | overridden | `LOG(10, value)` |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | standard |  |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `TO_DATE(year \|\| '-' \|\| month \|\| '-' \|\| day, 'YYYY-MM-DD')` |  |
| make_time | datetime | scalar | overridden | `LPAD(hour, 2, '0') \|\| ':' \|\| LPAD(minute, 2, '0') \|\| ':' \|\| LPAD(second, 2, '0')` |  |
| make_timestamp | datetime | scalar | overridden | `TO_TIMESTAMP(year \|\| '-' \|\| month \|\| '-' \|\| day \|\| ' ' \|\| hour \|\| ':' \|\| minute \|\| ':' \|\| second, 'YYYY-MM-DD HH24:MI:SS')` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | unsupported | `—` |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | overridden | `EXTRACT(MINUTE FROM value)` |  |
| minute_begin | datetime | scalar | overridden | `TRUNC(CAST(value AS DATE), 'MI')` |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | overridden | `EXTRACT(MONTH FROM value)` |  |
| month_add | datetime | scalar | overridden | `(ADD_MONTHS(value, n) - GREATEST(EXTRACT(DAY FROM ADD_MONTHS(value, n)) - EXTRACT(DAY FROM value), 0))` |  |
| month_begin | datetime | scalar | overridden | `TRUNC(value, 'MM')` |  |
| month_end | datetime | scalar | overridden | `TRUNC(LAST_DAY(value))` |  |
| months_between | datetime | scalar | overridden | `TRUNC(MONTHS_BETWEEN(end, start))` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | overridden | `LENGTHB(string)` |  |
| overlay | string | scalar | overridden | `overlay(…) — dialect-specific rendering` |  |
| parse_date | datetime | scalar | overridden | `TO_DATE(value, format)` |  |
| parse_time | datetime | scalar | overridden | `TO_DATE(value, format)` |  |
| parse_timestamp | datetime | scalar | overridden | `TO_TIMESTAMP(value, format)` |  |
| pi | math | scalar | overridden | `ACOS(-1)` |  |
| position | string | scalar | overridden | `INSTR(str, substr)` |  |
| power | math | scalar | standard |  | results differ |
| quarter | datetime | scalar | overridden | `TO_NUMBER(TO_CHAR(value, 'Q'))` |  |
| quarter_begin | datetime | scalar | overridden | `TRUNC(value, 'Q')` |  |
| quarter_end | datetime | scalar | overridden | `LAST_DAY(ADD_MONTHS(TRUNC(value, 'Q'), 2))` |  |
| random | math | scalar | overridden | `DBMS_RANDOM.VALUE` |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | standard |  |  |
| regexp_like | pattern_matching | scalar | standard |  |  |
| regexp_replace | pattern_matching | scalar | standard |  |  |
| regexp_substr | pattern_matching | scalar | standard |  |  |
| repeat | string | scalar | overridden | `RPAD(string, number * LENGTH(string), string)` |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | unsupported | `—` |  |
| right | string | scalar | overridden | `SUBSTR(string, CASE WHEN n <= 0 THEN LENGTH(string) + 1 ELSE GREATEST(LENGTH(string) - n + 1, 1) END)` |  |
| round | math | scalar | standard |  |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | standard |  |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | overridden | `TRUNC(EXTRACT(SECOND FROM value))` |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | unsupported | `—` |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | overridden | `(SUBSTR(string, 1, LENGTH(prefix)) = prefix)` |  |
| string_agg | aggregate | aggregate | overridden | `LISTAGG(value, separator) WITHIN GROUP (ORDER BY value)`<br>`LISTAGG(value, separator) WITHIN GROUP (ORDER BY order_by)` | no OVER clause |
| substring | string | scalar | overridden | `SUBSTR(string, start, length)` |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | overridden | `CAST(value AS NUMBER(19))` |  |
| to_boolean | conversion | scalar | standard |  |  |
| to_char | formatting | scalar | standard |  |  |
| to_date | conversion | scalar | overridden | `to_date(…) — dialect-specific rendering`<br>`to_date(…) — dialect-specific rendering`<br>`TO_DATE(year \|\| '-' \|\| month \|\| '-' \|\| day, 'YYYY-MM-DD')` |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | overridden | `CAST(value AS BINARY_DOUBLE)` |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | overridden | `TO_CHAR(number, 'FMXXXXXXXXXXXXXXXX')` |  |
| to_integer | conversion | scalar | standard |  |  |
| to_interval | other | scalar | native | `to_interval(…) — dialect-specific rendering`<br>`to_interval(…) — dialect-specific rendering` |  |
| to_number | formatting | scalar | standard |  |  |
| to_smallint | conversion | scalar | standard |  |  |
| to_text | conversion | scalar | overridden | `CAST(value AS VARCHAR(4000))`<br>`CAST(value AS VARCHAR(4000))`<br>`CAST(value AS VARCHAR(4000))`<br>`CAST(value AS VARCHAR(4000))`<br>`CAST(value AS VARCHAR(4000))`<br>`CAST(value AS VARCHAR(4000))`<br>`TO_CHAR(value, 'TM9', 'NLS_NUMERIC_CHARACTERS = ''.,''')`<br>`TO_CHAR(value, 'TM9', 'NLS_NUMERIC_CHARACTERS = ''.,''')`<br>`TO_CHAR(value, 'TM9', 'NLS_NUMERIC_CHARACTERS = ''.,''')`<br>`TO_CHAR(value, 'YYYY-MM-DD')`<br>`TO_CHAR(value, 'HH24:MI:SS')`<br>`TO_CHAR(value, 'YYYY-MM-DD HH24:MI:SS')` |  |
| to_time | conversion | scalar | overridden | `to_time(…) — dialect-specific rendering`<br>`to_time(…) — dialect-specific rendering`<br>`LPAD(hour, 2, '0') \|\| ':' \|\| LPAD(minute, 2, '0') \|\| ':' \|\| LPAD(second, 2, '0')` |  |
| to_timestamp | conversion | scalar | overridden | `CAST(value AS TIMESTAMP)`<br>`TO_TIMESTAMP(value, format)`<br>`FROM_TZ(TO_TIMESTAMP(value, format), 'UTC') AT TIME ZONE tz`<br>`TO_TIMESTAMP(year \|\| '-' \|\| month \|\| '-' \|\| day \|\| ' ' \|\| hour \|\| ':' \|\| minute \|\| ':' \|\| second, 'YYYY-MM-DD HH24:MI:SS')`<br>`FROM_TZ(TO_TIMESTAMP(year \|\| '-' \|\| month \|\| '-' \|\| day \|\| ' ' \|\| hour \|\| ':' \|\| minute \|\| ':' \|\| second, 'YYYY-MM-DD HH24:MI:SS'), 'UTC') AT TIME ZONE tz` |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | standard |  |  |
| translate | string | scalar | standard |  |  |
| trim | string | scalar | overridden | `trim(…) — dialect-specific rendering` |  |
| trunc | math | scalar | standard |  |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | overridden | `TO_NUMBER(TO_CHAR(value, 'IW'))` |  |
| week_begin | datetime | scalar | overridden | `TRUNC(value, 'IW')` |  |
| week_end | datetime | scalar | overridden | `(TRUNC(value, 'IW') + 6)` |  |
| year | datetime | scalar | overridden | `EXTRACT(YEAR FROM value)` |  |
| year_add | datetime | scalar | overridden | `(ADD_MONTHS(value, n * 12) - GREATEST(EXTRACT(DAY FROM ADD_MONTHS(value, n * 12)) - EXTRACT(DAY FROM value), 0))` |  |
| year_begin | datetime | scalar | overridden | `TRUNC(value, 'YYYY')` |  |
| year_end | datetime | scalar | overridden | `LAST_DAY(ADD_MONTHS(TRUNC(value, 'YYYY'), 11))` |  |
| year_month | datetime | scalar | overridden | `TO_NUMBER(TO_CHAR(value, 'YYYYMM'))` |  |
| years_between | datetime | scalar | overridden | `TRUNC(MONTHS_BETWEEN(end, start) / 12)` |  |
