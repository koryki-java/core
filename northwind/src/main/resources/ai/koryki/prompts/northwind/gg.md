# VISUALISE (Grammar of Graphics) — instructions for LLMs

The `VISUALISE` clause turns a KQL result into a chart (compiled to Vega-Lite). It is **optional**
and is appended **after** the `FETCH` clause. It never changes the returned rows — it only adds a
chart on top of them. Add a chart only when it is meaningful: comparisons, time series,
distributions, shares, correlations, or maps. If the user just asks for data, omit `VISUALISE`.

Everything you write is validated by the compiler — send the finished query to `validateKql` and fix
reported errors. Keywords are UPPERCASE; channel / mark / coordinate / palette / transform names are
lowercase and validated semantically (an unknown name is a validation error, not a crash).

## Golden rules

1. `VISUALISE` maps **FETCH aliases** (the result headers), never raw `table.column`. Give every
   visualised column an alias in `FETCH` first.
2. Clause order is fixed: `VISUALISE <mappings>` then any of `DRAW` / `PLACE` / `SCALE` / `FACET` /
   `PROJECT` / `LABEL`, in that family order. `VISUALISE` comes first and carries the mappings.
3. One `VISUALISE`; usually one `DRAW`. Extra `DRAW`s stack as layers (e.g. points + trend line).
4. Aggregate the data in `FETCH` (`sum(...)`, `count(...)`) OR let the chart aggregate with
   `SETTING aggregate => …` — do not do both for the same value.
5. Prefer few channels. `x` + `y` (+ optional `color`) covers most charts.

## Mappings and channels

Form: `VISUALISE alias AS channel, alias AS channel, …`

Position: `x`, `y` (and secondaries `x2`, `y2`) · Grouping colour: `color`, `fill` · Marks:
`size`, `shape`, `opacity` · Text: `text` · Hover: `tooltip` · Extra grouping: `detail` ·
Radial: `theta`, `radius` · Geographic: `lon`, `lat`, `geometry`.

```
FIND customers c
FETCH c.country country, count(c) amount
VISUALISE country AS x, amount AS y
DRAW bar
```

## DRAW — the mark (chart type)

Plain marks: `point` (scatter) · `line` · `area` · `bar` · `tile` (heatmap cell) · `text` ·
`rule`/`segment` (reference lines) · `path` · `polygon` · `ribbon` · `range`.

Statistical marks — **computed in the database**, then inlined:
`histogram` (map `x` only) · `boxplot` (quartiles per group) · `smooth` (linear-regression trend,
usually layered over `point`) · `density` · `violin`.

Geographic mark: `spatial` (a filled `geoshape`; needs a column mapped to `geometry`).

Layering — points with a trend line:

```
FIND order_details d
FETCH d.unit_price price, d.quantity amount
VISUALISE price AS x, amount AS y
DRAW point
DRAW smooth
```

## SETTING and chart-side aggregation

`DRAW <mark> SETTING name => value, …` passes options to the mark/stat. The most useful is
`aggregate` (values: `count`, `sum`, `avg`, `min`, `max`; all but `count` aggregate the `y` column):

```
FIND customers c FETCH c.country country
VISUALISE country AS x
DRAW bar SETTING aggregate => 'count'
```

## LABEL — titles and axis/legend text

`LABEL title => 'Chart title', x => 'X axis', y => 'Y axis', color => 'Legend'`

```
VISUALISE month AS x, revenue AS y
DRAW line
LABEL title => 'Revenue per month', x => 'Month', y => 'Revenue'
```

## FACET — small multiples

`FACET var [BY var]` draws one sub-chart per value:

```
VISUALISE month AS x, revenue AS y
DRAW line
FACET category
```

## SCALE — axes, domains, palettes

`SCALE [type] <channel> [FROM domain] [TO range|palette] [VIA transform] [SETTING …] [RENAMING …]`

- Transform an axis: `SCALE y VIA log` (also `ln`, `log10`, `log2`, `sqrt`, `square`, `symlog`,
  `pseudo_log`, `asinh`).
- Fix a domain / range: `SCALE y FROM [0, 100]`.
- Colour palette: `SCALE color TO viridis` (name a Vega scheme).
- Scale type: one of `continuous`, `discrete`, `binned`, `ordinal`, `identity`.
- Relabel discrete values: `SCALE color RENAMING 0 => 'No', 1 => 'Yes'`.

```
VISUALISE product AS x, revenue AS y
DRAW bar
SCALE y VIA log
```

## PROJECT — coordinate systems (pie charts and maps)

`PROJECT [aes] TO <coord> [SETTING …]` changes the coordinate system.

- **Pie / donut / radial:** `PROJECT TO polar` turns a bar/area into an arc chart (`y` becomes the
  angle, `x` the radius). Donut hole: `PROJECT TO polar SETTING inner => 0.5`.

```
FIND products p, p categories c
FETCH c.category_name category, count(p) products
VISUALISE category AS color, products AS y
DRAW bar
PROJECT TO polar
LABEL title => 'Products per category'
```

- **Maps:** see the next section. Projection names include `equal_earth`, `mercator`,
  `orthographic`, `albers`, `equirectangular`, `natural`, `robinson`, `mollweide`,
  `azimuthal_equidistant`, `stereographic`, `gnomonic`, `winkel_tripel`.

## Geo-charting (maps)

koryki draws maps **without any database spatial extension** — coordinates and outlines are ordinary
columns. In northwind, the `countries` lookup provides `latitude`, `longitude`, and a GeoJSON
`geometry` string; join it via a country name (`customers`/`orders` → `countries`).

**Point / bubble map** — `lon` + `lat` from the centroid columns, sized by a metric, on a projection:

```
FIND customers k, k countries c
FETCH c.longitude lng, c.latitude lat, count(k.customer_id) customers
VISUALISE lng AS lon, lat AS lat, customers AS size
DRAW point
PROJECT TO equal_earth
```

**Choropleth** — `geometry` from the GeoJSON column, `DRAW spatial`, coloured by a metric via `fill`:

```
FIND orders o, o countries c
FETCH c.country_name country, c.geometry geom, sum(o.freight) freight
VISUALISE geom AS geometry, freight AS fill
DRAW spatial
LABEL title => 'Total freight by destination country'
```

## Advanced (use rarely)

- `PLACE <mark> SETTING …` adds an annotation layer that carries its own data (e.g. a reference line
  or callout) instead of the shared rows.
- `DRAW <mark> MAPPING …` / `REMAPPING …` overrides or adds channel mappings for that one layer, so
  stacked layers can use different columns.

## Chart-type quick reference

- Bars / ranking → `DRAW bar`, category `AS x`, value `AS y`.
- Time series → `DRAW line`, time `AS x`, value `AS y`, series `AS color`.
- Scatter / correlation → `DRAW point` (+ `DRAW smooth` for a trend).
- Distribution of one value → `DRAW histogram` (map `x` only) or `DRAW boxplot` per group.
- Share of a whole → `DRAW bar` + `PROJECT TO polar` (pie).
- Heatmap → `DRAW tile`, two categories `AS x`/`AS y`, metric `AS fill`.
- Point map → `lon`/`lat` + `DRAW point` + `PROJECT TO <projection>`.
- Choropleth → `geometry` + `DRAW spatial`, metric `AS fill`.

## Common mistakes to avoid

- Mapping a raw column instead of a FETCH alias → always alias in `FETCH` first.
- Mapping a channel to a column that is not in `FETCH`.
- Using `aggregate` in `SETTING` while the value is already aggregated in `FETCH` (double counting).
- Inventing mark/channel/projection names — use only the names listed above.
- Adding a chart when the user only wanted the data.
