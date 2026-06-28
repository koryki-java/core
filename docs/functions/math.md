---
menuTitle: "Mathematical Functions"
parent: "FUNCTIONS"
order: 1
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Mathematical Functions

## abs

`abs(value: numeric)` → argument-dependent

Absolute value.

Standard SQL: `abs(value)`

Example: `abs(-17.4)`


## ceil

`ceil(value: numeric)` → argument-dependent

Nearest integer greater than or equal to *value*.

Standard SQL: `ceil(value)`

Example: `ceil(o.freight)`


## ceiling

`ceiling(value: numeric)` → argument-dependent

Nearest integer greater than or equal to *value* (alias of ceil).

Standard SQL: `ceiling(value)`


## floor

`floor(value: numeric)` → argument-dependent

Nearest integer less than or equal to *value*.

Standard SQL: `floor(value)`

Example: `floor(o.freight)`


## round

`round(value: numeric [, scale: INTEGER])` → argument-dependent

Rounds to *scale* decimal places (default 0).

Half-way rounding of binary-float inputs is engine-defined: a value such as `0.15` is stored as `0.149999…`, so engines may round it up or down.

See: [What Every Computer Scientist Should Know About Floating-Point Arithmetic](https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html).

Standard SQL: `round(value, scale)`

Example: `round(o.freight, 2)`


## mod

`mod(dividend: numeric, divisor: numeric)` → argument-dependent

Remainder of *dividend* / *divisor*.

Standard SQL: `mod(dividend, divisor)`

Example: `mod(o.order_id, 7)`


## power

`power(base: numeric, exponent: numeric)` → FLOAT

*base* raised to the power of *exponent*.

Standard SQL: `power(base, exponent)`

Example: `power(c.value, 2)`


## sqrt

`sqrt(value: numeric)` → FLOAT

Square root.

Standard SQL: `sqrt(value)`

Example: `sqrt(2)`


## pi

`pi()` → FLOAT

Approximate value of π.

Standard SQL: `pi()`


## random

`random()` → FLOAT

Random value in the range 0.0 <= x < 1.0.

Standard SQL: `random()`

