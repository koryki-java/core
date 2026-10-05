-- A call with a lambda is a value like any other: it filters, and a selector takes an element of
-- what it returns.
SELECT
  t.nr
, (CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT upper(_l0_x) FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.tags AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u ORDER BY _pos__l0_x) END)[1] AS first_shouted
, (CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.scores AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END)[1] AS first_big
, (CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT (_l0_it).sku FROM (SELECT _s._a[_i] AS _l0_it, _i AS _pos__l0_it FROM (SELECT t.items AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u ORDER BY _pos__l0_it) END)[array_length((CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT (_l0_it).sku FROM (SELECT _s._a[_i] AS _l0_it, _i AS _pos__l0_it FROM (SELECT t.items AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u ORDER BY _pos__l0_it) END), 1)] AS last_sku
FROM
 check_complex t
WHERE
  (CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.scores AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END)[1] > 5