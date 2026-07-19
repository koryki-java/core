# Shift Control to Human-Centric Queries

Enable users to validate and reason about queries directly in a simple, readable form aligned with their intent.

Traditional Text-to-SQL systems place the burden of understanding SQL on the user: to trust the results, they must be able to validate SQL.
**koryki** takes a different approach. The critical validation point is shifted from SQL to **KQL** — a compact, understandable, and less error-prone language.
From there, transformation into SQL is deterministic and rule-based.


![reduce uncertainty by introduction of kql](shift_control.svg)


# Reducing Complexity in Data Analysis

**koryki** introduces several layers of abstraction to simplify how users work with data:

- **Business-oriented modeling**
Instead of exposing raw database schemas, koryki presents a semantic layer built on domain-specific terms that users naturally understand.
- **Abstracted relationships**
Entity relationships are labeled and managed internally, removing the need for users to define join conditions.
- **Context-aware simplification**
Queries can omit details that are either obvious to humans, inferred from context, or handled automatically by AI.
- **Hidden technical optimizations**
Database-specific implementation details—such as partitioning, indexing strategies, or precalculated values—are abstracted away from the user.

Each abstraction step is deterministic and systematically processed, ensuring that the resulting queries remain predictable and reliable. This structure allows SQL generation to be handled with precision while keeping user-facing queries simple and transparent.


# Semantic model and dimension analysis

Beyond naming entities and relationships, **koryki** attaches meaning to the columns a query returns.
Each output column is analysed along several dimensions:

- **Column role**
Whether a column is a descriptive dimension or a measured quantity, and how it was aggregated or ordered.
- **Physical quantity and unit**
The kind of value it holds—a currency amount, a count, a ratio, a duration—and its unit, so magnitudes are never mixed up.
- **Temporal grain**
The resolution of a date or time column (day, month, year), which drives correct formatting and ordering.
- **Derived metric**
The business name concluded from an expression's shape—for example, a sum of unit price times quantity recognised as *net revenue* in euros.

This analysis is derived deterministically from the semantic model and the query itself, not guessed from column names, so it stays reliable and explainable even for AI-generated queries. It lets **koryki** apply sensible defaults, flag combinations that make no dimensional sense, and give both users and language models a precise, machine-readable description of what each result represents—the same foundation that later labels and formats charts.


# Grammar of graphics

The principles that make queries readable and verifiable extend to visualisation. Instead of choosing from a fixed catalogue of chart types, **koryki** describes a chart as a composition of independent parts through an optional `VISUALISE` clause—a declarative *Grammar of Graphics*:

- **Composable parts**
Aesthetic mappings (columns to visual channels), marks (point, line, bar, area), scales, the coordinate system, and faceting into small multiples—each changed independently.
- **Deterministic compilation**
The clause compiles to a standard [Vega-Lite](https://vega.github.io/vega-lite/ "(Vega-Lite)") specification, so a chart is as transparent and reproducible as the query behind it.
- **Statistics in the database**
Histograms, box plots, and trend lines are computed in SQL, keeping the client light and results consistent across database engines.
- **Meaning-driven defaults**
The dimension analysis supplies titles from metric names, unit suffixes, grain-based axis formats, and separate scales for measures of different physical dimensions.

A chart is added only when it helps—not every query needs one, and an explicit choice always overrides a default. Together this gives humans and AI a compact, controllable path from a validated query to a validated visualisation.


# AI assistance

A simple and well-defined grammar is essential for making queries easier to create and validate—for both humans and Large Language Models. 

With the help of the koryki.ai MCP–Server, users can gain read access to databases with the support of an AI-model, see
[Model-Context-Protocol](https://modelcontextprotocol.io/docs/getting-started/intro "(MCP)").


![MCP-Server Overview](mcp_overview.png)

See: [demo.koryki.ai](https://demo.koryki.ai "(demo.koryki.ai)").
