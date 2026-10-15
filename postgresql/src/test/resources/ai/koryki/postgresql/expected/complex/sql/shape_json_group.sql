-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST((t.doc ->> 'n') AS INTEGER) AS n
, count(t.nr) AS rows_per_n
, sum(CAST((t.doc -> 'a' -> 'b' ->> 0) AS INTEGER)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST((t.doc ->> 'n') AS INTEGER)