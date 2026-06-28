-- Date literal: equality comparison on order date.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date = DATE '1996-07-04'