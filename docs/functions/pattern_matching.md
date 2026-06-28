---
menuTitle: "Pattern Matching"
parent: "FUNCTIONS"
order: 3
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Pattern Matching

## regexp_like

`regexp_like(string: TEXT, pattern: TEXT [, flags: TEXT])` → BOOLEAN

True if the string matches the regular expression *pattern*.

Standard SQL: `regexp_like(string, pattern, flags)`

Example: `regexp_like(c.company_name, '^A')`


## regexp_count

`regexp_count(string: TEXT, pattern: TEXT [, start: INTEGER])` → INTEGER

Number of matches of *pattern* in the string.

Standard SQL: `regexp_count(string, pattern, start)`

Example: `regexp_count(e.notes, 'sales')`


## regexp_substr

`regexp_substr(string: TEXT, pattern: TEXT)` → TEXT

First substring matching the regular expression *pattern*.

Standard SQL: `regexp_substr(string, pattern)`

Example: `regexp_substr(c.phone, '[0-9]+')`


## regexp_replace

`regexp_replace(string: TEXT, pattern: TEXT, replacement: TEXT [, flags: TEXT])` → TEXT

Replaces substrings matching the regular expression *pattern* with *replacement*.

Standard SQL: `regexp_replace(string, pattern, replacement, flags)`

Example: `regexp_replace(c.phone, '[^0-9]', '')`

