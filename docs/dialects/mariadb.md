---
menuTitle: "MariaDB"
parent: "FUNCTIONS"
order: 26
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: mariadb

What is specific to mariadb: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| DISTINCT | comparison | scalar | overridden | `NOT (left <=> right)` |  |
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
| char_length | string | scalar | overridden | `CHAR_LENGTH(string)` |  |
| chr | string | scalar | standard |  |  |
| clock_now | datetime | scalar | overridden | `SYSDATE()` |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | overridden | `concat_ws('', value)` |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | overridden | `COUNT(DISTINCT value)`<br>`COUNT(DISTINCT value, more)` | no OVER clause |
| date_trunc | datetime | scalar | overridden | `date_trunc(…) — dialect-specific rendering` |  |
| day | datetime | scalar | standard |  |  |
| day_add | datetime | scalar | standard |  |  |
| day_begin | datetime | scalar | overridden | `CAST(DATE(value) AS DATETIME)` |  |
| dayofweek | datetime | scalar | overridden | `(WEEKDAY(value) + 1)` |  |
| dayofyear | datetime | scalar | standard |  |  |
| days_between | datetime | scalar | overridden | `TIMESTAMPDIFF(DAY, start, end)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  |  |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | standard |  |  |
| hour_begin | datetime | scalar | overridden | `CAST(DATE_FORMAT(value, '%Y-%m-%d %H:00:00') AS DATETIME)` |  |
| initcap | string | scalar | unsupported | `—` |  |
| lag | window | window | overridden | `lag(value, offset)`<br>`lag(value, offset, default) — unsupported` |  |
| lead | window | window | overridden | `lead(value, offset)`<br>`lead(value, offset, default) — unsupported` |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | standard |  |  |
| ln | math | scalar | standard |  |  |
| log | math | scalar | standard |  |  |
| log10 | math | scalar | standard |  |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | standard |  |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `STR_TO_DATE(CONCAT(year, '-', month, '-', day), '%Y-%m-%d')` |  |
| make_time | datetime | scalar | overridden | `MAKETIME(hour, minute, second)` |  |
| make_timestamp | datetime | scalar | overridden | `STR_TO_DATE(CONCAT(year, '-', month, '-', day, ' ', hour, ':', minute, ':', second), '%Y-%m-%d %H:%i:%s')` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | standard |  |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | standard |  |  |
| minute_begin | datetime | scalar | overridden | `CAST(DATE_FORMAT(value, '%Y-%m-%d %H:%i:00') AS DATETIME)` |  |
| mod | math | scalar | standard |  |  |
| month | datetime | scalar | standard |  |  |
| month_add | datetime | scalar | standard |  |  |
| month_begin | datetime | scalar | overridden | `(MAKEDATE(YEAR(value), 1) + INTERVAL (MONTH(value) - 1) MONTH)` |  |
| month_end | datetime | scalar | overridden | `LAST_DAY(value)` |  |
| months_between | datetime | scalar | overridden | `TIMESTAMPDIFF(MONTH, start, end)` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | standard |  |  |
| overlay | string | scalar | overridden | `INSERT(string, start, length, replacement)` |  |
| parse_date | datetime | scalar | overridden | `parse_date(…) — dialect-specific rendering` |  |
| parse_time | datetime | scalar | overridden | `parse_time(…) — dialect-specific rendering` |  |
| parse_timestamp | datetime | scalar | overridden | `parse_timestamp(…) — dialect-specific rendering` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | standard |  |  |
| power | math | scalar | standard |  |  |
| quarter | datetime | scalar | standard |  |  |
| quarter_begin | datetime | scalar | overridden | `(MAKEDATE(YEAR(value), 1) + INTERVAL (QUARTER(value) - 1) * 3 MONTH)` |  |
| quarter_end | datetime | scalar | overridden | `LAST_DAY(MAKEDATE(YEAR(value), 1) + INTERVAL (QUARTER(value) * 3 - 1) MONTH)` |  |
| random | math | scalar | overridden | `RAND()` |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | unsupported | `—` |  |
| regexp_like | pattern_matching | scalar | overridden | `(string REGEXP pattern)` |  |
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
| split_part | string | scalar | overridden | `SUBSTRING_INDEX(SUBSTRING_INDEX(string, delimiter, n), delimiter, -1)` |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | overridden | `(LEFT(string, CHAR_LENGTH(prefix)) = prefix)` |  |
| string_agg | aggregate | aggregate | overridden | `GROUP_CONCAT(value SEPARATOR separator)`<br>`GROUP_CONCAT(value ORDER BY order_by SEPARATOR separator)` | no OVER clause |
| substring | string | scalar | standard |  |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | overridden | `CAST(value AS SIGNED)` |  |
| to_boolean | conversion | scalar | overridden | `(value <> 0)` |  |
| to_char | formatting | scalar | overridden | `to_char(…) — dialect-specific rendering` |  |
| to_date | conversion | scalar | standard |  |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | standard |  |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | overridden | `HEX(number)` |  |
| to_integer | conversion | scalar | standard |  |  |
| to_number | formatting | scalar | unsupported | `—` |  |
| to_smallint | conversion | scalar | overridden | `CAST(value AS SIGNED)` |  |
| to_text | conversion | scalar | overridden | `CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)`<br>`CAST(value AS CHAR)` |  |
| to_time | conversion | scalar | standard |  |  |
| to_timestamp | conversion | scalar | overridden | `CAST(value AS DATETIME)` |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | standard |  |  |
| translate | string | scalar | unsupported | `—` |  |
| trim | string | scalar | overridden | `TRIM(string)`<br>`trim(string, characters) — unsupported` |  |
| trunc | math | scalar | overridden | `TRUNCATE(value, 0)`<br>`TRUNCATE(value, scale)` |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | overridden | `WEEKOFYEAR(value)` |  |
| week_begin | datetime | scalar | overridden | `(DATE(value) - INTERVAL WEEKDAY(value) DAY)` |  |
| week_end | datetime | scalar | overridden | `(DATE(value) - INTERVAL WEEKDAY(value) DAY + INTERVAL 6 DAY)` |  |
| year | datetime | scalar | standard |  |  |
| year_add | datetime | scalar | standard |  |  |
| year_begin | datetime | scalar | overridden | `MAKEDATE(YEAR(value), 1)` |  |
| year_end | datetime | scalar | overridden | `LAST_DAY(MAKEDATE(YEAR(value), 1) + INTERVAL 11 MONTH)` |  |
| year_month | datetime | scalar | standard |  |  |
| years_between | datetime | scalar | overridden | `TIMESTAMPDIFF(YEAR, start, end)` |  |
