-- An array of composites: an element, a field of it, a list inside the field, and a slice that is an
-- array of composites again.
SELECT
  t.nr
, (t.items[1]).sku AS first_sku
, (t.items[1]).price AS first_price
, ((t.items[1]).opts)[2] AS second_option
, (t.items[2]).opts AS empty_options
, (t.events[1]).ts AS first_event
, t.items[2:2] AS second_item
FROM
 check_complex t