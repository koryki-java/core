-- Pattern-matching chapter: regexp_like maps to DuckDB regexp_matches,
-- regexp_count stays under its canonical name.
SELECT
  c.company_name
, regexp_matches(c.company_name, '^A')
FROM
 customers c
WHERE
  regexp_count(c.company_name, 'a') > 1