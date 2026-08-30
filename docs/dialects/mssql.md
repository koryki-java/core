---
menuTitle: "SQL Server"
parent: "FUNCTIONS"
order: 24
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: mssql

What is specific to mssql: the rendering column is filled in only where this dialect departs from the standard one. The canonical form, and the SQL every dialect generates for a sample query, are on the function pages.

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
| bit_length | string | scalar | overridden | `(DATALENGTH(string) * 8)` |  |
| calendar_distance | datetime | scalar | standard |  |  |
| case | conditional | scalar | standard |  |  |
| ceil | math | scalar | overridden | `CEILING(value)` |  |
| char_length | string | scalar | overridden | `LEN(string)` |  |
| chr | string | scalar | overridden | `CHAR(code)` |  |
| clock_now | datetime | scalar | standard |  |  |
| coalesce | conditional | scalar | standard |  |  |
| concat | string | scalar | standard |  |  |
| concat_ws | string | scalar | standard |  |  |
| cos | math | scalar | standard |  |  |
| count | aggregate | aggregate | standard |  |  |
| count_distinct | aggregate | aggregate | overridden | `COUNT(DISTINCT value)`<br>`count_distinct(value, more) — unsupported` | no OVER clause |
| date_trunc | datetime | scalar | overridden | `date_trunc(…) — dialect-specific rendering` |  |
| day | datetime | scalar | standard |  |  |
| day_add | datetime | scalar | overridden | `DATEADD(DAY, n, value)` |  |
| day_begin | datetime | scalar | overridden | `CAST(CAST(value AS DATE) AS DATETIME2)` |  |
| dayofweek | datetime | scalar | overridden | `((DATEDIFF(day, '19000101', value) % 7) + 1)` |  |
| dayofyear | datetime | scalar | overridden | `DATEPART(dayofyear, value)` |  |
| days_between | datetime | scalar | overridden | `(DATEDIFF_BIG(second, start, end) / 86400)` |  |
| dense_rank | window | window | standard |  |  |
| divide | arithmetic | scalar | standard |  |  |
| exp | math | scalar | standard |  |  |
| floor | math | scalar | standard |  |  |
| greatest | math | scalar | standard |  |  |
| hour | datetime | scalar | overridden | `DATEPART(HOUR, value)` |  |
| hour_begin | datetime | scalar | overridden | `DATEADD(HOUR, DATEDIFF(HOUR, 0, value), 0)` |  |
| initcap | string | scalar | unsupported | `—` |  |
| lag | window | window | standard |  |  |
| lead | window | window | standard |  |  |
| least | math | scalar | standard |  |  |
| left | string | scalar | standard |  |  |
| ln | math | scalar | overridden | `LOG(value)` |  |
| log | math | scalar | overridden | `LOG(value, base)` |  |
| log10 | math | scalar | standard |  |  |
| lower | string | scalar | standard |  |  |
| lpad | string | scalar | overridden | `CASE WHEN LEN(string) >= length THEN LEFT(string, length) ELSE RIGHT(REPLICATE(fill, length) + CAST(string AS VARCHAR(MAX)), length) END` |  |
| ltrim | string | scalar | standard |  |  |
| make_date | datetime | scalar | overridden | `DATEFROMPARTS(year, month, day)` |  |
| make_time | datetime | scalar | overridden | `TIMEFROMPARTS(hour, minute, second, 0, 0)` |  |
| make_timestamp | datetime | scalar | overridden | `DATETIME2FROMPARTS(year, month, day, hour, minute, second, 0, 0)` |  |
| max | aggregate | aggregate | standard |  |  |
| md5 | string | scalar | overridden | `LOWER(CONVERT(VARCHAR(32), HASHBYTES('MD5', string), 2))` |  |
| min | aggregate | aggregate | standard |  |  |
| minus | arithmetic | scalar | standard |  |  |
| minute | datetime | scalar | overridden | `DATEPART(MINUTE, value)` |  |
| minute_begin | datetime | scalar | overridden | `DATEADD(MINUTE, DATEDIFF(MINUTE, 0, value), 0)` |  |
| mod | math | scalar | overridden | `((dividend) % NULLIF((divisor), 0))` |  |
| month | datetime | scalar | standard |  |  |
| month_add | datetime | scalar | overridden | `DATEADD(MONTH, n, value)` |  |
| month_begin | datetime | scalar | overridden | `DATEADD(MONTH, DATEDIFF(MONTH, 0, value), 0)` |  |
| month_end | datetime | scalar | overridden | `EOMONTH(value)` |  |
| months_between | datetime | scalar | overridden | `(DATEDIFF(month, start, end) - CASE WHEN DAY(end) < DAY(start) THEN 1 ELSE 0 END)` |  |
| multiply | arithmetic | scalar | standard |  |  |
| negate | arithmetic | scalar | standard |  |  |
| now | datetime | scalar | standard |  |  |
| ntile | window | window | standard |  |  |
| nullif | conditional | scalar | standard |  |  |
| octet_length | string | scalar | overridden | `DATALENGTH(string)` |  |
| overlay | string | scalar | overridden | `STUFF(string, start, length, replacement)` |  |
| parse_date | datetime | scalar | unsupported | `—` |  |
| parse_time | datetime | scalar | unsupported | `—` |  |
| parse_timestamp | datetime | scalar | unsupported | `—` |  |
| pi | math | scalar | standard |  |  |
| position | string | scalar | overridden | `CHARINDEX(substr, str)` |  |
| power | math | scalar | overridden | `POWER(CAST(base AS FLOAT), exponent)` |  |
| quarter | datetime | scalar | overridden | `DATEPART(quarter, value)` |  |
| quarter_begin | datetime | scalar | overridden | `DATEADD(QUARTER, DATEDIFF(QUARTER, 0, value), 0)` |  |
| quarter_end | datetime | scalar | overridden | `EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, value), 0), 2)` |  |
| random | math | scalar | overridden | `RAND(CHECKSUM(NEWID()))` |  |
| rank | window | window | standard |  |  |
| regexp_count | pattern_matching | scalar | unsupported | `—` |  |
| regexp_like | pattern_matching | scalar | unsupported | `—` |  |
| regexp_replace | pattern_matching | scalar | unsupported | `—` |  |
| regexp_substr | pattern_matching | scalar | unsupported | `—` |  |
| repeat | string | scalar | overridden | `REPLICATE(string, number)` |  |
| replace | string | scalar | standard |  |  |
| reverse | string | scalar | standard |  |  |
| right | string | scalar | standard |  |  |
| round | math | scalar | overridden | `round(value, scale)`<br>`ROUND(value, 0)` |  |
| row_number | window | window | standard |  |  |
| rpad | string | scalar | overridden | `LEFT(CAST(string AS VARCHAR(MAX)) + REPLICATE(fill, length), length)` |  |
| rtrim | string | scalar | standard |  |  |
| second | datetime | scalar | overridden | `DATEPART(SECOND, value)` |  |
| sign | math | scalar | standard |  |  |
| sin | math | scalar | standard |  |  |
| split_part | string | scalar | overridden | `(SELECT value FROM STRING_SPLIT(string, delimiter, 1) WHERE ordinal = n)` |  |
| sqrt | math | scalar | standard |  |  |
| starts_with | string | scalar | overridden | `starts_with(…) — dialect-specific rendering` |  |
| string_agg | aggregate | aggregate | overridden | `string_agg(value, separator)`<br>`STRING_AGG(value, separator) WITHIN GROUP (ORDER BY order_by)` | no OVER clause |
| substring | string | scalar | overridden | `substring(…) — dialect-specific rendering` |  |
| sum | aggregate | aggregate | standard |  |  |
| tan | math | scalar | standard |  |  |
| to_bigint | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS BIGINT)` |  |
| to_boolean | conversion | scalar | overridden | `CAST(value AS BIT)` |  |
| to_char | formatting | scalar | overridden | `to_char(…) — dialect-specific rendering` |  |
| to_date | conversion | scalar | standard |  |  |
| to_decimal | conversion | scalar | standard |  |  |
| to_double | conversion | scalar | overridden | `CAST(value AS FLOAT)` |  |
| to_float | conversion | scalar | standard |  |  |
| to_hex | string | scalar | overridden | `UPPER(CASE WHEN number = 0 THEN '0' ELSE SUBSTRING(CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST(number AS BIGINT)), 2), PATINDEX('%[^0]%', CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST(number AS BIGINT)), 2)), 16) END)` |  |
| to_integer | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS INT)` |  |
| to_number | formatting | scalar | unsupported | `—` |  |
| to_smallint | conversion | scalar | overridden | `CAST(ROUND(value, 0) AS SMALLINT)` |  |
| to_text | conversion | scalar | overridden | `CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))`<br>`CAST(value AS VARCHAR(MAX))` |  |
| to_time | conversion | scalar | standard |  |  |
| to_timestamp | conversion | scalar | overridden | `CAST(value AS DATETIME2)` |  |
| to_utc | datetime | scalar | standard |  |  |
| to_varchar | conversion | scalar | standard |  |  |
| today | datetime | scalar | overridden | `CAST(GETDATE() AS DATE)` |  |
| translate | string | scalar | standard |  |  |
| trim | string | scalar | overridden | `trim(…) — dialect-specific rendering` |  |
| trunc | math | scalar | overridden | `ROUND(value, 0, 1)`<br>`ROUND(value, scale, 1)` |  |
| upper | string | scalar | standard |  |  |
| week | datetime | scalar | overridden | `DATEPART(iso_week, value)` |  |
| week_begin | datetime | scalar | overridden | `DATEADD(day, -(DATEDIFF(day, 0, value) % 7), CAST(value AS DATE))` |  |
| week_end | datetime | scalar | overridden | `DATEADD(day, 6 - (DATEDIFF(day, 0, value) % 7), CAST(value AS DATE))` |  |
| year | datetime | scalar | standard |  |  |
| year_add | datetime | scalar | overridden | `DATEADD(YEAR, n, value)` |  |
| year_begin | datetime | scalar | overridden | `DATEADD(YEAR, DATEDIFF(YEAR, 0, value), 0)` |  |
| year_end | datetime | scalar | overridden | `DATEFROMPARTS(YEAR(value), 12, 31)` |  |
| year_month | datetime | scalar | standard |  |  |
| years_between | datetime | scalar | overridden | `(DATEDIFF(YEAR, start, end) - CASE WHEN DATEADD(YEAR, DATEDIFF(YEAR, start, end), start) > end THEN 1 ELSE 0 END)` |  |
