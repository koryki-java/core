![Java](https://img.shields.io/badge/Java-21-blue)
![Gradle](https://img.shields.io/badge/Build-Gradle-02303A)
![Jackson](https://img.shields.io/badge/JSON-Jackson-black)
![ANTLR](https://img.shields.io/badge/Parser-ANTLR-blueviolet)




# koryki-java/core

The [**koryki.ai**](https://koryki.ai "(koryki.ai platform)") platform improves how business users interact with relational databases for search and data analysis.
It reduces complexity while preserving full control over what is queried and executed.

At its core is **KQL** (Koryki Query Language), a concise and human-readable language designed for ease of learning, interpretation, and validation.
A well-defined grammar is key to making queries reliable and verifiable — for both humans and large language models.

The purpose of **koryki** is:
- Shift control to human-centric queries
- Simplify data analysis
- Enhance workflows with AI while keeping full control

→ [Read more](./docs/PURPOSE.md "purpose of the koryki.ai platform"), or see  [sample query](./docs/SAMPLE_QUERY.md "sample query")

A demo application is available at: [demo.koryki.ai](https://demo.koryki.ai "(demo.koryki.ai)").


## Sub Projects

- **core**: the koryki core library
- **duckdb**: DuckDB dialect
- **northwind**: [`Northwind sample database`](./NOTICE "Northwind sample database") for testing purpose

 
## Developer Documentation
- Package [`ai.koryki.antlr`](./docs/ANTLR.md "package ai.koryki.antlr") – Grammar and parsing layer
- Package [`ai.koryki.iql`](./docs/IQL.md "package ai.koryki.iql") – Intermediate representation, **IQL** language, query rewriting rules and validation.
- Package [`ai.koryki.kql`](./docs/KQL.md "package ai.koryki.kql") – **KQL** language, transpiler and engine to retrieve results from databases
- Package [`ai.koryki.jdbc`](./docs/JDBC.md "package ai.koryki.jdbc") – JDBC database access
- Package [`ai.koryki.scaffold`](./docs/SCAFFOLD.md "package ai.koryki.scaffold") – Database schema description and semantic layer

- [`KQL-Grammar definition`](./core/src/main/antlr/kql/KQL.g4 "KQL grammar")


## Contribution

**koryki** is in early stage and open source under
[`Apache 2.0 License`](./LICENSE "Apache 2.0 License")

Any kind of feedback is welcome: info@koryki.ai

