-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT) AS n
, count(t.nr) AS rows_per_n
, sum(CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]')), 0) AS INT)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST(ROUND(COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')), 0) AS INT)