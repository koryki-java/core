-- list_transform does something to every element of a list and gives the list back, element for
-- element. x is the element; a second parameter is its position, from 1. A NULL element stays NULL.
SELECT
  t.nr
, transform(t.tags, _l0_x -> upper(_l0_x)) AS shouted
, transform(t.scores, _l0_x -> _l0_x * 2) AS doubled
, zip_with(t.scores, slice(sequence(1, greatest(cardinality(t.scores), 1)), 1, cardinality(t.scores)), (_l0_x, _l0_i) -> _l0_x * _l0_i) AS weighted
FROM
 check_complex t