-- The same for a fold, which has two parameters: where the dialect has one.
SELECT
  t.nr
, reduce(slice(t.scores, 2, cardinality(t.scores)), element_at(t.scores, 1), (_l0_order, _l0_case) -> _l0_order + _l0_case, s -> s) AS total
FROM
 check_complex t
WHERE
  t.nr <> 2