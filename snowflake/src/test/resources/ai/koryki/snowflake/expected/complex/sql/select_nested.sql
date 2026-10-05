-- Steps follow the structure to any depth: a list of structs, a struct holding a list, a list of
-- maps of structs holding a list and a map of lists. Each step is checked against the type it meets.
SELECT
  t.nr
, GET(GET(t.items, 0), 'sku') AS first_sku
, GET(GET(t.items, 0), 'price') AS first_price
, GET(GET(GET(t.items, 0), 'opts'), 1) AS second_option
, GET(GET(t.items, 1), 'opts') AS empty_options
, GET(GET(t.events, 0), 'ts') AS first_event
, GET(GET(t.events, 0), 'day') AS first_day
, GET(GET(GET(GET(t.deep, 0), 'm'), 'a'), 1) AS deep_number
, GET(GET(GET(GET(GET(t.deep, 0), 'm'), 'b'), 1), 0) AS deep_text
, ARRAY_SLICE(t.items, 1, 2) AS second_item
FROM
 check_complex t