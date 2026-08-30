---
menuTitle: "String Functions"
parent: "FUNCTIONS"
order: 5
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# String Functions

String functions operate on text values (`TEXT` / `VARCHAR`). Character positions and the `start` of
a substring are **1-based**, and length is measured in **characters** (`char_length`, `length`) — use
`octet_length` or `bit_length` for byte- and bit-oriented sizes. The catalog covers length, case
conversion, trimming and padding, substring extraction and search, concatenation, replacement, and a
few encoding helpers (`ascii`, `chr`, `to_hex`, `md5`).

## Where the databases disagree

Text is the least uniform corner of SQL, so this page is franker than the others about it. Two kinds
of difference show up, and they are marked differently.

**Some functions do not exist everywhere.** Each one says so — look for *Unsupported* under the
function. `initcap`, `md5`, `translate`, `split_part`, `concat_ws`, `reverse`, `to_hex` and the
padding family (`lpad`, `rpad`, `repeat`, `overlay`) are each missing on at least one engine, and a
query using one is rejected there rather than quietly doing something else.

`reverse` is worth singling out. It is refused on **Oracle** not because Oracle lacks it, but because
Oracle's reverses *bytes* rather than characters: `reverse('Königlich')` comes back as corrupted text
instead of `hcilginöK`. Being told the function is unavailable is better than being handed mojibake.

**Case conversion is ASCII-only on SQLite.** `upper` and `lower` there fold `a`–`z` and leave
everything else untouched, so `upper('Ölsson-äöü')` is `ÖLSSON-äöü`. Core SQLite has no Unicode
casing at all. On every other supported engine, case conversion is Unicode-aware. If you sort or
group on an upper-cased key and SQLite is in scope, that difference is real.

**Comparison and sorting follow the database's collation**, not KQL — whether `'a' = 'A'`, and where
accented letters sort, is decided by the column's collation. That is a schema decision, and
deliberately not one this layer overrides.

## char_length

`char_length(string: TEXT)` → INTEGER

Number of characters in the string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to measure |

Sample query:

```kql
// char_length: length of the company name in characters.
FIND customers c
FETCH char_length(c.company_name) name_len
```

### Generated SQL

**duckdb · postgresql**

```sql
-- char_length: length of the company name in characters.
SELECT
  char_length(c.company_name) AS name_len
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · snowflake | `LENGTH(c.company_name) AS name_len` |
| sqlite · trino | `length(c.company_name) AS name_len` |
| mariadb | `CHAR_LENGTH(c.company_name) AS name_len` |
| mssql | `LEN(c.company_name) AS name_len` |


## octet_length

`octet_length(string: TEXT)` → INTEGER

Number of bytes in the string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to measure |

Sample query:

```kql
// octet_length: length of the company name in bytes.
FIND customers c
FETCH octet_length(c.company_name) name_bytes
```

### Generated SQL

**snowflake · postgresql · mariadb · sqlite**

```sql
-- octet_length: length of the company name in bytes.
SELECT
  octet_length(c.company_name) AS name_bytes
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| duckdb | `octet_length(encode(c.company_name)) AS name_bytes` |
| mssql | `DATALENGTH(c.company_name) AS name_bytes` |
| oracle | `LENGTHB(c.company_name) AS name_bytes` |
| trino | `length(to_utf8(c.company_name)) AS name_bytes` |


## bit_length

`bit_length(string: TEXT)` → INTEGER

Number of bits in the string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to measure |

Sample query:

```kql
// bit_length: length of the company name in bits.
FIND customers c
FETCH bit_length(c.company_name) name_bits
```

### Generated SQL

**duckdb · snowflake · postgresql · mariadb**

```sql
-- bit_length: length of the company name in bits.
SELECT
  bit_length(c.company_name) AS name_bits
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `(DATALENGTH(c.company_name) * 8) AS name_bits` |
| oracle | `LENGTHB(c.company_name) * 8 AS name_bits` |
| sqlite | `length(CAST(c.company_name AS BLOB)) * 8 AS name_bits` |
| trino | `length(to_utf8(c.company_name)) * 8 AS name_bits` |


## upper

`upper(string: TEXT)` → TEXT

Converts the string to upper case.

On SQLite this folds ASCII only, so accented letters are returned unchanged; every other supported engine is Unicode-aware.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to convert |

Sample query:

```kql
// upper: company name in upper case.
FIND customers c
FETCH upper(c.company_name) shout
```

### Generated SQL

**all dialects**

```sql
-- upper: company name in upper case.
SELECT
  upper(c.company_name) AS shout
FROM
 customers c
```

> Results differ from the other dialects on **sqlite**.


## lower

`lower(string: TEXT)` → TEXT

Converts the string to lower case.

On SQLite this folds ASCII only, so accented letters are returned unchanged; every other supported engine is Unicode-aware.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to convert |

Sample query:

```kql
// lower: company name in lower case.
FIND customers c
FETCH lower(upper(c.company_name)) quiet
```

### Generated SQL

**all dialects**

```sql
-- lower: company name in lower case.
SELECT
  lower(upper(c.company_name)) AS quiet
FROM
 customers c
```


## initcap

`initcap(string: TEXT)` → TEXT

Upper-cases the first letter of each word **and lower-cases the rest** — `initcap('hELLO wORLD')` is `Hello World`, not `hEllo wOrld`. A word is a run of letters and digits; anything else separates two words.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to capitalize |

Sample query:

```kql
// initcap: title-case the contact name.
FIND customers c
FETCH initcap(c.contact_name) titled
```

### Generated SQL

**oracle · snowflake · postgresql**

```sql
-- initcap: title-case the contact name.
SELECT
  initcap(c.contact_name) AS titled
FROM
 customers c
```

Unsupported: **duckdb**, **mariadb**, **mssql**, **sqlite**, **trino**


## trim

`trim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from both ends.

*characters* is a **set of characters**, not a substring: `trim('abhelloba', 'ab')` gives `hello`, because it strips any `a` and any `b` from either end until something else appears.

**MariaDB has no set-based trim** and cannot express this form — a call with *characters* is rejected there rather than answered wrongly. Its `TRIM(BOTH x FROM s)` removes a whole substring, which is a different question: it would leave `helloba`. Without *characters* the function works everywhere.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to trim |
| characters | TEXT *(optional)* | the characters to strip; spaces if omitted |

Sample query:

```kql
// trim: strip surrounding whitespace.
FIND customers c
FETCH trim(c.company_name) trimmed
```

### Generated SQL

**duckdb · snowflake · postgresql · sqlite · trino**

```sql
-- trim: strip surrounding whitespace.
SELECT
  trim(c.company_name) AS trimmed
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle · mssql · mariadb | `TRIM(c.company_name) AS trimmed` |


## ltrim

`ltrim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from the start.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to trim |
| characters | TEXT *(optional)* | the characters to strip; spaces if omitted |

Sample query:

```kql
// ltrim: strip leading whitespace.
FIND customers c
FETCH ltrim(c.postal_code) left_trimmed
```

### Generated SQL

**all dialects**

```sql
-- ltrim: strip leading whitespace.
SELECT
  ltrim(c.postal_code) AS left_trimmed
FROM
 customers c
```


## rtrim

`rtrim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from the end.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to trim |
| characters | TEXT *(optional)* | the characters to strip; spaces if omitted |

Sample query:

```kql
// rtrim: strip trailing whitespace.
FIND customers c
FETCH rtrim(c.postal_code) right_trimmed
```

### Generated SQL

**all dialects**

```sql
-- rtrim: strip trailing whitespace.
SELECT
  rtrim(c.postal_code) AS right_trimmed
FROM
 customers c
```


## substring

`substring(string: TEXT, start: INTEGER [, length: INTEGER])` → TEXT

Extracts the substring starting at *start* (1-based), optionally limited to *length* characters. Without *length* it runs to the end of the string.

*start* counts from **1**. A value below that is outside the definition and the databases disagree on it — measured, `substring('alphabet', 0, 3)` gives `al` on most, nothing on MariaDB and Trino, and `alp` on Snowflake. Start at 1 or later, or clamp the value before passing it.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| start | INTEGER | 1-based index of the first character to keep |
| length | INTEGER *(optional)* | number of characters to take; to the end of the string if omitted |

Sample query:

```kql
// substring: first three characters of the company name.
FIND customers c
FETCH substring(c.company_name, 1, 3) abbrev
```

### Generated SQL

**duckdb · snowflake · postgresql · mariadb · sqlite · trino**

```sql
-- substring: first three characters of the company name.
SELECT
  substring(c.company_name, 1, 3) AS abbrev
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `SUBSTRING(c.company_name, 1, 3) AS abbrev` |
| oracle | `SUBSTR(c.company_name, 1, 3) AS abbrev` |


## left

`left(string: TEXT, n: INTEGER)` → TEXT

First *n* characters.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| n | INTEGER | number of leading characters to keep |

Sample query:

```kql
// left: first two characters of the postal code.
FIND customers c
FETCH left(c.postal_code, 2) prefix
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · mariadb**

```sql
-- left: first two characters of the postal code.
SELECT
  left(c.postal_code, 2) AS prefix
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite · trino | `substr(c.postal_code, 1, 2) AS prefix` |
| oracle | `SUBSTR(c.postal_code, 1, 2) AS prefix` |


## right

`right(string: TEXT, n: INTEGER)` → TEXT

Last *n* characters — the whole string if *n* reaches or exceeds its length, and nothing at all if *n* is zero.

Those two edges are worth stating because three dialects express `right` through a negative substring offset, where they used to fall out wrong: `right(s, 0)` returned the **whole** string on Oracle and SQLite, and `right(s, 99)` returned nothing on Oracle and Trino. Both are levelled now.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| n | INTEGER | number of trailing characters to keep |

Sample query:

```kql
// right: last four characters of the phone number.
FIND customers c
FETCH right(c.phone, 4) last_four
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · mariadb**

```sql
-- right: last four characters of the phone number.
SELECT
  right(c.phone, 4) AS last_four
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| oracle | `SUBSTR(c.phone, CASE WHEN 4 <= 0 THEN LENGTH(c.phone) + 1 ELSE GREATEST(LENGTH(c.phone) - 4 + 1, 1) END) AS last_four` |
| sqlite | `substr(c.phone, CASE WHEN 4 <= 0 THEN length(c.phone) + 1 ELSE max(length(c.phone) - 4 + 1, 1) END) AS last_four` |
| trino | `substr(c.phone, CASE WHEN 4 <= 0 THEN length(c.phone) + 1 ELSE GREATEST(length(c.phone) - 4 + 1, 1) END) AS last_four` |


## reverse

`reverse(string: TEXT)` → TEXT

Reverses the string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to reverse |

Sample query:

```kql
// reverse: company name reversed.
FIND customers c
FETCH reverse(c.company_name) reversed
```

### Generated SQL

**duckdb · snowflake · mssql · postgresql · mariadb · sqlite · trino**

```sql
-- reverse: company name reversed.
SELECT
  reverse(c.company_name) AS reversed
FROM
 customers c
```

Unsupported: **oracle**


## repeat

`repeat(string: TEXT, number: INTEGER)` → TEXT

Repeats the string *number* times.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to repeat |
| number | INTEGER | how many times to repeat the text |

Sample query:

```kql
// repeat: a ten-character rule.
FIND customers c
FETCH repeat('-', 10) ruler
```

### Generated SQL

**duckdb · snowflake · postgresql · mariadb**

```sql
-- repeat: a ten-character rule.
SELECT
  repeat('-', 10) AS ruler
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `REPLICATE('-', 10) AS ruler` |
| oracle | `RPAD('-', 10 * LENGTH('-'), '-') AS ruler` |
| trino | `array_join(repeat('-', 10), '') AS ruler` |

Unsupported: **sqlite**


## lpad

`lpad(string: TEXT, length: INTEGER [, fill: TEXT])` → TEXT

Pads the string on the left to *length* using *fill* (default space).

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to pad |
| length | INTEGER | target total length after padding |
| fill | TEXT *(optional)* | the padding text; a space if omitted |

Sample query:

```kql
// lpad: left-pad the postal code to width six with zeros.
FIND customers c
FETCH lpad(c.postal_code, 6, '0') padded
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · trino**

```sql
-- lpad: left-pad the postal code to width six with zeros.
SELECT
  lpad(c.postal_code, 6, '0') AS padded
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CASE WHEN LEN(c.postal_code) >= 6 THEN LEFT(c.postal_code, 6) ELSE RIGHT(REPLICATE('0', 6) + CAST(c.postal_code AS VARCHAR(MAX)), 6) END AS padded` |

Unsupported: **sqlite**


## rpad

`rpad(string: TEXT, length: INTEGER [, fill: TEXT])` → TEXT

Pads the string on the right to *length* using *fill* (default space).

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to pad |
| length | INTEGER | target total length after padding |
| fill | TEXT *(optional)* | the padding text; a space if omitted |

Sample query:

```kql
// rpad: right-pad the postal code to width ten with spaces.
FIND customers c
FETCH rpad(c.postal_code, 10, ' ') padded
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · trino**

```sql
-- rpad: right-pad the postal code to width ten with spaces.
SELECT
  rpad(c.postal_code, 10, ' ') AS padded
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `LEFT(CAST(c.postal_code AS VARCHAR(MAX)) + REPLICATE(' ', 10), 10) AS padded` |

Unsupported: **sqlite**


## concat

`concat(value: any, ...)` → TEXT

Concatenates the text form of all arguments; null arguments are ignored.

Operands: any type.

Sample query:

```kql
// concat: join first and last name.
FIND employees e
FETCH concat(e.first_name, ' ', e.last_name) full_name
```

### Generated SQL

**duckdb · oracle · mssql · postgresql · sqlite**

```sql
-- concat: join first and last name.
SELECT
  concat(e.first_name, ' ', e.last_name) AS full_name
FROM
 employees e
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb · trino | `concat_ws('', e.first_name, ' ', e.last_name) AS full_name` |
| snowflake | `ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT(e.first_name, ' ', e.last_name), '') AS full_name` |


## concat_ws

`concat_ws(separator: TEXT, ...)` → TEXT

Concatenates all arguments after the first, separated by *separator*.

| Argument | Type | Description |
|---|---|---|
| separator | TEXT | the separator placed between the joined values |

Sample query:

```kql
// concat_ws: join city and country with a separator.
FIND customers c
FETCH concat_ws(', ', c.city, c.country) location
```

### Generated SQL

**duckdb · mssql · postgresql · mariadb · sqlite · trino**

```sql
-- concat_ws: join city and country with a separator.
SELECT
  concat_ws(', ', c.city, c.country) AS location
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| snowflake | `ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT(c.city, c.country), ', ') AS location` |

Unsupported: **oracle**


## replace

`replace(string: TEXT, from: TEXT, to: TEXT)` → TEXT

Replaces all occurrences of *from* with *to*.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| from | TEXT | the substring to search for |
| to | TEXT | the replacement substring |

Sample query:

```kql
// replace: remove dashes from a phone number.
FIND customers c
FETCH replace(c.phone, '-', '') no_dashes
```

### Generated SQL

**all dialects**

```sql
-- replace: remove dashes from a phone number.
SELECT
  replace(c.phone, '-', '') AS no_dashes
FROM
 customers c
```


## translate

`translate(string: TEXT, from: TEXT, to: TEXT)` → TEXT

Replaces each character in *from* with the corresponding character in *to*.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| from | TEXT | the characters to replace |
| to | TEXT | the matching replacement characters |

Sample query:

```kql
// translate: transliterate German umlauts.
FIND customers c
FETCH translate(c.company_name, 'äöü', 'aou') ascii_name
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · trino**

```sql
-- translate: transliterate German umlauts.
SELECT
  translate(c.company_name, 'äöü', 'aou') AS ascii_name
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `translate(c.company_name, N'äöü', 'aou') AS ascii_name` |

Unsupported: **mariadb**, **sqlite**


## overlay

`overlay(string: TEXT, replacement: TEXT, start: INTEGER [, length: INTEGER])` → TEXT

Writes *replacement* over *string*, beginning at *start* and covering *length* characters — the rest of the text stays where it is. Without *length* it covers as many characters as *replacement* is long, so the string keeps its size; a larger *length* shortens it, a smaller one lengthens it.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| replacement | TEXT | the text to insert |
| start | INTEGER | 1-based index where replacement begins |
| length | INTEGER *(optional)* | number of characters to overwrite; the replacement's length if omitted |

Sample query:

```kql
// overlay: mask the first two characters of the phone.
FIND customers c
FETCH overlay(c.phone, 'XX', 1, 2) masked
```

### Generated SQL

**duckdb · trino**

```sql
-- overlay: mask the first two characters of the phone.
SELECT
  substr(c.phone, 1, 1 - 1) || 'XX' || substr(c.phone, 1 + 2) AS masked
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| snowflake · mariadb | `INSERT(c.phone, 1, 2, 'XX') AS masked` |
| mssql | `STUFF(c.phone, 1, 2, 'XX') AS masked` |
| oracle | `SUBSTR(c.phone, 1, 1 - 1) \|\| 'XX' \|\| SUBSTR(c.phone, 1 + 2) AS masked` |
| postgresql | `overlay(c.phone, 'XX', 1, 2) AS masked` |

Unsupported: **sqlite**


## starts_with

`starts_with(string: TEXT, prefix: TEXT)` → BOOLEAN

True if the string begins with *prefix*.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to test |
| prefix | TEXT | the prefix to look for |

Sample query:

```kql
// starts_with: does the company name start with A?
FIND customers c
FETCH starts_with(c.company_name, 'A') is_a
```

### Generated SQL

**duckdb · postgresql · trino**

```sql
-- starts_with: does the company name start with A?
SELECT
  starts_with(c.company_name, 'A') AS is_a
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `(LEFT(c.company_name, CHAR_LENGTH('A')) = 'A') AS is_a` |
| mssql | `CAST(CASE WHEN (LEFT(c.company_name, LEN('A')) = 'A') THEN 1 ELSE 0 END AS BIT) AS is_a` |
| oracle | `(SUBSTR(c.company_name, 1, LENGTH('A')) = 'A') AS is_a` |
| snowflake | `STARTSWITH(c.company_name, 'A') AS is_a` |
| sqlite | `(substr(c.company_name, 1, length('A')) = 'A') AS is_a` |


## split_part

`split_part(string: TEXT, delimiter: TEXT, n: INTEGER)` → TEXT

Splits at *delimiter* and returns the *n*-th field (1-based).

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to split |
| delimiter | TEXT | the delimiter to split on |
| n | INTEGER | 1-based index of the field to return |

Sample query:

```kql
// split_part: first dash-separated field of the phone.
FIND customers c
FETCH split_part(c.phone, '-', 1) first_field
```

### Generated SQL

**duckdb · snowflake · postgresql · trino**

```sql
-- split_part: first dash-separated field of the phone.
SELECT
  split_part(c.phone, '-', 1) AS first_field
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `SUBSTRING_INDEX(SUBSTRING_INDEX(c.phone, '-', 1), '-', -1) AS first_field` |
| mssql | `(SELECT value FROM STRING_SPLIT(c.phone, '-', 1) WHERE ordinal = 1) AS first_field` |

Unsupported: **oracle**, **sqlite**


## ascii

`ascii(character: TEXT)` → INTEGER

Numeric code of the first character.

**Defined for ASCII.** Beyond it the databases part company, because they do not agree what a "character code" is: measured, `ascii('Ö')` gives `214` — the Unicode code point — on DuckDB, PostgreSQL, SQLite, Trino and SQL Server, `195` (the first **byte** of the UTF-8 form) on MariaDB and Snowflake, and `50070` (the whole multi-byte value) on Oracle. `chr` mirrors the split. Stay inside ASCII, or map the characters in the application.

| Argument | Type | Description |
|---|---|---|
| character | TEXT | the text whose first character is coded |

Sample query:

```kql
// ascii: code point of the first character.
FIND customers c
FETCH ascii(c.company_name) first_code
```

### Generated SQL

**duckdb · oracle · snowflake · mssql · postgresql · mariadb**

```sql
-- ascii: code point of the first character.
SELECT
  ascii(c.company_name) AS first_code
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| sqlite | `unicode(c.company_name) AS first_code` |
| trino | `codepoint(cast(substr(c.company_name, 1, 1) as varchar(1))) AS first_code` |


## chr

`chr(code: INTEGER)` → TEXT

Character with the given numeric code.

**Defined for ASCII.** Beyond it the databases part company, because they do not agree what a "character code" is: measured, `ascii('Ö')` gives `214` — the Unicode code point — on DuckDB, PostgreSQL, SQLite, Trino and SQL Server, `195` (the first **byte** of the UTF-8 form) on MariaDB and Snowflake, and `50070` (the whole multi-byte value) on Oracle. `chr` mirrors the split. Stay inside ASCII, or map the characters in the application.

| Argument | Type | Description |
|---|---|---|
| code | INTEGER | the character code to convert |

Sample query:

```kql
// chr: character for a code point.
FIND customers c
FETCH chr(65) letter_a
```

### Generated SQL

**duckdb · oracle · snowflake · postgresql · mariadb · trino**

```sql
-- chr: character for a code point.
SELECT
  chr(65) AS letter_a
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CHAR(65) AS letter_a` |
| sqlite | `char(65) AS letter_a` |


## to_hex

`to_hex(number: INTEGER)` → TEXT

Hexadecimal representation of the number, in **upper case** and without a leading `0x`.

A negative number comes out as its two's-complement form, and how wide that is depends on the database's integer width — so a negative input is not portable. Positive values agree everywhere.

| Argument | Type | Description |
|---|---|---|
| number | INTEGER | the number to convert to hexadecimal |

Sample query:

```kql
// to_hex: hexadecimal of a number.
FIND customers c
FETCH to_hex(255) hex_ff
```

### Generated SQL

**duckdb**

```sql
-- to_hex: hexadecimal of a number.
SELECT
  to_hex(255) AS hex_ff
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mariadb | `HEX(255) AS hex_ff` |
| mssql | `UPPER(CASE WHEN 255 = 0 THEN '0' ELSE SUBSTRING(CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST(255 AS BIGINT)), 2), PATINDEX('%[^0]%', CONVERT(VARCHAR(16), CONVERT(VARBINARY(8), CAST(255 AS BIGINT)), 2)), 16) END) AS hex_ff` |
| oracle | `TO_CHAR(255, 'FMXXXXXXXXXXXXXXXX') AS hex_ff` |
| postgresql | `upper(to_hex(255)) AS hex_ff` |
| sqlite | `printf('%X', 255) AS hex_ff` |
| trino | `upper(to_base(255, 16)) AS hex_ff` |

Unsupported: **snowflake**


## md5

`md5(string: TEXT)` → TEXT

MD5 hash as a hexadecimal string.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the text to hash |

Sample query:

```kql
// md5: MD5 hash of the company name.
FIND customers c
FETCH md5(c.company_name) hash
```

### Generated SQL

**duckdb · snowflake · postgresql · mariadb**

```sql
-- md5: MD5 hash of the company name.
SELECT
  md5(c.company_name) AS hash
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `LOWER(CONVERT(VARCHAR(32), HASHBYTES('MD5', c.company_name), 2)) AS hash` |

Unsupported: **oracle**, **sqlite**, **trino**


## position

`position(substr: TEXT, str: TEXT)` → INTEGER

Position of the first occurrence of *substr* in *str* (1-based, 0 if absent).

| Argument | Type | Description |
|---|---|---|
| substr | TEXT | the substring to locate |
| str | TEXT | the text to search in |

Sample query:

```kql
// position: position of a dash within the phone.
FIND customers c
FETCH position('-', c.phone) dash_pos
```

### Generated SQL

**duckdb · snowflake · postgresql · mariadb · trino**

```sql
-- position: position of a dash within the phone.
SELECT
  POSITION('-' IN c.phone) AS dash_pos
FROM
 customers c
```

The remaining dialects differ only in this expression:

| Dialect | Expression |
|---|---|
| mssql | `CHARINDEX('-', c.phone) AS dash_pos` |
| oracle | `INSTR(c.phone, '-') AS dash_pos` |
| sqlite | `instr(c.phone, '-') AS dash_pos` |

