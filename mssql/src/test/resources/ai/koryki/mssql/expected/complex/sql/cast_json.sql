-- A JSON member is no number until it is cast: to_integer, to_float and to_text read the scalar at the
-- path, a number as a number. The filter is on the cast, so only the object (row 1) passes; the array
-- of row 3 has no member n, and a cast of nothing is NULL.
SELECT
  t.nr
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) AS n
, CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')), 0) AS INT) + 1 AS inner_plus_one
, CAST(CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) AS VARCHAR(MAX)) AS n_text
, CAST(COALESCE(JSON_QUERY(t.doc, '$.a.b[1]'), JSON_VALUE(t.doc, '$.a.b[1]')) AS FLOAT) AS second
FROM
 check_complex t
WHERE
  CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) > 0