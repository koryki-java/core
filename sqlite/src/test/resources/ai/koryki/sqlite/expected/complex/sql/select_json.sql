-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
SELECT
  t.nr
, json_extract(t.doc, '$.n') AS n
, json_extract(t.doc, '$.a.b') AS inner_list
, json_extract(t.doc, '$.a.b[0]') AS inner_first
, json_extract(t.doc, '$[0]') AS array_first
, json_extract(t.doc, '$[#-1]') AS array_last
FROM
 check_complex t