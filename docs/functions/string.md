---
menuTitle: "String Functions"
parent: "FUNCTIONS"
order: 2
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# String Functions

## char_length

`char_length(string: TEXT)` → INTEGER

Number of characters in the string.

Standard SQL: `char_length(string)`

Example: `char_length(c.company_name)`


## character_length

`character_length(string: TEXT)` → INTEGER

Number of characters in the string.

Standard SQL: `character_length(string)`

Example: `character_length(c.company_name)`


## length

`length(string: TEXT)` → INTEGER

Number of characters in the string.

Standard SQL: `length(string)`

Example: `length(c.company_name)`


## octet_length

`octet_length(string: TEXT)` → INTEGER

Number of bytes in the string.

Standard SQL: `octet_length(string)`

Example: `octet_length(c.company_name)`


## bit_length

`bit_length(string: TEXT)` → INTEGER

Number of bits in the string.

Standard SQL: `bit_length(string)`

Example: `bit_length(c.company_name)`


## upper

`upper(string: TEXT)` → TEXT

Converts the string to upper case.

Standard SQL: `upper(string)`

Example: `upper(c.company_name)`


## lower

`lower(string: TEXT)` → TEXT

Converts the string to lower case.

Standard SQL: `lower(string)`

Example: `lower(c.company_name)`


## initcap

`initcap(string: TEXT)` → TEXT

Capitalizes the first letter of each word.

Standard SQL: `initcap(string)`

Example: `initcap(c.contact_name)`


## trim

`trim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from both ends.

Standard SQL: `trim(string, characters)`

Example: `trim(c.company_name)`


## ltrim

`ltrim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from the start.

Standard SQL: `ltrim(string, characters)`

Example: `ltrim(c.postal_code)`


## rtrim

`rtrim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from the end.

Standard SQL: `rtrim(string, characters)`

Example: `rtrim(c.postal_code)`


## btrim

`btrim(string: TEXT [, characters: TEXT])` → TEXT

Removes *characters* (default spaces) from both ends (alias of trim).

Standard SQL: `btrim(string, characters)`


## substr

`substr(string: TEXT, start: INTEGER [, length: INTEGER])` → TEXT

Extracts the substring starting at *start* (1-based), optionally limited to *length* characters.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| start | INTEGER | 1-based index of the first character to keep |
| length | INTEGER *(optional)* | number of characters to take; to the end of the string if omitted |

Standard SQL: `substr(string, start, length)`

Example: `substr(c.company_name, 1, 3)`


## substring

`substring(string: TEXT, start: INTEGER [, length: INTEGER])` → TEXT

Extracts the substring starting at *start* (1-based), optionally limited to *length* characters.

| Argument | Type | Description |
|---|---|---|
| string | TEXT | the source text |
| start | INTEGER | 1-based index of the first character to keep |
| length | INTEGER *(optional)* | number of characters to take; to the end of the string if omitted |

Standard SQL: `substring(string, start, length)`

Example: `substr(c.company_name, 1, 3)`


## left

`left(string: TEXT, n: INTEGER)` → TEXT

First *n* characters.

Standard SQL: `left(string, n)`

Example: `left(c.postal_code, 2)`


## right

`right(string: TEXT, n: INTEGER)` → TEXT

Last *n* characters.

Standard SQL: `right(string, n)`

Example: `right(c.phone, 4)`


## reverse

`reverse(string: TEXT)` → TEXT

Reverses the string.

Standard SQL: `reverse(string)`

Example: `reverse(c.company_name)`


## repeat

`repeat(string: TEXT, number: INTEGER)` → TEXT

Repeats the string *number* times.

Standard SQL: `repeat(string, number)`

Example: `repeat('-', 10)`


## lpad

`lpad(string: TEXT, length: INTEGER [, fill: TEXT])` → TEXT

Pads the string on the left to *length* using *fill* (default space).

Standard SQL: `lpad(string, length, fill)`

Example: `lpad(c.postal_code, 6, '0')`


## rpad

`rpad(string: TEXT, length: INTEGER [, fill: TEXT])` → TEXT

Pads the string on the right to *length* using *fill* (default space).

Standard SQL: `rpad(string, length, fill)`

Example: `rpad(c.postal_code, 10)`


## concat

`concat(value: any, ...)` → TEXT

Concatenates the text form of all arguments; null arguments are ignored.

Standard SQL: `concat(value)`

Example: `concat(e.first_name, ' ', e.last_name)`


## concat_ws

`concat_ws(separator: any, ...)` → TEXT

Concatenates all arguments after the first, separated by *separator*.

Standard SQL: `concat_ws(separator)`

Example: `concat_ws(', ', c.city, c.country)`


## replace

`replace(string: TEXT, from: TEXT, to: TEXT)` → TEXT

Replaces all occurrences of *from* with *to*.

Standard SQL: `replace(string, from, to)`

Example: `replace(c.phone, '-', '')`


## translate

`translate(string: TEXT, from: TEXT, to: TEXT)` → TEXT

Replaces each character in *from* with the corresponding character in *to*.

Standard SQL: `translate(string, from, to)`

Example: `translate(c.company_name, 'äöü', 'aou')`


## overlay

`overlay(string: TEXT, replacement: TEXT, start: INTEGER [, length: INTEGER])` → TEXT

Replaces a substring (dialect-specific syntax).

Standard SQL: `overlay(string, replacement, start, length)`


## starts_with

`starts_with(string: TEXT, prefix: TEXT)` → BOOLEAN

True if the string begins with *prefix*.

Standard SQL: `starts_with(string, prefix)`

Example: `starts_with(c.company_name, 'A')`


## strpos

`strpos(string: TEXT, substring: TEXT)` → INTEGER

Position of the first occurrence of *substring* (1-based, 0 if absent).

Standard SQL: `strpos(string, substring)`

Example: `strpos(c.phone, '-')`


## split_part

`split_part(string: TEXT, delimiter: TEXT, n: INTEGER)` → TEXT

Splits at *delimiter* and returns the *n*-th field (1-based).

Standard SQL: `split_part(string, delimiter, n)`

Example: `split_part(c.phone, '-', 1)`


## ascii

`ascii(character: TEXT)` → INTEGER

Numeric code of the first character.

Standard SQL: `ascii(character)`

Example: `ascii(c.company_name)`


## chr

`chr(code: INTEGER)` → TEXT

Character with the given numeric code.

Standard SQL: `chr(code)`

Example: `chr(65)`


## to_hex

`to_hex(number: INTEGER)` → TEXT

Hexadecimal representation of the number.

Standard SQL: `to_hex(number)`

Example: `to_hex(255)`


## md5

`md5(string: TEXT)` → TEXT

MD5 hash as a hexadecimal string.

Standard SQL: `md5(string)`

Example: `md5(c.company_name)`


## position

`position(substr: TEXT, str: TEXT)` → INTEGER

Position of the first occurrence of *substr* in *str* (1-based, 0 if absent).

Standard SQL: `POSITION(substr IN str)`

Example: `position('-', c.phone)`

