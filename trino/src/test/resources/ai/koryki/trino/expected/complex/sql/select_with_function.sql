-- A function on a selection and a selection on a function: coalesce keeps the structure of its
-- branches, so its result can be selected from.
SELECT
  t.nr
, upper(element_at(t.tags, 1)) AS shouted
, element_at(coalesce(t.tags, t.tags), 1) AS first_tag
, (element_at(t.items, 1)).price * 2 AS double_price
FROM
 check_complex t