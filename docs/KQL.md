# Package `ai.koryki.kql`

`ai.koryki.kql` implements the **Koryki Query Language (KQL)**, the user-facing and AI-facing query language of the koryki platform. **KQL** is a compact, readable DSL designed to be written without SQL knowledge. It uses **semantic layer** — entity names, relationship (link) names, and attribute names — rather than physical database identifiers.

**KQL** queries are parsed, transformed into the shared `ai.koryki.iql.query.*` bean model, validated, and finally rendered to SQL for execution.


## Main Classes

|Class | Role                                                                                                            |
|---|-----------------------------------------------------------------------------------------------------------------|
| `KQLReader` | ANTLR-based lexer/parser for KQL text                                                                           |
| `KQLQueryMapper` | Maps a KQL parse tree to `ai.koryki.iql.query.*` bean objects                                                   |
| `KQLTranspiler` | Orchestrates the full KQL → SQL pipeline                                                                        |
| `KQLFormatter` | Round-trips a KQL parse tree back to formatted KQL text, with optional name translation                         |
| `Generator` | KQL without a database: validate, format, transpile to SQL, resolve the output columns — what a query generator needs |
| `Engine` | Top-level runtime façade: a `Generator` that also executes against a database, passing the result to a ResultConsumer |
| `Translator` | Name-translation contract (domain names → DB names, or identity)                                                |


