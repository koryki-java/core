-- canonical formatter: INTEGER without forced ".0", DATE as ISO, DECIMAL at real scale
SELECT
  o.order_id
, o.order_date
, o.freight
FROM
 orders o
WHERE
  o.order_id = 10248