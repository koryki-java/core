-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST(json_extract_scalar(t.doc, '$.n') AS INTEGER) AS n
, count(t.nr) AS rows_per_n
, sum(CAST(json_extract_scalar(t.doc, '$.a.b[0]') AS INTEGER)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST(json_extract_scalar(t.doc, '$.n') AS INTEGER)