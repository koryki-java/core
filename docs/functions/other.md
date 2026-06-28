---
menuTitle: "Other Functions"
parent: "FUNCTIONS"
order: 13
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Other Functions

## at_zone

`at_zone(value: date/time, zone: TEXT)` → TIMESTAMP

Reads *value* as a model-zone wall-clock value and returns its wall-clock in the named zone (e.g. for day-bucketing: date(at_zone(o.ts, 'Europe/Berlin'))).

Standard SQL: `at_zone(…) — dialect-specific rendering`

Example: `date(at_zone(o.ts, 'Europe/Berlin'))`


## to_utc

`to_utc(value: date/time, zone: TEXT)` → TIMESTAMP

Inverse of at_zone: reads *value* as a wall-clock in the named zone and returns the model-zone wall-clock value.

Standard SQL: `to_utc(…) — dialect-specific rendering`

Example: `to_utc(o.local_ts, 'Europe/Berlin')`

