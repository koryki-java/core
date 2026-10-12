-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
-- (A JSON path in SQL Server has no position from the end, so there is no @-1 here.)
SELECT
  t.nr
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) AS n
, COALESCE(JSON_QUERY(t.doc, '$.a.b'), JSON_VALUE(t.doc, '$.a.b')) AS inner_list
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')), 0) AS INT) AS inner_first
, COALESCE(JSON_QUERY(t.doc, '$[0]'), JSON_VALUE(t.doc, '$[0]')) AS array_first
FROM
 check_complex t