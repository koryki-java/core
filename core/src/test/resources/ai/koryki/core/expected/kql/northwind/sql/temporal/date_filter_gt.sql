-- Date literal: greater-than comparison on shipped date.
SELECT
  o.order_id
, o.shipped_date
FROM
 orders o
WHERE
  o.shipped_date > DATE '1997-01-01'