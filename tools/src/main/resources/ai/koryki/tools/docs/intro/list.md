Functions on a list: a column that holds one (an ARRAY, or a JSON array where the catalog declares it),
or what another list function or a lambda made. They ask the list about itself (`list_size`,
`list_contains`, `list_sum`, `list_min`, `list_max`, `list_avg`), shape it (`list_sort`,
`list_distinct`, `list_concat`) or make one (`list(a, b, ...)`; `list_agg` is an aggregate and is on
the [Aggregate](aggregate.md) page). The ones that take a function per element, `list_transform`
and the like, are on the [Lambda](lambda.md) page.

    list_size(t.tags)                     →  3
    list_contains(t.tags, 'red')          →  true      -- or: 'red' IN t.tags
    list_sum(t.scores)                    →  17
    list_sort(t.tags, 'desc')

A list that is NULL gives NULL. A list with nothing left to count (empty, or only NULLs) gives NULL
for the sum, the mean, the smallest and the largest. A list is not a text: `length(t.tags)` is
refused and names `list_size`.

Lists exist on DuckDB, PostgreSQL, Trino, Snowflake and Oracle; a use that another dialect cannot
express is refused with its position. [Nested values](NESTED.md) has the table, and what each
dialect does differently.
