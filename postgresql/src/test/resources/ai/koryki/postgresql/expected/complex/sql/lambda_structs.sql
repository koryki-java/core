-- The parameter is the element: a composite from an array of composites, and a step selects from it.
-- (A list of lists, as the options of every item, is not possible: PostgreSQL arrays do not nest.)
SELECT
  t.nr
, CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT (_l0_it).price * (_l0_it).qty FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u ORDER BY _pos__l0_it) END AS line_totals
, CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT (_l0_it).sku FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u ORDER BY _pos__l0_it) END AS skus
, ((CASE WHEN t.items IS NULL THEN NULL ELSE ARRAY(SELECT _l0_it FROM (SELECT t.items[_i] AS _l0_it, _i AS _pos__l0_it FROM generate_subscripts(t.items, 1) AS _i) AS _u WHERE (_l0_it).price > 10 ORDER BY _pos__l0_it) END)[1]).sku AS first_expensive
FROM
 check_complex t