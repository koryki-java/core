-- list_reduce combines a list into one value: (a, b) is the result so far and the next element,
-- starting with the first element. A list of one element is that element; DuckDB refuses an empty
-- list, so the row that has one is filtered out. A NULL anywhere makes the result NULL, as the sum
-- of a NULL does.
SELECT
  t.nr
, REDUCE(ARRAY_SLICE(t.scores, 1, ARRAY_SIZE(t.scores)), GET(t.scores, 0), (_l0_a, _l0_b) -> _l0_a + _l0_b) AS total
, REDUCE(ARRAY_SLICE(t.vec, 1, ARRAY_SIZE(t.vec)), GET(t.vec, 0), (_l0_a, _l0_b) -> _l0_a * _l0_b) AS product
FROM
 check_complex t
WHERE
  t.nr <> 2