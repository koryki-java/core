-- JSON whose members the catalog declares (.n INTEGER, .a.b[] INTEGER) needs no cast: the member is
-- a number in comparisons, arithmetic and aggregates. A member that is not declared stays JSON.
SELECT
  t.nr
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) + 1 AS n_plus_one
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.a.b[1]'), JSON_VALUE(t.doc, '$.a.b[1]')), 0) AS INT) * 2 AS second_doubled
, COALESCE(JSON_QUERY(t.doc, '$[0]'), JSON_VALUE(t.doc, '$[0]')) AS undeclared
FROM
 check_complex t
WHERE
  CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) > 0