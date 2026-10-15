-- JSON whose members the catalog declares (.n INTEGER, .a.b[] INTEGER) needs no cast: the member is
-- a number in comparisons, arithmetic and aggregates. A member that is not declared stays JSON.
SELECT
  t.nr
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) + 1 AS n_plus_one
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[1]'), JSON_VALUE(t.doc, '$.a.b[1]')) END AS INTEGER) * 2 AS second_doubled
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$[0]'), JSON_VALUE(t.doc, '$[0]')) END AS undeclared
FROM
 check_complex t
WHERE
  CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) > 0