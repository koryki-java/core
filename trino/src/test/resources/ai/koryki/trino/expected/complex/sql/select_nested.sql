-- Steps follow the structure to any depth: a list of structs, a struct holding a list, a list of
-- maps of structs holding a list and a map of lists. Each step is checked against the type it meets.
SELECT
  t.nr
, (element_at(t.items, 1)).sku AS first_sku
, (element_at(t.items, 1)).price AS first_price
, element_at((element_at(t.items, 1)).opts, 2) AS second_option
, (element_at(t.items, 2)).opts AS empty_options
, (element_at(t.events, 1)).ts AS first_event
, (element_at(t.events, 1)).day AS first_day
, element_at((element_at(element_at(t.deep, 1), 'm')).a, 2) AS deep_number
, element_at(element_at((element_at(element_at(t.deep, 1), 'm')).b, 1), 1) AS deep_text
, slice(t.items, 2, 1) AS second_item
FROM
 check_complex t