-- A lambda over a JSON array: its elements are the declared integers.
SELECT
  t.nr
, transform(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER)), _l0_x -> _l0_x * 10) AS tenfold
, filter(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER)), _l0_x -> _l0_x > 5) AS big
FROM
 check_complex t
WHERE
  cardinality(CAST(json_extract(t.doc, '$.a.b') AS ARRAY(INTEGER))) > 0