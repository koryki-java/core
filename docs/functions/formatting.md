---
menuTitle: "Data Type Formatting Functions"
parent: "FUNCTIONS"
order: 4
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Data Type Formatting Functions

## to_char

`to_char(value: any, format: TEXT)` → TEXT

Formats a number, date or timestamp using the dialect-native *format* mask.

Standard SQL: `to_char(value, format)`

Example: `to_char(o.order_date, 'YYYY-MM')`


## to_number

`to_number(value: TEXT, format: TEXT)` → DECIMAL

Parses a string into a number using the dialect-native *format* mask.

Standard SQL: `to_number(value, format)`

Example: `to_number(c.postal_code, '99999')`

