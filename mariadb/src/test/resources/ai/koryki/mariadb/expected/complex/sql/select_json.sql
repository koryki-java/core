-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
SELECT
  t.nr
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) AS n
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b'), JSON_VALUE(t.doc, '$.a.b')) END AS inner_list
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')) END AS INTEGER) AS inner_first
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$[0]'), JSON_VALUE(t.doc, '$[0]')) END AS array_first
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$[last]'), JSON_VALUE(t.doc, '$[last]')) END AS array_last
FROM
 check_complex t