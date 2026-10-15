-- The list can be anything that is a list, a lambda's result as well as a column. Row 2 has an empty
-- list, row 3 NULL elements among its values: nothing matches, and the sum of what is left is 10.
SELECT
  t.nr
, ARRAY_CONTAINS('RED', TRANSFORM(t.tags, _l0_x -> upper(_l0_x))) AS has_red_upper
, ARRAY_SIZE(FILTER(t.scores, _l0_x -> _l0_x > 5)) AS big
, (SELECT CASE WHEN ARRAY_SIZE(BO_1.L) = 0 THEN NULL ELSE REDUCE(BO_1.L, 0, (a, x) -> a + x) END FROM (SELECT ARRAY_COMPACT(TRANSFORM(t.scores, _l0_x -> _l0_x * 2)) AS L) BO_1) AS doubled_total
FROM
 check_complex t