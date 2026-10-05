-- A JSON member is no number until it is cast: to_integer, to_float and to_text read the scalar at the
-- path, a number as a number. The filter is on the cast, so only the object (row 1) passes; the array
-- of row 3 has no member n, and a cast of nothing is NULL.
SELECT
  t.nr
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) AS n
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')) END AS INTEGER) + 1 AS inner_plus_one
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS CHAR) AS n_text
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[1]'), JSON_VALUE(t.doc, '$.a.b[1]')) END AS FLOAT) AS second
FROM
 check_complex t
WHERE
  CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) > 0