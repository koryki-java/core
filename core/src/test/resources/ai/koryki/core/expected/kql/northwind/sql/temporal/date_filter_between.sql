-- Date literal: BETWEEN range on order date.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date >= DATE '1996-01-01'
 AND
  o.order_date < DATE '1997-01-01'