-- Lists made of lists. NULL elements come last in either order, list_distinct drops them and gives the
-- rest in ascending order, and list_concat is NULL when either list is (vec of row 2 is NULL).
SELECT
  t.nr
, CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(t.tags) AS _u(_e) ORDER BY _e NULLS LAST) END AS sorted_tags
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(t.scores) AS _u(_e) ORDER BY _e DESC NULLS LAST) END AS scores_desc
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT DISTINCT _e FROM unnest(BO_1.L) AS _u(_e) WHERE _e IS NOT NULL ORDER BY _e) END FROM (SELECT array_cat(t.tags, t.tags) AS L) BO_1) AS distinct_tags
, array_cat(t.scores, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 10 FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _pos__l0_x) END) AS joined
, array_cat(t.vec, t.vec) AS vec_twice
FROM
 check_complex t