-- The parameter is the element, whatever it is: a struct from a list of structs, and a step selects
-- from it. A lambda inside a lambda sees both parameters.
SELECT
  t.nr
, TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'price') * GET(_l0_it, 'qty')) AS line_totals
, TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'sku')) AS skus
, TRANSFORM(t.items, _l0_it -> TRANSFORM(GET(_l0_it, 'opts'), _l1_o -> upper(_l1_o))) AS options
FROM
 check_complex t