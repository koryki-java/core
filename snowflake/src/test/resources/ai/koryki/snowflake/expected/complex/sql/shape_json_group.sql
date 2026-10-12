-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) AS n
, count(t.nr) AS rows_per_n
, sum(CAST((GET_PATH(t.doc, 'a.b[0]'))::STRING AS INTEGER)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER)