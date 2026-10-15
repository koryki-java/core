-- list_transform does something to every element of a list and gives the list back, element for
-- element. x is the element; a second parameter is its position, from 1. A NULL element stays NULL.
SELECT
  t.nr
, TRANSFORM(t.tags, _l0_x -> upper(_l0_x)) AS shouted
, TRANSFORM(t.scores, _l0_x -> _l0_x * 2) AS doubled
, TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(t.scores) + 1), _l0_i -> GET(t.scores, CAST(_l0_i AS INTEGER) - 1) * _l0_i) AS weighted
FROM
 check_complex t