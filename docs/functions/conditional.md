---
menuTitle: "Conditional Expressions"
parent: "FUNCTIONS"
order: 6
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Conditional Expressions

## coalesce

`coalesce(value: any, ...)` → argument-dependent

First non-null argument.

Standard SQL: `coalesce(…) — dialect-specific rendering`

Example: `coalesce(o.shipped_date, today())`


## case

`case(condition: BOOLEAN, result: any, ...)` → argument-dependent

Searched CASE: case(cond1, result1, ..., [else]) -> CASE WHEN cond1 THEN result1 ... [ELSE else] END.

Standard SQL: `case(…) — dialect-specific rendering`

Example: `case(o.freight > 100, 'high', 'low')`

