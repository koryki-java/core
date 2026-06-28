---
menuTitle: "Arithmetic Operators"
parent: "FUNCTIONS"
order: 10
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Arithmetic Operators

## negate

`negate(value: any)` → argument-dependent

Arithmetic negation (`-x`): negates a number, or flips the sign of every component of a DURATION.

Standard SQL: `negate(…) — dialect-specific rendering`

Example: `-c.balance`


## add

`add(left: any, right: any, ...)` → argument-dependent

Addition (`+`): numeric addition, or temporal arithmetic per the result-type algebra — DATE/TIMESTAMP/TIME + DURATION, DATE + TIME, and DURATION + DURATION (see docs/TEMPORAL.md).

Standard SQL: `add(…) — dialect-specific rendering`

Example: `o.order_date + 30d`


## minus

`minus(left: any, right: any, ...)` → argument-dependent

Subtraction (`-`): numeric subtraction, or temporal — DATE − DATE and TIMESTAMP − TIMESTAMP yield a DURATION, and a temporal value − DURATION shifts it (see docs/TEMPORAL.md).

Standard SQL: `minus(…) — dialect-specific rendering`

Example: `o.shipped_date - o.order_date`


## multiply

`multiply(left: any, right: any, ...)` → argument-dependent

Multiplication (`*`): numeric multiplication, or DURATION × integer scaled component-wise (see docs/TEMPORAL.md).

Standard SQL: `multiply(…) — dialect-specific rendering`

Example: `o.unit_price * o.quantity`


## divide

`divide(left: any, right: any, ...)` → argument-dependent

Division (`/`): numeric division; the result is decimal/double. Dividing a DURATION is not defined.

Standard SQL: `divide(…) — dialect-specific rendering`

Example: `o.freight / o.quantity`

