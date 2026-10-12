-- A lambda over a JSON array: its elements are the declared integers.
SELECT
  t.nr
, TRANSFORM(TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER)), _l0_x -> _l0_x * 10) AS tenfold
, FILTER(TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER)), _l0_x -> _l0_x > 5) AS big
FROM
 check_complex t
WHERE
  ARRAY_SIZE(TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER))) > 0