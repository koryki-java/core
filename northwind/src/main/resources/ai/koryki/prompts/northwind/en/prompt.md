# Instructions for answers

If a query is available, validate first using tool: validateKql.
send query to validateKql in plain text, no markdown, no json, just plain text, no formatting.
If validation fails, try no more than maximum three times, 3 times maximum validation. Then state error message.
Pass formatted querystring returned by validateKql method.
Do not try to evaluate results of query-Service. Instead, try to include all information available inside query.

no markdown with embedded json, instead json objekt only

Answer JSON-only object matching
{
"query": string | null,
"message": string | null,
"error": string | null
}

markdown is allowed inside message field only


# Usage of korykiai query language (kql)

kql is easy to understand by humans and easy to generate by AI.
kql-queries are transformed into SQL and then executed in databases.

kql uses terms defined in schema.md.

kql is quite different from SQL, but intend to pursue the same objectives for data retrieval.

All alias and blockid must be querywide unique. 

## first example

    FIND customers c, c orders o
    FILTER count(o) > 10 AND
        o.order_date BETWEEN "2023-01-01" AND "2023-01-31"
    FETCH c.company_name, count(o) DESC

Three major keywords: **FIND**, **FILTER**, **FETCH**.

### FIND-Clause

Keyword **FIND** is followed by a first entity and optional a list of links. The purpose of **FIND** is to define the
entities and entity-relations we are looking for.

The first entity `customers` followed by alias `c`. Aliases are used to refer entity inside the query.

Then link `c orders o`. Entity `c` is linked to entity `orders` with alias `o`.
Anonymous form because only one link exists between `customers` and `orders`.
If more than one link connects `customers` to `orders`, specify the link name using `VIA`:

    c VIA same_customer orders o

You must link all entities, using anonymous form or named form with `VIA link_name`:

    FIND orders o, o VIA same_customer customers c, o employees e, o order_details od
    FETCH o.order_date, c.company_name, e.first_name, sum(od.unit_price * od.quantity) total_value

#### INNER-Link

INNER-Link resolves to INNER JOIN in SQL. Connected entity must exist. Write the link without `+`.

#### OPTIONAL-Link

OPTIONAL-Link resolves to OUTER JOIN in SQL. Connected entity may not exist. Place `+` at one end of the link.

    FIND employees emp, emp VIA report_to + employees boss

### FILTER-Clause

**FILTER** is followed by one logical expression. A logical expression can be a composition of
logical expressions using **AND**, **OR** and **NOT**.

    a AND b OR NOT c

For better reading we can introduce brackets. The expression is equivalent to:

    (a AND b) OR (NOT c)

a, b and c are unary logical expressions like:

    lower(emp.last_name) LIKE 'a%'
    emp.date_of_birth BETWEEN "2002-01-01" AND "2002-12-31"
    count(o) > 10
    emp.home_phone ISNULL

Each unary logical expression resolve to true or false.

#### EXISTS

In FILTER-Clause you can use EXISTS expressions to test whether a linked entity exists or not:

    // Find customers without orders
    
    FIND customers c
    FILTER NOT EXISTS (c orders o)
    FETCH c.contact_name


The first element references an alias from the outer FIND-Clause. Subsequent links are the same as in FIND-Clause.

Use exists expression if user asks for absent entities. Do not use property ISNULL.

### FETCH-Clause

**FETCH** is followed by a comma-separated list of expressions the query should give as result-columns.

Each FETCH-expression can have an optional header and optional ASC/DESC for ordering. Order-Position is optional.

    FIND employees e FETCH e.last_name ASC 1

## Charts (VISUALISE)

The **FETCH**-Clause may be followed by an optional **VISUALISE**-Clause that renders the result as a
chart. Not every query needs a chart — add one only when a visualisation is meaningful (comparisons,
distributions, time series, shares). The **VISUALISE**-Clause does not change the returned data.

**VISUALISE** refers to the column headers (aliases) from the **FETCH**-Clause, not to the raw columns.

    FIND customers c
    FETCH c.country country, count(c) amount
    VISUALISE country AS x, amount AS y
    DRAW bar

### Mapping and channels

**VISUALISE** is followed by a comma-separated list of mappings of the form `column AS channel`.
Available channels:

- `x`, `y` — position (horizontal / vertical axis)
- `color`, `fill` — colour (to group)
- `size` — size
- `shape` — point shape
- `opacity` — transparency
- `text` — in-chart label
- `tooltip` — hover info
- `theta`, `radius` — angle and radius (pie / radial chart)

Every mapped column must be a result column from the **FETCH**-Clause.

### DRAW — chart type

**DRAW** sets the chart type. Multiple **DRAW** stack as layers.

Basic types:

- `point` — scatter
- `line` — line chart (time series)
- `area` — area chart
- `bar` — bar chart
- `text` — text marks
- `tile` — raster / heatmap cells

Statistical types — computed in the database:

- `histogram` — value distribution (map `x` only)
- `boxplot` — box-and-whisker (quartiles) per group
- `smooth` — trend line (linear regression), usually together with `point`
- `density` — density curve
- `violin` — violin plot

Two layers (points plus a trend line):

    FIND order_details d
    FETCH d.unit_price price, d.quantity amount
    VISUALISE price AS x, amount AS y
    DRAW point
    DRAW smooth

### Aggregate in the chart

A chart can aggregate in the database even when the query itself does not group:

    FIND customers c FETCH c.country country
    VISUALISE country AS x
    DRAW bar SETTING aggregate => 'count'

Values for `aggregate`: `'count'`, `'sum'`, `'avg'`, `'min'`, `'max'` (all but `'count'` aggregate the `y` column).

### Labels (LABEL)

Set the title and axis labels with **LABEL** (`title` for the chart title, otherwise the channel name):

    VISUALISE month AS x, revenue AS y
    DRAW line
    LABEL title => 'Revenue per month', x => 'Month', y => 'Revenue'

### Small multiples (FACET)

Use **FACET** to draw one sub-chart per value of a column:

    VISUALISE month AS x, revenue AS y
    DRAW line
    FACET category_name

### Scales (SCALE)

Use **SCALE** to adjust an axis, e.g. logarithmic:

    VISUALISE product AS x, revenue AS y
    DRAW bar
    SCALE y VIA log

### Full example

    FIND orders o, o order_details od, od products p, p categories c
    FETCH c.category_name category, month(o.order_date) month,
        sum(od.unit_price * od.quantity) revenue
    VISUALISE month AS x, revenue AS y, category AS color
    DRAW line
    LABEL title => 'Revenue per category and month'

## Nested Queries

Nested queries are valid in expression:

    FIND products p, p categories c
    FILTER p.unit_price > (
        FIND products p2, p2 categories c2
        FILTER c2.category_name = c.category_name
        FETCH avg(p2.unit_price)
    )
    FETCH p.product_name

## Set-Operation

Connect resultsets of queries using SET-Operators:

    FIND products p
    FILTER p.units_in_stock < 20
    FETCH p.product_name
    INTERSECT
    FIND products p
    FILTER lower(p.product_name) LIKE 'a%'
    FETCH p.product_name

## Query-Blocks

Use query-blocks to reuse inside query.

    WITH sales AS (
        FIND orders o, o order_details d
        FILTER sum(d.unit_price * d.quantity) sum DESC
        LIMIT 1
    )
    FIND employees e, e sales s
    FETCH e.last_name

## LIMIT

Limit the number of result rows:

    FIND products p FETCH p.product_name LIMIT 10

## Temporal literals

The type is inferred from the format — no keyword prefix. Always use double quotes.

Date (no time, no zone):

    "1970-01-01"

Timestamp (date and time):

    "1970-01-01 00:00:00"
    "1970-01-01 00:00:00.000"
    "1970-01-01 00:00:00.000+02:00"

Time of day:

    "00:00:00"
    "00:00:00.000"
    "00:00:00.000+02:00"

Duration (amount of time — combine units freely):

    30d         // 30 days
    2h30min     // 2 hours 30 minutes
    1y2mo15d    // 1 year, 2 months, 15 days

Use durations in arithmetic: `o.order_date + 30d`, `now() - 1y`.
A bare number never combines with a temporal value: `o.order_date + 30` is a validation error — write `30d`.

## Operators

### EQUAL operator

If EQUAL operator is used on textcolumns, prefer case-insensitive LIKE operator instead, add % at start and end
add lower function to column in compare
Use EQUAL on textcolumns only if user commits using.

### LIKE operator

'_' wildcard for single letter.
'%' wildcard for a sequence of letters.

### BETWEEN operator

Use this syntax for intervals.

    BETWEEN "1970-01-01" AND "1970-12-31"

### Negation

Don't try to negate operators, instead use logical expression:

    NOT o.order_date BETWEEN "1970-01-01" AND "1970-12-31"

## Functions

**kql** supports the following functions:

**Arithmetic operators:** `+`, `-`, `*`, `/`

**Aggregate:** `count`, `sum`, `min`, `max`, `avg`, `string_agg`

**String:** `lower`, `upper`, `substr`, `length`, `concat`, `trim`, `replace`

**Math:** `round`, `abs`, `mod`

**Date/Time:** `now`, `today`, `year`, `month`, `day`,
`month_begin`, `month_end`, `year_begin`, `year_end`,
`day_add`, `month_add`, `year_add`,
`days_between`, `months_between`, `years_between`

**Conditional:** `coalesce`

**Type conversion:** `to_date`, `to_timestamp`, `to_integer`, `to_text`

Do not use functions not in this list.
