-- Whether some or every element passes a test. An empty list has no element that passes and every
-- element of it does; a test that is NULL does not pass (row 3 has NULL scores and a NULL quantity).
SELECT
  t.nr
, (cardinality(CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 25 ORDER BY _pos__l0_x) END) > 0) AS any_big
, (cardinality(CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END) = cardinality(t.scores)) AS all_big
, (cardinality(CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT _l0_it FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u WHERE (_l0_it).qty > 1 ORDER BY _pos__l0_it) END) > 0) AS any_many
, (cardinality(CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT _l0_it FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u WHERE (_l0_it).qty > 0 ORDER BY _pos__l0_it) END) = cardinality(t.items)) AS all_stocked
FROM
 check_complex t