-- The same for a fold, which has two parameters: where the dialect has one.
SELECT
  t.nr
, REDUCE(ARRAY_SLICE(t.scores, 1, ARRAY_SIZE(t.scores)), GET(t.scores, 0), (_l0_order, _l0_case) -> _l0_order + _l0_case) AS total
FROM
 check_complex t
WHERE
  t.nr <> 2