-- list_reduce combines a list into one value: (a, b) is the result so far and the next element,
-- starting with the first element. A list of one element is that element; DuckDB refuses an empty
-- list, so the row that has one is filtered out. A NULL anywhere makes the result NULL, as the sum
-- of a NULL does.
SELECT
  t.nr
, reduce(slice(t.scores, 2, cardinality(t.scores)), element_at(t.scores, 1), (_l0_a, _l0_b) -> _l0_a + _l0_b, s -> s) AS total
, reduce(slice(t.vec, 2, cardinality(t.vec)), element_at(t.vec, 1), (_l0_a, _l0_b) -> _l0_a * _l0_b, s -> s) AS product
FROM
 check_complex t
WHERE
  t.nr <> 2