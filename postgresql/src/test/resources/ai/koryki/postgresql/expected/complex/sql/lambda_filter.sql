-- list_filter keeps the elements for which the test is true, in order; the elements of an array of
-- composites arrive whole, so the test can select from them.
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 10 ORDER BY _pos__l0_x) END AS big
, CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.tags[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.tags, 1) AS _i) AS _u WHERE _l0_x <> 'green' ORDER BY _pos__l0_x) END AS without_green
, CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT _l0_it FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u WHERE (_l0_it).qty > 1 ORDER BY _pos__l0_it) END AS many
FROM
 check_complex t