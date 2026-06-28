Conceptual map

| Dimension                      | KQL                       | LookML                | DAX                   | Tableau LOD                 | SAQL                | MDX                     |
|--------------------------------|---------------------------|-----------------------|-----------------------|-----------------------------|---------------------|-------------------------|
| Query style                    | declarative text          | model definition      | functional formula    | field expression            | imperative pipeline | multidimensional cursor |
| Data model                     | relational (semantic)     | semantic (definition) | columnar (tabular)    | relational/flat             | flat dataset        | OLAP cube               |
| Relationships                  | auto-resolved             | pre-declared          | implicit via context  | pre-joined                  | none                | dimension hierarchies   |
| Aggregation control            | explicit GROUP BY implied | measure definition    | filter context        | FIXED/INCLUDE/EXCLUDE level | group step          | slice/member            |
| Full query expressible as text | yes                       | no (model only)       | no (measure only)     | no (expression only)        | yes                 | yes                     |
| Multi-entity traversal         | yes                       | yes (via explores)    | via RELATEDTABLE      | limited                     | no                  | limited (role-playing)  |
| AI / LLM writable              | yes (design goal)         | not targeted          | hard (context rules)  | not applicable              | partially           | very hard               |
| Transpiles to SQL              | yes                       | yes (via Looker)      | no (in-memory engine) | yes (via Hyper)             | no                  | no                      |
