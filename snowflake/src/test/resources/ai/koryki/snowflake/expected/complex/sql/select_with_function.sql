-- A function on a selection and a selection on a function: coalesce keeps the structure of its
-- branches, so its result can be selected from.
SELECT
  t.nr
, upper(GET(t.tags, 0)) AS shouted
, GET(coalesce(t.tags, t.tags), 0) AS first_tag
, GET(GET(t.items, 0), 'price') * 2 AS double_price
FROM
 check_complex t