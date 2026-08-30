-- Pattern-matching chapter: regexp_like maps to DuckDB regexp_matches,
-- regexp_count to len(regexp_extract_all) (DuckDB has no regexp_count);
-- the optional 3rd start argument becomes a substr on the input.
SELECT
  c.company_name
, regexp_matches(c.company_name, '^A')
, len(regexp_extract_all(substr(c.company_name, 3), 'a')) AS tail_count
FROM
 customers c
WHERE
  len(regexp_extract_all(c.company_name, 'a')) > 1