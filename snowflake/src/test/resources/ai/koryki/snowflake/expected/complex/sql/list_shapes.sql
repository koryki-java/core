-- Lists made of lists. NULL elements come last in either order, list_distinct drops them and gives the
-- rest in ascending order, and list_concat is NULL when either list is (vec of row 2 is NULL).
SELECT
  t.nr
, ARRAY_SORT(t.tags, TRUE, FALSE) AS sorted_tags
, ARRAY_SORT(t.scores, FALSE, FALSE) AS scores_desc
, ARRAY_SORT(ARRAY_DISTINCT(ARRAY_COMPACT(COALESCE(ARRAY_CAT(t.tags, t.tags), t.tags, t.tags))), TRUE, FALSE) AS distinct_tags
, COALESCE(ARRAY_CAT(t.scores, TRANSFORM(t.scores, _l0_x -> _l0_x * 10)), t.scores, TRANSFORM(t.scores, _l0_x -> _l0_x * 10)) AS joined
, COALESCE(ARRAY_CAT(t.vec, t.vec), t.vec, t.vec) AS vec_twice
FROM
 check_complex t