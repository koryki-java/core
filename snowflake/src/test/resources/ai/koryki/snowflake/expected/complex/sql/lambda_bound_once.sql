-- A list that is an expression, not a column, is evaluated once where its size or its elements are
-- asked for more than once: a position from the end, a range with a negative end, the position
-- parameter of a lambda, a fold.
SELECT
  t.nr
, (SELECT CASE WHEN ARRAY_SIZE(BO_1.L) >= 1 THEN GET(BO_1.L, ARRAY_SIZE(BO_1.L) - 1) END FROM (SELECT FILTER(t.scores, _l0_x -> _l0_x > 5) AS L) BO_1) AS last_big
, (SELECT ARRAY_SLICE(BO_1.L, GREATEST(ARRAY_SIZE(BO_1.L) - 2, 0), ARRAY_SIZE(BO_1.L)) FROM (SELECT TRANSFORM(t.scores, _l0_x -> _l0_x * 2) AS L) BO_1) AS last_two_doubled
, (SELECT TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(BO_1.L) + 1), _l0_i -> GET(BO_1.L, _l0_i - 1) * _l0_i) FROM (SELECT FILTER(t.scores, _l0_x -> _l0_x > 5) AS L) BO_1) AS weighted_big
, (SELECT REDUCE(ARRAY_SLICE(BO_1.L, 1, ARRAY_SIZE(BO_1.L)), GET(BO_1.L, 0), (_l0_a, _l0_b) -> _l0_a + _l0_b) FROM (SELECT FILTER(t.scores, _l0_x -> _l0_x > 5) AS L) BO_1) AS total_big
FROM
 check_complex t
WHERE
  t.nr = 1