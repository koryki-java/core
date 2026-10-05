-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$.n' RETURNING JSON) END AS n
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' THEN JSON_QUERY(t.doc, '$.a.b' RETURNING JSON) END AS inner_list
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.b.type()') = 'array' THEN JSON_QUERY(t.doc, '$.a.b[0]' RETURNING JSON) END AS inner_first
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'array' THEN JSON_QUERY(t.doc, '$[0]' RETURNING JSON) END AS array_first
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'array' THEN JSON_QUERY(t.doc, '$[last]' RETURNING JSON) END AS array_last
FROM
 check_complex t