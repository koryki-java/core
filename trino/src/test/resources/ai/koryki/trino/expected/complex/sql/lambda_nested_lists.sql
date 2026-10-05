-- Lists made by lists made by lists: each lambda takes the position as well as the element, which
-- names the list it works on more than once, so the SQL must evaluate it once or grow with every
-- nesting. The same on every dialect that can build the list.
SELECT
  t.nr
, (SELECT zip_with(BO_2.L, slice(sequence(1, greatest(cardinality(BO_2.L), 1)), 1, cardinality(BO_2.L)), (_l0_x, _l0_i) -> _l0_x - _l0_i) FROM (SELECT zip_with(BO_1.L, slice(sequence(1, greatest(cardinality(BO_1.L), 1)), 1, cardinality(BO_1.L)), (_l0_x, _l0_i) -> _l0_x * 2) AS L FROM (SELECT zip_with(t.scores, slice(sequence(1, greatest(cardinality(t.scores), 1)), 1, cardinality(t.scores)), (_l0_x, _l0_i) -> _l0_x + _l0_i) AS L) BO_1) BO_2) AS deep
FROM
 check_complex t