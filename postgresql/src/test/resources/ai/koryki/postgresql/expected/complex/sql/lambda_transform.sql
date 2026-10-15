-- list_transform becomes a subquery over the positions of the array: the element is the parameter,
-- the second parameter the position from 1. A NULL element stays NULL, a NULL list stays NULL.
SELECT
  t.nr
, CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT upper(_l0_x) FROM (SELECT t.tags[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.tags, 1) AS _i) AS _u ORDER BY _pos__l0_x) END AS shouted
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 2 FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _pos__l0_x) END AS doubled
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * _l0_i FROM (SELECT t.scores[_i] AS _l0_x, _i AS _l0_i FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _l0_i) END AS weighted
FROM
 check_complex t