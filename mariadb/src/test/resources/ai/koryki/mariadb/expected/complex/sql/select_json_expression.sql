-- A JSON value that is an expression, not a column, is evaluated once where the path asks for it
-- more than once.
SELECT
  t.nr
, CASE WHEN JSON_TYPE(JSON_QUERY(coalesce(t.doc, t.doc), '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(coalesce(t.doc, t.doc), '$.n'), JSON_VALUE(coalesce(t.doc, t.doc), '$.n')) END AS n
, CASE WHEN JSON_TYPE(JSON_QUERY(coalesce(t.doc, t.doc), '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(coalesce(t.doc, t.doc), '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(coalesce(t.doc, t.doc), '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(coalesce(t.doc, t.doc), '$.a.b[0]'), JSON_VALUE(coalesce(t.doc, t.doc), '$.a.b[0]')) END AS inner_first
FROM
 check_complex t