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
| `Engine` | Top-evel runtime façade: transpiles KQL and executes it against a database, passes the result to ResultConsumer |
| `Translator` | Name-translation contract (domain names → DB names, or identity)                                                |


