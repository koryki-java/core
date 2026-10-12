-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
SELECT
  t.nr
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER) AS n
, CASE WHEN JSON_VALUE(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' THEN JSON_QUERY(t.doc, '$.a.b' RETURNING JSON) END, '$.type()') = 'array' THEN CAST(MULTISET(SELECT jt.v FROM JSON_TABLE(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' THEN JSON_QUERY(t.doc, '$.a.b' RETURNING JSON) END, '$[*]' COLUMNS (ord FOR ORDINALITY, v NUMBER PATH '$')) jt ORDER BY jt.ord) AS SYS.ODCINUMBERLIST) END AS inner_list
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.b.type()') = 'array' THEN JSON_VALUE(t.doc, '$.a.b[0]') END AS INTEGER) AS inner_first
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'array' THEN JSON_QUERY(t.doc, '$[0]' RETURNING JSON) END AS array_first
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'array' THEN JSON_QUERY(t.doc, '$[last]' RETURNING JSON) END AS array_last
FROM
 check_complex t