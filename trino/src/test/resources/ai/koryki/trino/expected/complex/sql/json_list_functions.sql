-- A JSON array whose elements the catalog declares (.a.b[] INTEGER) is a list: the list functions
-- and lambdas apply to it. Row 1 holds [5, 6]; the others have no such array.
SELECT
  t.nr
, cardinality(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER))) AS size
, (SELECT sum(_e) FROM UNNEST(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER))) AS _u(_e)) AS total
, (array_position(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER)), 6) > 0) AS has_six
FROM
 check_complex t