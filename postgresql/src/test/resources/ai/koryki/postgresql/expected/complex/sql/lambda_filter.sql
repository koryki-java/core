-- list_filter keeps the elements for which the test is true, in order; the elements of an array of
-- composites arrive whole, so the test can select from them.
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.scores AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE _l0_x > 10 ORDER BY _pos__l0_x) END AS big
, CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.tags AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE _l0_x <> 'green' ORDER BY _pos__l0_x) END AS without_green
, CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT _l0_it FROM (SELECT _s._a[_i] AS _l0_it, _i AS _pos__l0_it FROM (SELECT t.items AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE (_l0_it).qty > 1 ORDER BY _pos__l0_it) END AS many
FROM
 check_complex t