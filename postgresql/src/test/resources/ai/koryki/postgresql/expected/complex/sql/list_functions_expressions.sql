-- The list can be anything that is a list, a lambda's result as well as a column. Row 2 has an empty
-- list, row 3 NULL elements among its values: nothing matches, and the sum of what is left is 10.
SELECT
  t.nr
, (CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT upper(_l0_x) FROM (SELECT t.tags[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.tags, 1) AS _i) AS _u ORDER BY _pos__l0_x) END @> ARRAY['RED']) AS has_red_upper
, cardinality(CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END) AS big
, (SELECT sum(_e) FROM unnest(CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 2 FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _pos__l0_x) END) AS _u(_e)) AS doubled_total
FROM
 check_complex t