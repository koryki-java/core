-- A declared JSON member is a grouping key and a measure like any column.
SELECT
  CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER) AS n
, count(t.nr) AS rows_per_n
, sum(CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.b.type()') = 'array' THEN JSON_VALUE(t.doc, '$.a.b[0]') END AS INTEGER)) AS total_first
FROM
 check_complex t
GROUP BY
  CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER)