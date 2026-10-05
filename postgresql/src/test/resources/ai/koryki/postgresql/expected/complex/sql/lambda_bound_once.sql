-- A list that is an expression, not a column, is evaluated once where its size or its elements are
-- asked for more than once: a position from the end, a range with a negative end, the position
-- parameter of a lambda, a fold.
SELECT
  t.nr
, (SELECT BO_1.L[array_length(BO_1.L, 1)] FROM (SELECT CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END AS L) BO_1) AS last_big
, (SELECT BO_1.L[array_length(BO_1.L, 1) - 1:] FROM (SELECT CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 2 FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _pos__l0_x) END AS L) BO_1) AS last_two_doubled
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * _l0_i FROM (SELECT BO_1.L[_i] AS _l0_x, _i AS _l0_i FROM generate_subscripts(BO_1.L, 1) AS _i) AS _u ORDER BY _l0_i) END FROM (SELECT CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END AS L) BO_1) AS weighted_big
FROM
 check_complex t
WHERE
  t.nr <> 2