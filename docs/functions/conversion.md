---
menuTitle: "Type Conversion"
parent: "FUNCTIONS"
order: 9
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Type Conversion

## to_date

`to_date(value: any)` → DATE

Converts a timestamp or date-compatible value to a DATE, discarding any time component. Text input must be ISO 8601 (`YYYY-MM-DD`); other formats are dialect-dependent — use `parse_date` for an explicit format mask.

Standard SQL: `CAST(value AS DATE)`

Example: `to_date(c.signup_text)`


## to_time

`to_time(value: any)` → TIME

Converts a timestamp or time-compatible value to a TIME, discarding the date part. Text input must be in `HH:MM:SS` format.

Standard SQL: `CAST(value AS TIME)`

Example: `to_time(o.pickup_text)`


## to_timestamp

`to_timestamp(value: any)` → TIMESTAMP

Converts a date or text value to a TIMESTAMP. Text input must be ISO 8601 (`YYYY-MM-DD HH:MM:SS`); a DATE is extended with midnight.

Standard SQL: `CAST(value AS TIMESTAMP)`

Example: `to_timestamp(o.created_text)`


## to_boolean

`to_boolean(value: any)` → BOOLEAN

Converts a numeric value to BOOLEAN (`0` = false, non-zero = true).

Standard SQL: `CAST(value AS BOOLEAN)`

Example: `to_boolean(c.active_flag)`


## to_text

`to_text(value: BLOB)` → TEXT

Converts a BLOB *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: BOOLEAN)` → TEXT

Converts a BOOLEAN *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: DATE)` → TEXT

Converts a DATE *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: DECIMAL)` → TEXT

Converts a DECIMAL *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: FLOAT)` → TEXT

Converts a FLOAT *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: INTEGER)` → TEXT

Converts a INTEGER *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: TIME)` → TEXT

Converts a TIME *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: DURATION)` → TEXT

Converts a DURATION *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: TIMESTAMP)` → TEXT

Converts a TIMESTAMP *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: TEXT)` → TEXT

Converts a TEXT *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: JSON)` → TEXT

Converts a JSON *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`

`to_text(value: UUID)` → TEXT

Converts a UUID *value* to TEXT.

Standard SQL: `CAST(value AS TEXT)`


## to_float

`to_float(value: any)` → FLOAT

Converts a numeric or text value to single-precision float. Fractional precision may be lost relative to the source.

Standard SQL: `CAST(value AS FLOAT)`

Example: `to_float(c.rating_text)`


## to_double

`to_double(value: any)` → DOUBLE

Converts a numeric or text value to double-precision float.

Standard SQL: `CAST(value AS DOUBLE)`

Example: `to_double(m.reading_text)`


## to_integer

`to_integer(value: any)` → INTEGER

Converts a numeric or text value to a 32-bit integer. The fractional part is truncated, not rounded; overflow behaviour is dialect-defined.

Standard SQL: `CAST(value AS INTEGER)`

Example: `to_integer(o.quantity_text)`


## to_bigint

`to_bigint(value: any)` → BIGINT

Converts a numeric or text value to a 64-bit integer. Use instead of `to_integer` when values may exceed ±2 147 483 647.

Standard SQL: `CAST(value AS BIGINT)`

Example: `to_bigint(e.event_id_text)`


## to_smallint

`to_smallint(value: any)` → SMALLINT

Converts a numeric or text value to a 16-bit integer (range −32 768 to 32 767). Overflow behaviour is dialect-defined.

Standard SQL: `CAST(value AS SMALLINT)`

Example: `to_smallint(c.age_text)`


## to_decimal

`to_decimal(value: any, precision: INTEGER, scale: INTEGER)` → argument-dependent

Converts *value* to a fixed-point decimal. *precision* is the total number of significant digits, *scale* the digits after the point — `to_decimal(value, 10, 2)` holds up to 99 999 999.99.

Standard SQL: `CAST(value AS DECIMAL(precision, scale))`

Example: `to_decimal(o.unit_price, 10, 2)`


## to_varchar

`to_varchar(value: any, length: INTEGER)` → TEXT

Converts *value* to a variable-length string with an explicit maximum length — useful when the target column has a defined width.

Standard SQL: `CAST(value AS VARCHAR(length))`

Example: `to_varchar(c.company_name, 40)`

