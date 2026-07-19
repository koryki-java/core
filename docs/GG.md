# Grammar of Graphics in koryki

koryki turns a data query into a chart with a **`VISUALISE`** clause — a declarative
*Grammar of Graphics* (GG) appended to a KQL query and compiled to a
[Vega-Lite v6](https://vega.github.io/vega-lite/) specification. This document explains
the Grammar of Graphics and exactly how koryki implements it.

The `VISUALISE` clause is inspired by [**ggsql**](https://github.com/posit-dev/ggsql); the
grammar, compiler, and semantics here are an independent Java/ANTLR implementation.

---

## 1. What is the Grammar of Graphics?

The Grammar of Graphics (Wilkinson, 2005; popularised by ggplot2 and Vega-Lite) says a chart
is not a *chart type* ("bar chart", "pie chart") but a **composition of independent parts**.
Change one part and you get a different chart from the same data:

### How koryki adopts it

A KQL query already defines the **data** (`FIND … FILTER … FETCH …`). The `VISUALISE` clause
adds the graphics layer on top of the *same* query. It is optional — not every result has a
meaningful chart.

```
FIND orders o, o order_details od, od products p, p categories c
FETCH c.category_name category, month(o.order_date) month,
      sum(od.unit_price * od.quantity) revenue
VISUALISE month AS x, revenue AS y, category AS color   -- aesthetic mappings
DRAW line                                                -- mark / geom
LABEL title => 'Revenue per category and month'          -- guides
```

Each GG component maps to one keyword:

| GG component | KQL keyword | Compiles to (Vega-Lite) |
|---|---|---|
| aesthetic mapping | `VISUALISE col AS channel`, per-layer `MAPPING`/`REMAPPING` | `encoding` |
| mark / geom | `DRAW <geom>` (layers stack) | `mark` / `layer[]` |
| annotation | `PLACE <geom> SETTING …` | own-data annotation layer |
| statistical transform | `DRAW histogram`/`boxplot`/`smooth`/… or `SETTING aggregate => …` | data computed **in the DB** |
| scale | `SCALE [type] channel FROM … TO … VIA … RENAMING …` | `encoding.<ch>.scale`/`axis`/`legend` |
| coordinate system | `PROJECT [aes] TO <coord> SETTING …` | axis swap / `arc` / `projection` |
| facet | `FACET vars [BY vars] SETTING …` | `facet` + `resolve` |
| guides / labels | `LABEL target => 'text'` | `title` / `axis.title` / `legend.title` |

Convention (shared with the rest of KQL): **keywords are UPPERCASE**, everything else —
channels, geoms, coordinate names, palettes, transforms — is lowercase `ID`, validated
semantically by the compiler rather than reserved in the grammar.

---

### The compilation pipeline

```
KQL text
  │  ANTLR grammar  (core/src/main/antlr/kql/KQL.g4 — visualiseClause)
  ▼
Parse tree
  │  KQLQueryMapper.toVisualise(...)
  ▼
Visualise AST            (ai.koryki.iql.query.viz.*)
  │                        Visualise · Layer · Mapping · ScaleSpec ·
  │                        FacetSpec · Projection · Label · Rename
  ▼
VegaLite emitter         (ai.koryki.viz.VegaLite)          ── statistical geoms ──►  StatTransform
  │  builds a Jackson ObjectNode tree                          (ai.koryki.viz.stat.*)
  ▼                                                            computes bins / quartiles /
Vega-Lite v6 JSON                                              regression / KDE **as SQL**,
                                                               runs it, inlines the result
```

---

## Geo-charting

koryki renders two kinds of maps from the same grammar, with **no database spatial extension**.
A **point (symbol) map** binds longitude/latitude columns to the `lon`/`lat` channels and picks a
cartographic coordinate system with `PROJECT TO <projection>` (`equal_earth`, `orthographic`, …);
koryki resolves the projection name to a Vega-Lite/d3 `projection` and leaves the spherical math to
the client. A **choropleth** binds a GeoJSON string column to the `geometry` channel under
`DRAW spatial` (a `geoshape` mark), and the region outlines are inlined straight into the compiled
Vega-Lite spec. Because the *same* KQL is transpiled to every SQL dialect koryki targets, geography
travels as backend-neutral data — numeric centroids and GeoJSON-as-text (see the northwind
`countries` lookup) — rather than native spatial types, so an identical map query runs on SQLite,
Oracle or Snowflake with no `ST_*` functions or PostGIS.

**ggsql vs koryki.** Posit's [ggsql](https://github.com/posit-dev/ggsql) shows how the Grammar of
Graphics can be expressed in SQL syntax; koryki carries that idea in a different direction — an
independent Java/ANTLR implementation whose `VISUALISE` grammar sits on top of KQL's schema-aware
semantic layer and compiles to Vega-Lite across every supported dialect. For maps that difference is
deliberate: koryki favours dialect-portability — client-side projection over engine-specific
geospatial operators — so one chart definition works the same on every backend.

