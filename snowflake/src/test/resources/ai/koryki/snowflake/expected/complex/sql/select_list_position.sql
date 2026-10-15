-- A position selects one element of a list: @1 the first, @-1 the last. Positions count from 1, as
-- in a list of DuckDB; a position outside the list, and any position of an empty list, is NULL.
SELECT
  t.nr
, GET(t.tags, 0) AS first_tag
, CASE WHEN ARRAY_SIZE(t.tags) >= 1 THEN GET(t.tags, ARRAY_SIZE(t.tags) - 1) END AS last_tag
, GET(t.tags, 4) AS fifth_tag
, GET(t.scores, 1) AS second_score
FROM
 check_complex t