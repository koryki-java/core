-- Lists made of lists. NULL elements come last in either order, list_distinct drops them and gives the
-- rest in ascending order, and list_concat is NULL when either list is (vec of row 2 is NULL).
SELECT
  t.nr
, array_sort(t.tags) AS sorted_tags
, array_sort(t.scores, (a, b) -> CASE WHEN a IS NULL AND b IS NULL THEN 0 WHEN a IS NULL THEN 1 WHEN b IS NULL THEN -1 WHEN a < b THEN 1 WHEN a > b THEN -1 ELSE 0 END) AS scores_desc
, array_sort(array_distinct(filter(coalesce(concat(t.tags, t.tags), t.tags, t.tags), x -> x IS NOT NULL))) AS distinct_tags
, coalesce(concat(t.scores, transform(t.scores, _l0_x -> _l0_x * 10)), t.scores, transform(t.scores, _l0_x -> _l0_x * 10)) AS joined
, coalesce(concat(t.vec, t.vec), t.vec, t.vec) AS vec_twice
FROM
 check_complex t