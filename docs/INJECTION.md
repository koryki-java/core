---
menuTitle: "SQL Injection"
parent: "REFERENCE"
order: 5
---

# SQL Injection

This document is **normative**: it names every way author-controlled text can reach the SQL this
library emits, the barrier that stops it at each one, and the rules a change has to follow to keep
that true. `SECURITY.md` states the policy — *"a query, catalog or identifier that makes the
transpiler emit SQL doing something the query did not ask for … is the worst thing this project can
get wrong"* — this is how that policy is kept.

## Why this is the hard problem here

The usual answer to SQL injection is not available to koryki.

```
KQL text ─┐
          ├─→ parse ─→ IQL bean model ─→ rewrite rules ─→ validate ─→ render ─→ SQL string
IQL text ─┘                  ▲                                          │
                             │                                          ▼
                      catalog (db.json, model.json)          prepareStatement(sql)
```

`JdbcDatabase` hands the rendered statement to `Connection.prepareStatement(sql)` and **binds
nothing**. There is no `?` in anything the transpiler emits, because a KQL literal *is* a literal in
the SQL — a value written into the statement, not a parameter supplied beside it. That is a design
choice, not an oversight: the rendered SQL is meant to be read, diffed and kept as a golden, and a
statement full of `?` says nothing about what it will do.

The consequence is that **the escaping in the renderers is the entire boundary**. There is no second
line of defence behind it, so every path to the statement has to be named and every one has to have
a barrier.

> **The one rule.** Author-supplied text reaches the SQL *only* as the body of a string literal or
> the body of a quoted identifier — never as SQL itself. Everything below is that rule, applied.

### Trust model

| Input | Supplied by | Trusted? |
|---|---|---|
| KQL query text | the end user, or an LLM writing on their behalf | **no** |
| IQL query text | whatever produced it — often the same source | **no** |
| `db.json` / `model.json` | the application, but possibly generated, uploaded or introspected | **no** |
| dialect function catalog | this repository | yes (it is code) |
| the database account's grant | the application | out of scope — see below |

The rights of the account handed to this library are explicitly **out of scope**: a transpiled query
runs with exactly those, and no library can lower them. Grant the account only what the queries
need. That is a control the application owns, and it is the one that decides what a successful
injection would be worth.

## Injection paths

Five places take text from outside and put it into a statement. Four are real paths with real
barriers; the fifth section lists what looks like a path and is not, and says why — because "this
one is safe" is a claim that has to be checkable, not remembered.

### 1. Values — KQL and IQL string literals

The path everyone thinks of first, and the one with the most traffic.

```
FILTER c.company_name = 'O\'Brien'      →      WHERE c.company_name = 'O''Brien'
```

Both front doors define the token identically, and this is deliberate — one escape convention, one
renderer, one set of tests:

```antlr
SQ_STRING : SINGLE_QUOTE ('\\\'' | .)*? SINGLE_QUOTE ;   // KQLTokens.g4 and IQL.g4
```

The mapper keeps the token **with its delimiters** (`KQLQueryMapper.toExpression`,
`IQLQueryMapper.toExpression`), and `SqlSelectRenderer.toSqlUnparenthesized` converts KQL's
backslash escape into SQL's doubled quote before handing the result to `SqlDialect.textLiteral`.

**Barrier:** the lexer (B1) and the literal escaping (B2), in that order. Both matter — see below.

### 2. Identifiers — the catalog

An entity's `table` and an attribute's `column` are *physical* names, written into the statement as
identifiers. They cannot be literals and they cannot be parameters. A catalog is a JSON file, and an
application that generates one from an introspection run, accepts one per tenant, or lets a
scaffolding tool write one is handing this library names it did not choose.

```
"table": "customers\"; DROP TABLE orders; --"
                                              →   FROM "customers""; DROP TABLE orders; --"
```

The name appears in four positions, and each is reached by its own code path:

| Position | Renderer |
|---|---|
| the table and its alias in `FROM` and the join tree | `SqlSelectRenderer.toSql(Source, int)` |
| a column in the projection, `WHERE`, `GROUP BY`, `ORDER BY` | `SqlSelectRenderer.toSql(Field, int)` |
| an output header — `… AS h` | `SqlSelectRenderer.toSql(Out, int)` |
| the block name and its column list — `WITH b (…)` | `SqlQueryRenderer.toHeader` |

The CTE column list is the reason this list is written out rather than summarised. It is documented
in `SqlQueryRenderer` as *"the only identifier position that never reached `normal`"*: for a while it
emitted a physical column name raw while the same name inside the block body was quoted correctly.
One position that forgets is all an injection needs.

**Barrier:** identifier quoting (B3).

### 3. Function arguments a dialect *reads back*

This is the path that is easy to miss, and the one that actually leaked.

Almost every function is declared as a `SqlTemplate` — `"strpos({0}, {1})"` — and a template only
splices an **already rendered** operand into a fixed string. An operand that is a string literal is
a quoted, escaped literal by the time the template sees it. There is nothing to get wrong.

The exceptions are the functions whose dialect rendering *inspects* an argument: it strips the
quotes off the rendered literal, reads the text inside, and builds SQL from it. **That unquoting is
the dangerous moment** — what comes out is no longer inside a literal, and if it is written into the
statement as-is, every character of the author's string is SQL.

The sites, as of this writing:

| Site | Reads | Disposition |
|---|---|---|
| `DuckdbBaseDialect.toIntervalUnit` | `to_interval` unit | **was the hole** — now shape-checked |
| `OracleDialect.oracleToIntervalUnit` | `to_interval` unit | safe: the fall-through re-uses the *quoted* `unit` |
| `TimeEncodings.intervalUnitSeconds` | `to_interval` unit | safe: closed switch, `default -> throw` |
| `date_trunc` on MariaDB, SQL Server, Oracle, SQLite | unit | safe: closed switch, `default -> throw` |
| `FormatMask.translate` (DuckDB, SQLite, MariaDB, Trino) | `to_char` / `parse_*` mask | safe: re-quoted on the way out |
| `MssqlDialect.compileMask` | `to_char` mask | safe: each literal run is re-quoted |
| `MssqlDialect.unquote` | zone name | safe: the text goes to `ZoneId.of`, and what is emitted is the parsed zone |

**Barrier:** the shape of the value, checked by the dialect that reads it (B4). There is no generic
one, which is exactly why this path needs a rule (see *Hardening*, R2).

### 4. The query description — a comment, not a value

A query may open with a comment. That comment becomes `Query.description`
(`AbstractReader.getComment`) and is written back out as a leading `--` line by `SqlQueryRenderer`,
so the generated SQL says what it came from. `IQLSerializer` does the same with `//`.

No value is involved, so nothing about escaping values helps — and the payload lands **in front of**
the query rather than inside it, which makes it the most powerful of the four: it is not limited to
bending a filter.

```
/* note<CR>SELECT 42 AS injected -- */ FIND customers c FETCH c.customer_id
```

**Barrier:** comment marking that covers every line break, not just `\n` (B6).

### 5. What is *not* a path, and why

Each of these carries author text and none of it reaches the statement as SQL. Verified against the
transpiler, not assumed: where a row shows rendered SQL, that is what it actually emits.

| Input | Why it is not a path |
|---|---|
| numbers | parsed to `BigInteger` / `BigDecimal` and **re-rendered** by `Literals.number`; `0009` comes out as `9` |
| `DATE` / `TIME` / `TIMESTAMP` literals | grammar-constrained tokens, parsed to `java.time`, re-rendered (`DATE '2020-01-01'`) |
| durations | `DURATION : (DIGIT+ ('ms'\|'s'\|'min'\|'h'\|'d'\|'w'\|'mo'\|'q'\|'y'))+` → components → `INTERVAL '2 day 4 hour'` |
| `LIMIT` | `Integer.parseInt`, rendered as an `int` |
| window frame bounds | built from `Limit.PRECEDING(i)` and friends — keywords and an `int` |
| `FETCH … h` headers | `h=ID`, i.e. `[a-z_][a-z0-9_]*`, and quoted anyway by `renderIdentifier` |
| `FETCH … h "label"` labels | held on `Out.label` for the result heading and the IQL round-trip; **never rendered into SQL** |
| placeholders `#name` | `PlaceholderValidator` refuses a query that still has one — the pipeline stops before rendering |
| custom operators | `custom=ID`, so the alphabet is `[a-z_][a-z0-9_]*`; no payload fits in it |
| result output (CSV, XML) | `XMLStreamWriter.writeCharacters` escapes; CSV quotes — a different threat model, not this one |

## Barriers

Six mechanisms, each with a defined job and a defined limit. A barrier that is credited with more
than it does is worse than no barrier.

| # | Barrier | Where | Stops | Does **not** stop |
|---|---|---|---|---|
| B1 | Lexer literal rule | `KQLTokens.g4`, `IQL.g4` | a bare `'` inside a literal | anything after the literal is parsed |
| B2 | Literal escaping | `SqlSelectRenderer`, `SqlDialect.textLiteral` | quote break-out in a value | text that never becomes a literal |
| B3 | Identifier quoting | `SqlDialect.renderIdentifier` / `quote` | catalog and alias break-out | a position that forgets to call it |
| B4 | Validation | `FunctionValidator`, `PlaceholderValidator` | wrong arity, wrong family, unbound placeholders | a *value* the family accepts |
| B5 | Template rendering | `SqlTemplate` | re-expansion of filled operands | a dialect that bypasses the template |
| B6 | Comment marking | `Text.lineComment` | a description escaping its comment | nothing else; it has one job |

### B1 — the lexer refuses, it does not escape

`SQ_STRING` is non-greedy and ends at the first quote that is not `\'`. A payload written as
`'x' OR 1=1 --'` is therefore not a literal with a quote in it — it is a literal, then junk, and the
parser rejects the query. **Refusing is better than escaping**: an author who wrote a stray quote
gets a syntax error pointing at it, and the renderer never has to be clever.

This also means an SQL-style `''` is *not* a KQL escape. The two conventions do not meet until the
renderer, which is where the translation is made once.

### B2 — one escape, applied once, adjusted per dialect

`SqlSelectRenderer` turns `\'` into `''`, the one escape all eight engines share. Doubling alone is
not enough everywhere, though, and that is why `SqlDialect.textLiteral` exists. Three dialects
override it:

- **MariaDB / MySQL** and **Snowflake** read `\` as an escape *inside a string literal*, so
  `MariadbDialect.textLiteral` and `SnowflakeDialect.textLiteral` double every backslash. Without
  that, the KQL `'x\\' OR 1=1 -- '` — a perfectly ordinary literal whose value is
  `x\' OR 1=1 -- ` — renders as `'x\'' OR 1=1 -- '`, and those two engines read the `\'` as an
  escaped quote, take the next `'` as the closing one, and execute `OR 1=1`. The other six read the
  backslash as an ordinary character, so the same text is one literal there. This is the clearest
  case in the project of a defence that has to be **per engine**: one escape convention is not
  enough, and a dialect that forgets this has an injection, not a formatting bug.
- **SQL Server** prefixes a non-ASCII literal with `N`, which is a collation matter, not a security
  one.

### B3 — quoting is a dialect property, doubling is not

`renderIdentifier` is the single place that decides. It quotes when `Identifier.needsQuoting` (the
name is outside `[A-Za-z_][A-Za-z0-9_]*`, or is not all lower case) or when the dialect's
`isReserved` says so, and the quoting itself is `SqlDialect.quote`:

| Dialect | Delimiter | Escape |
|---|---|---|
| default (7 of 8) | double quote | doubled double quote |
| MariaDB | backtick | doubled backtick |
| SQL Server | `[` … `]` | doubled `]` |

**Doubling the delimiter is the invariant** — never a backslash, which is not an identifier escape on
any engine here. A dialect that overrides `quote` has to keep it.

Note what B3 does not do: it cannot help a caller that builds `alias + "." + column` without going
through it. Qualified names are assembled from two `renderIdentifier` calls for exactly that reason.

### B4 — the validator checks types, not values

`FunctionValidator` enforces arity and argument *family* — `to_interval(value, unit)` requires
`NUMERIC` and `TEXT`. It does not, and should not, know which text is a legal interval unit: that is
the dialect's knowledge. So B4 narrows the path but never closes it, and a dialect that reads a value
back out cannot lean on it.

### B5 — a template never rescans what it filled in

`SqlTemplate` parses its template once into `Literal` / `Arg` / `Rest` segments and appends. A filled
operand is never re-examined, so a KQL literal `'{1}'` stays an operand instead of being replaced by
the value of another argument. The naive implementation — a chain of `String.replace("{0}", …)` —
has precisely that bug.

### B6 — every line break, not just `\n`

`Text.lineComment(marker, description)` splits on the regex `\R`, which is every line boundary Java
knows: CR, LF, CRLF, NEL and the two Unicode separators. The SQL standard ends a simple comment at a
*newline*, without fixing which characters spell one; DuckDB, measured, ends one at a bare CR.
Splitting on `\n` alone therefore leaves a hole on any engine that agrees with DuckDB, and a comment
whose safety depends on an engine *not* treating CR as a newline is not a defence worth keeping.
Splitting on `\R` closes the question for all of them, and costs at most a line break a description
did not ask for.

## Where barriers have failed

Two defects, both found in the September 2026 review, both fixed, both with a test that goes red
when the fix is reverted. They are recorded because the *shape* of each is more useful than the fix.

### `to_interval(n, unit)` — an unquoted read-back (DuckDB family)

`DuckdbBaseDialect.toIntervalUnit` stripped the quotes off the rendered unit literal and, for a unit
its switch did not recognise, wrote the body into the SQL **unquoted**:

```java
default -> "INTERVAL (" + value + ") " + bare;   // bare = the author's string, unquoted
```

So this KQL

```
FIND orders o FETCH to_interval(1, 'DAY, (SELECT company_name FROM customers LIMIT 1) AS leaked')
```

rendered — and DuckDB executed —

```sql
SELECT
  INTERVAL (1) DAY, (SELECT COMPANY_NAME FROM CUSTOMERS LIMIT 1) AS LEAKED
FROM
 orders o
```

an output column the query never asked for, read from a table it never named.

**Shape:** a read-back with an open `default`. The fix requires the unit to match `[A-Za-z]+` — a
word carries no quote, paren, comma, semicolon or comment marker, so whatever the engine makes of an
unknown unit it stays one token in the position the renderer put it. Deliberately *not* an allow-list
of known units: the pass-through for units DuckDB knows and the switch does not (`DECADE`,
`CENTURY`) is a feature, and narrowing it would be a behaviour change wearing a security fix's
clothes.

### A carriage return in the description — a comment that ended early

`SqlQueryRenderer` prefixed `--` after every `\n`. A KQL block comment accepts any character its
`.*?` matches, a bare CR included. Measured on DuckDB, `--note\rSELECT 42 AS injected` answers one
column, named `injected` — the comment ended at the CR and the rest was a statement. (Only DuckDB
was measured; see B6 for why the other seven do not need to be.)

**Shape:** a defence written against one spelling of a concept that has several. The fix is
`Text.lineComment`, shared by the SQL renderer and the IQL serializer so the two cannot drift.

### A trailing backslash — a literal the renderer left open

The lexer and the renderer disagreed about one character. `SQ_STRING` prefers the escape reading of
`\'`, but only where that reading yields a token at all; where it does not, the backslash is an
ordinary character and the quote after it closes the literal. So the KQL `'a\'` is a literal whose
value is `a\`. The renderer, which ran `replace("\\'", "''")` over the token *including its
delimiters*, read the same two characters as an escape and emitted

```sql
WHERE c.company_name = 'a''
```

— a literal with no terminator, which swallows whatever was rendered after it.

**Shape:** two readings of one escape, in two places, neither aware of the other. The fix is
`Literals.text`: strip the delimiters first, unescape the body, escape it again for SQL, put the
delimiters back — so neither step can mistake a delimiter for content.

No exploit was demonstrated for it. The shapes that would place attacker text after the open
literal do not survive KQL lexing, because the same backtracking that creates the bug consumes the
second literal differently. It is recorded as a defect rather than a vulnerability: a valid query
rendering SQL that does not parse is wrong on its own, and an unterminated literal is the shape an
escaped-wrongly value takes before it is anything worse.

**Found by generated input**, not by a case anyone wrote — see R4.

### Still open, and not an injection

`MssqlDialect.compileMask` re-escapes a literal run that `FormatMask.body` already returned escaped,
so a quote inside a `to_char` mask becomes `''''` and renders as two quotes instead of one. A
correctness defect, not a break-out: the literal stays balanced.

## Hardening strategy

### R1 — one place decides, and the callers cannot opt out

Every identifier goes through `renderIdentifier`; every literal through `textLiteral`; every
description through `lineComment`. The CTE column list is on record as what happens otherwise: the
same fix applied twice, and the third call site never found. When a second caller appears, it should
inherit the decision rather than have to remember it.

**Applied:** a helper that both quotes *and* renders, never one that returns something a caller is
trusted to quote afterwards.

### R2 — never un-escape into the statement

This is the rule the `to_interval` defect was missing, and it is the one to check first in any
review:

> If a dialect takes the quotes off a rendered literal, the text it gets out may **only** be
> compared, never emitted. If something derived from it has to be emitted, it is either a constant
> the dialect chose, or it is re-quoted.

Concretely, a read-back switch has exactly three legal endings:

```java
default -> throw new …;                              // reject: date_trunc, intervalUnitSeconds
default -> "F(" + value + ", " + unit + ")";         // re-quote: Oracle's to_interval
default -> { if (!SHAPE.matcher(bare).matches()) throw …; yield …; }   // shape-check: DuckDB's to_interval
```

An ending that concatenates `bare` without one of those three is a finding.

### R3 — grep for the shape, not for the bug

The read-back sites are findable mechanically, which is what makes this auditable rather than
folkloric. Run this over `core` and the dialect modules after any function work:

```
grep -rn --include='*.java' \
     "startsWith(\"'\")\|substring(1, .*length() - 1)\|replace(\"'\", \"\")" \
     */src/main/java
```

It finds "something is taking a delimiter off", which is wider than path 3 — `Identifier.bare`
strips identifier quotes, the two query mappers strip a label's, `SqlDialect.unwrapOuterParens`
strips parentheses, and none of those is a literal read-back. Sort the hits into the two kinds and
check the literal ones against R2. The table in *path 3* above is the current census; keep it
current.

### R4 — tests assert on the skeleton, not on goldens

`core/src/test/java/ai/koryki/security/SqlSkeleton` empties every string literal and quoted
identifier out of the rendered SQL and leaves the statement's structure. Assertions are made against
that:

> **No part of an author-supplied string may appear outside the literal it was written in.**

This matters more than the extra machinery suggests. A `contains("''")` assertion says the escaping
fired *somewhere*; it does not say the payload stayed put, and it goes on passing when a second,
unescaped copy leaks out elsewhere. The skeleton catches both that and the opposite failure — an
unterminated literal, which is the classic injection signature — and it does not move when a
renderer change keeps the meaning and shifts the text, so it reads as a policy rather than a golden.

Current coverage:

| Test | Module | Path |
|---|---|---|
| `security/LiteralInjectionTest` | core | 1 — `=`, `IN`, `LIKE`, projection, the backslash-before-escape shape, and B1's refusals |
| `security/CatalogIdentifierInjectionTest` | core | 2 — hostile names in all four positions; `quote` and `needsQuoting` as rules |
| `security/FunctionArgumentInjectionTest` | core | 3 — `to_interval`, format masks, `SqlTemplate`, the custom-operator alphabet |
| `security/CommentInjectionTest` | core | 4 — CR, LF, CRLF, and `lineComment` itself |
| `HostileLiteralTest` | tools | 1 and 3, **for all eight dialects**, each read with its own literal syntax |
| `HostileIdentifierTest` | tools | 2, for all eight dialects — spaces, keywords, embedded quotes, the CTE list |
| `SqlInjectionEngineTest` | duckdb | all four, against a running engine |
| `HostileNameSnowflakeTest`, `MixedCaseNameOracleTest` | snowflake, oracle | 2, against live engines |

The two `tools` tests are the cross-dialect layer, and they are there because `core` cannot see the
dialect modules — it is their dependency, not the other way round. `tools` already depends on
seven of the eight for its documentation generators, which makes it the one place a per-dialect
invariant can be stated once instead of eight times.

`HostileLiteralTest` deserves a note on how it asserts. Doubling the quote is the escape all eight
engines accept, but they do not agree on what *else* is an escape inside a literal, so one shared
expectation would be meaningless: the same rendered bytes are one literal on six engines and two on
MariaDB and Snowflake. The test therefore carries a per-dialect reading of the literal syntax and
empties the statement using *that*. Verified to bite: with `MariadbDialect.textLiteral` and
`SnowflakeDialect.textLiteral` reduced to returning their argument, it fails with

```
snowflake: unterminated string literal from offset 75 — this SQL does not parse:
  c.company_name = 'x\'' OR 1=1 -- '
```

which is the injection itself, not a proxy for it.

The engine test is what closes the argument. `core` asserts what the transpiler *writes*, which
takes on faith that a database reads it the way the test does. `SqlInjectionEngineTest` runs the
payloads on DuckDB, on its own temp copy of the Northwind file, and rests each case on a distinction
an injection either crosses or does not: a tautology that stays data matches **0** rows where a
landed one matches all **91**; a `DROP` payload leaves the table standing.

### R5 — the checklist for a new dialect or function

A new dialect, or a new function on an existing one, is the moment a barrier gets forgotten. Before
merging:

1. Does any rendering **read a literal back**? If so, does it end in one of R2's three forms?
2. Does `quote` double the delimiter, and is the delimiter the engine's own?
3. Does `textLiteral` account for the engine's escape characters — a backslash in particular?
4. Does `isReserved` add what this engine reserves and nothing it does not? (A word quoted
   unnecessarily resolves differently on Oracle and Snowflake — over-quoting is not free.)
5. Is every identifier emitted through `renderIdentifier`, including alias and column-list
   positions?
6. Does the dialect's function catalog declare argument families, so B4 narrows what reaches the
   renderer?

### Open work

| | Work | Why |
|---|---|---|
| 1 | ~~Extend the suite across the remaining seven dialects~~ — **done**, by `tools/HostileLiteralTest` alongside the `HostileIdentifierTest` that already covered identifiers | — |
| 2 | Validate the catalog at load time | `CatalogLoader` accepts any name. Quoting makes a hostile name harmless, but a name that could never have been created in the database is more likely a sign of tampering than a real column, and `LinkResolver` is the place to say so. |
| 3 | ~~Fuzz the front doors~~ — **done** for the literal and description paths (`HostileLiteralTest`), and it found the trailing-backslash defect above on its first run | IQL's front door is still only covered through the shapes KQL can express. |
| 4 | Consider emitting bind parameters | The largest structural change available, and not obviously the right one: it would remove path 1 outright, and cost the readable, diffable, golden-able SQL the project is built around. If it is ever taken, it is taken as a rendering *mode*, so the goldens keep their literals. |
| 5 | Keep the read-back census current (R3) | It is a table in a document, which is the weakest kind of guarantee. A test that fails when a new read-back site appears would be better. |

## Reporting a suspected injection

Privately, through GitHub's private vulnerability reporting — see `SECURITY.md`. The most useful
report names the KQL or catalog that reproduces it and the SQL it rendered.
