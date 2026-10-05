-- A list that is an expression, not a column, is evaluated once where its size or its elements are
-- asked for more than once: a position from the end, a range with a negative end, the position
-- parameter of a lambda, a fold.
SELECT
  t.nr
, element_at(filter(t.scores, _l0_x -> _l0_x > 5), -1) AS last_big
, (SELECT slice(BO_1.L, greatest(cardinality(BO_1.L) + -1, 1), greatest(cardinality(BO_1.L) - (greatest(cardinality(BO_1.L) + -1, 1)) + 1, 0)) FROM (VALUES (transform(t.scores, _l0_x -> _l0_x * 2))) AS BO_1(L)) AS last_two_doubled
, (SELECT zip_with(BO_1.L, slice(sequence(1, greatest(cardinality(BO_1.L), 1)), 1, cardinality(BO_1.L)), (_l0_x, _l0_i) -> _l0_x * _l0_i) FROM (VALUES (filter(t.scores, _l0_x -> _l0_x > 5))) AS BO_1(L)) AS weighted_big
, (SELECT reduce(slice(BO_1.L, 2, cardinality(BO_1.L)), element_at(BO_1.L, 1), (_l0_a, _l0_b) -> _l0_a + _l0_b, s -> s) FROM (VALUES (filter(t.scores, _l0_x -> _l0_x > 5))) AS BO_1(L)) AS total_big
FROM
 check_complex t
WHERE
  t.nr = 1