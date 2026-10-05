-- A call with a lambda is a value like any other: it filters, and a selector takes an element of
-- what it returns. The filter is on a selection of the filtered list, which is NULL for an empty
-- one; list_reduce would refuse it.
SELECT
  t.nr
, GET(TRANSFORM(t.tags, _l0_x -> upper(_l0_x)), 0) AS first_shouted
, GET(FILTER(t.scores, _l0_x -> _l0_x > 5), 0) AS first_big
, CASE WHEN ARRAY_SIZE(TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'sku'))) >= 1 THEN GET(TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'sku')), ARRAY_SIZE(TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'sku'))) - 1) END AS last_sku
FROM
 check_complex t
WHERE
  GET(FILTER(t.scores, _l0_x -> _l0_x > 5), 0) > 5