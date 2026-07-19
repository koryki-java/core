---
menuTitle: "DuckDB"
parent: "FUNCTIONS"
order: 21
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Function support: duckdb

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
| bit_and | other | scalar | native | `bit_and(…)` |
| bit_count | other | scalar | native | `bit_count(…)` |
| bit_length | string | scalar | standard | `bit_length(string)` |
| bit_or | other | scalar | native | `bit_or(…)` |
| bit_xor | other | scalar | native | `bit_xor(…)` |
| btrim | other | scalar | overridden | `btrim(…)` |
| calendar_distance | datetime | scalar | standard | `calendar_distance(…) — dialect-specific rendering` |
| case | conditional | scalar | standard | `case(…) — dialect-specific rendering` |
| ceil | math | scalar | standard | `ceil(value)` |
| ceiling | math | scalar | standard | `ceiling(value)` |
| char_length | string | scalar | standard | `char_length(string)` |
| character_length | string | scalar | standard | `character_length(string)` |
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
| days_between | datetime | scalar | standard | `date_diff('day', start, end)` |
| divide | arithmetic | scalar | standard | `divide(…) — dialect-specific rendering` |
| floor | math | scalar | standard | `floor(value)` |
| group_concat | other | scalar | native | `group_concat(…)` |
| hour | datetime | scalar | standard | `hour(value)` |
| initcap | string | scalar | standard | `initcap(string)` |
| instr | other | scalar | native | `instr(…)` |
| left | string | scalar | standard | `left(string, n)` |
| length | string | scalar | standard | `length(string)` |
| list | other | scalar | native | `[value]` |
| lower | string | scalar | standard | `lower(string)` |
| lpad | string | scalar | standard | `lpad(string, length, fill)` |
| ltrim | string | scalar | standard | `ltrim(string, characters)` |
| make_date | datetime | scalar | standard | `make_date(year, month, day)` |
| make_time | datetime | scalar | standard | `make_time(hour, minute, second)` |
| make_timestamp | datetime | scalar | standard | `make_timestamp(year, month, day, hour, minute, second)` |
| max | aggregate | aggregate | standard | `max(value)` |
| md5 | other | scalar | overridden | `md5(…)` |
| min | aggregate | aggregate | standard | `min(value)` |
| minus | arithmetic | scalar | standard | `minus(…) — dialect-specific rendering` |
| minute | datetime | scalar | standard | `minute(value)` |
| mod | math | scalar | standard | `mod(dividend, divisor)` |
| month | datetime | scalar | standard | `month(value)` |
| month_add | datetime | scalar | standard | `(value + INTERVAL (n) MONTH)` |
| month_begin | datetime | scalar | standard | `date_trunc('month', value)` |
| month_end | datetime | scalar | standard | `last_day(value)` |
| months_between | datetime | scalar | standard | `(CAST(EXTRACT(YEAR FROM age(end, start)) * 12 + EXTRACT(MONTH FROM age(end, start)) AS INTEGER))` |
| multiply | arithmetic | scalar | standard | `multiply(…) — dialect-specific rendering` |
| negate | arithmetic | scalar | standard | `negate(…) — dialect-specific rendering` |
| now | datetime | scalar | standard | `CURRENT_TIMESTAMP` |
| octet_length | string | scalar | standard | `octet_length(string)` |
| overlay | string | scalar | standard | `overlay(string, replacement, start, length)` |
| parse_date | other | scalar | native | `parse_date(…) — dialect-specific rendering` |
| parse_time | other | scalar | native | `parse_time(…) — dialect-specific rendering` |
| parse_timestamp | other | scalar | native | `parse_timestamp(…) — dialect-specific rendering` |
| pi | math | scalar | standard | `pi()` |
| position | string | scalar | standard | `POSITION(substr IN str)` |
| power | math | scalar | standard | `power(base, exponent)` |
| printf | other | scalar | native | `printf(…)` |
| quarter_begin | datetime | scalar | standard | `date_trunc('quarter', value)` |
| quarter_end | datetime | scalar | standard | `last_day(date_trunc('quarter', value) + INTERVAL 2 MONTH)` |
| rand | other | scalar | native | `random()` |
| random | math | scalar | standard | `random()` |
| regexp_count | pattern_matching | scalar | standard | `regexp_count(string, pattern, start)` |
| regexp_like | pattern_matching | scalar | overridden | `regexp_matches(string, pattern, flags)` |
| regexp_replace | pattern_matching | scalar | standard | `regexp_replace(string, pattern, replacement, flags)` |
| regexp_substr | pattern_matching | scalar | overridden | `regexp_extract(string, pattern)` |
| repeat | string | scalar | standard | `repeat(string, number)` |
| replace | string | scalar | standard | `replace(string, from, to)` |
| reverse | string | scalar | standard | `reverse(string)` |
| right | string | scalar | standard | `right(string, n)` |
| round | math | scalar | standard | `round(value, scale)` |
| rpad | string | scalar | standard | `rpad(string, length, fill)` |
| rtrim | string | scalar | standard | `rtrim(string, characters)` |
| second | datetime | scalar | standard | `second(value)` |
| sha256 | other | scalar | native | `sha256(…)` |
| split | other | scalar | native | `split(…)` |
| split_part | other | scalar | overridden | `split_part(…)` |
| sqrt | math | scalar | standard | `sqrt(value)` |
| starts_with | string | scalar | standard | `starts_with(string, prefix)` |
| string_agg | aggregate | aggregate | standard | `string_agg(value, separator)` |
| strpos | other | scalar | overridden | `strpos(…)` |
| substr | string | scalar | standard | `substr(string, start, length)` |
| substring | string | scalar | standard | `substring(string, start, length)` |
| sum | aggregate | aggregate | standard | `sum(value)` |
| time | datetime | scalar | standard | `time(value)` |
| timestamp | datetime | scalar | standard | `timestamp(value)` |
| to_bigint | conversion | scalar | standard | `CAST(value AS BIGINT)` |
| to_boolean | conversion | scalar | standard | `CAST(value AS BOOLEAN)` |
| to_char | formatting | scalar | standard | `to_char(value, format)` |
| to_date | other | scalar | overridden | `CAST(value AS DATE)`<br>`to_date(…) — dialect-specific rendering`<br>`MAKE_DATE(year, month, day)` |
| to_decimal | conversion | scalar | standard | `CAST(value AS DECIMAL(precision, scale))` |
| to_double | conversion | scalar | standard | `CAST(value AS DOUBLE)` |
| to_float | conversion | scalar | standard | `CAST(value AS FLOAT)` |
| to_hex | other | scalar | overridden | `to_hex(…)` |
| to_integer | conversion | scalar | standard | `CAST(value AS INTEGER)` |
| to_interval | other | scalar | native | `to_interval(…) — dialect-specific rendering`<br>`to_years(years) + to_months(months) + to_days(days) + to_hours(hours) + to_minutes(minutes) + to_seconds(seconds)` |
| to_number | formatting | scalar | standard | `to_number(value, format)` |
| to_smallint | conversion | scalar | standard | `CAST(value AS SMALLINT)` |
| to_text | conversion | scalar | standard | `CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)`<br>`CAST(value AS TEXT)` |
| to_time | other | scalar | overridden | `CAST(value AS TIME)`<br>`to_time(…) — dialect-specific rendering`<br>`MAKE_TIME(hour, minute, second::DOUBLE)` |
| to_timestamp | other | scalar | overridden | `CAST(value AS TIMESTAMP)`<br>`to_timestamp(…) — dialect-specific rendering`<br>`to_timestamp(…) — dialect-specific rendering`<br>`MAKE_TIMESTAMP(year, month, day, hour, minute, second)`<br>`timezone(tz, MAKE_TIMESTAMP(year, month, day, hour, minute, second)::TIMESTAMPTZ)` |
| to_utc | other | scalar | standard | `to_utc(…) — dialect-specific rendering` |
| to_varchar | conversion | scalar | standard | `CAST(value AS VARCHAR(length))` |
| today | datetime | scalar | standard | `CURRENT_DATE` |
| translate | string | scalar | standard | `translate(string, from, to)` |
| trim | string | scalar | standard | `trim(string, characters)` |
| truncate | other | scalar | native | `trunc(value)` |
| upper | string | scalar | standard | `upper(string)` |
| year | datetime | scalar | standard | `year(value)` |
| year_add | datetime | scalar | standard | `(value + INTERVAL (n) YEAR)` |
| year_begin | datetime | scalar | standard | `date_trunc('year', value)` |
| year_end | datetime | scalar | standard | `last_day(date_trunc('year', value) + INTERVAL 11 MONTH)` |
| years_between | datetime | scalar | standard | `CAST(EXTRACT(YEAR FROM age(end, start)) AS INTEGER)` |
