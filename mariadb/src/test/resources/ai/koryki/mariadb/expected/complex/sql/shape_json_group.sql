-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) AS n
, count(t.nr) AS rows_per_n
, sum(CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')) END AS INTEGER)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER)