-- A call with a lambda is a value like any other: it filters, and a selector takes an element of
-- what it returns.
SELECT
  t.nr
, (CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(SELECT upper(_l0_x) FROM (SELECT t.tags[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.tags, 1) AS _i) AS _u ORDER BY _pos__l0_x) END)[1] AS first_shouted
, (CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END)[1] AS first_big
, (SELECT BO_1.L[array_length(BO_1.L, 1)] FROM (SELECT CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT (_l0_it).sku FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u ORDER BY _pos__l0_it) END AS L) BO_1) AS last_sku
FROM
 check_complex t
WHERE
  (CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END)[1] > 5