-- What a list answers about itself. The size counts NULL elements and is NULL for a list that is NULL
-- (vec in row 2); the aggregates ignore NULL elements and are NULL when nothing is left (row 2 has an
-- empty list, row 3 has two NULLs and a 5); a list that is there either holds the value or it does not.
SELECT
  t.nr
, ARRAY_SIZE(t.tags) AS tags
, ARRAY_SIZE(t.vec) AS vec
, ARRAY_CONTAINS('red', t.tags) AS has_red
, (SELECT CASE WHEN ARRAY_SIZE(BO_1.L) = 0 THEN NULL ELSE REDUCE(BO_1.L, 0, (a, x) -> a + x) END FROM (SELECT ARRAY_COMPACT(t.scores) AS L) BO_1) AS total
, ARRAY_MIN(t.scores) AS lowest
, ARRAY_MAX(t.scores) AS highest
, (SELECT REDUCE(BO_1.L, 0::DOUBLE, (a, x) -> a + x::DOUBLE) / NULLIF(ARRAY_SIZE(BO_1.L), 0) FROM (SELECT ARRAY_COMPACT(t.scores) AS L) BO_1) AS average
FROM
 check_complex t