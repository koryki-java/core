-- A call with a lambda is a value like any other: it filters, and a selector takes an element of
-- what it returns. The filter is on a selection of the filtered list, which is NULL for an empty
-- one; list_reduce would refuse it.
SELECT
  t.nr
, element_at(transform(t.tags, _l0_x -> upper(_l0_x)), 1) AS first_shouted
, element_at(filter(t.scores, _l0_x -> _l0_x > 5), 1) AS first_big
, element_at(transform(t.items, _l0_it -> (_l0_it).sku), -1) AS last_sku
FROM
 check_complex t
WHERE
  element_at(filter(t.scores, _l0_x -> _l0_x > 5), 1) > 5