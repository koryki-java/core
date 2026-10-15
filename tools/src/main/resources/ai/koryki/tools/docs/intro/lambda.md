Functions that take a **lambda**: an expression with named parameters and an arrow, applied to each
element of a list. Only these functions take one, and only as an argument.

    list_transform(t.scores, x -> x * 2)          -- every element doubled
    list_filter(t.scores, x -> x > 5)             -- the elements that pass
    list_reduce(t.scores, (acc, x) -> acc + x)    -- folded into one value
    list_any(t.items, it -> it@price > 100)       -- whether some element passes

The parameters are named by you and used by that name. A selection from a parameter is the usual
postfix one, `it@price`, while `t.price` stays a field of the alias `t`. A lambda sees the columns of
the query as well as its parameters; a parameter may not be named like an alias in sight, nor like
the parameter of a lambda around it.

`list_transform` and `list_filter` take one parameter (the element) or two (element, position from
1). `list_reduce` takes two (state, element); the first element is the initial state, so a list of one
gives that element. `list_any` and `list_all` take a test: a test that is NULL does not pass, and an
empty list has no element that passes, and every element of it does. A NULL list stays NULL.

Lambdas need lists, so they exist where lists do (DuckDB, PostgreSQL, Trino, Snowflake and Oracle),
and some uses are narrower: `list_reduce` is unsupported on PostgreSQL and Oracle, and Oracle cannot
build a list of objects for `list_filter`. A use that a dialect cannot express is refused with its
position. See [Nested values](NESTED.md) for the table and the details, and the
[List](list.md) page for the functions that need no lambda.
