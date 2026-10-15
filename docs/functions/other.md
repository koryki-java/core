---
menuTitle: "Other Functions"
parent: "FUNCTIONS"
order: 13
---

<!-- Generated from the function catalog — do not edit. Delete this file and re-run the tests to regenerate. -->

# Other Functions

## list_transform

`list_transform(list: ARRAY, function: LAMBDA)` → argument-dependent

A list of what the function returns for each element of the list.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| function | LAMBDA | what is done to each element: x -> ..., or (x, i) -> ... with the position |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_filter

`list_filter(list: ARRAY, function: LAMBDA)` → argument-dependent

The elements of the list for which the function is true, in order.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| function | LAMBDA | the test an element has to pass: x -> ..., or (x, i) -> ... |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_reduce

`list_reduce(list: ARRAY, function: LAMBDA)` → argument-dependent

The elements of the list combined into one value, from the first element on.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| function | LAMBDA | combines the result so far with the next element: (a, x) -> ... |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **oracle**, **postgresql**, **sqlite**


## list_size

`list_size(list: ARRAY)` → INTEGER

The number of elements of the list, NULL elements included.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_contains

`list_contains(list: ARRAY, value: any)` → BOOLEAN

Whether the list has an element equal to the value.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| value | any | the value to look for |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_sum

`list_sum(list: ARRAY)` → argument-dependent

The sum of the elements, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list of numbers |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_min

`list_min(list: ARRAY)` → argument-dependent

The smallest element, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_max

`list_max(list: ARRAY)` → argument-dependent

The largest element, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_avg

`list_avg(list: ARRAY)` → DOUBLE

The mean of the elements as a floating point number, NULL elements ignored.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list of numbers |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_any

`list_any(list: ARRAY, function: LAMBDA)` → BOOLEAN

Whether at least one element of the list passes the test.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| function | LAMBDA | the test an element can pass: x -> ... |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_all

`list_all(list: ARRAY, function: LAMBDA)` → BOOLEAN

Whether every element of the list passes the test; true for an empty list.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| function | LAMBDA | the test every element has to pass: x -> ... |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_sort

`list_sort(list: ARRAY [, order: TEXT])` → argument-dependent

The list in order, NULL elements last.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |
| order | TEXT *(optional)* | 'asc' (the default) or 'desc' |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_distinct

`list_distinct(list: ARRAY)` → argument-dependent

The elements of the list without duplicates and without NULLs, in ascending order.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the list |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list_concat

`list_concat(list: ARRAY, other: ARRAY)` → argument-dependent

The first list followed by the second. A NULL list counts as an empty one; the answer is NULL only when both are.

| Argument | Type | Description |
|---|---|---|
| list | ARRAY | the first list |
| other | ARRAY | the list that follows |

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**


## list

`list(value: any, ...)` → argument-dependent

A list of the values, in the order they are written.

Operands: any type.

### Generated SQL

Unsupported: **mariadb**, **mssql**, **sqlite**

