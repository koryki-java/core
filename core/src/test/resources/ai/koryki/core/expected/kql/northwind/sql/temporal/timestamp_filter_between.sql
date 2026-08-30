-- Timestamp literal: BETWEEN range on delivered_date.
SELECT
  o.order_id
, o.delivered_date
FROM
 orders o
WHERE
  o.delivered_date >= TIMESTAMP '1996-07-01 00:00:00'
 AND
  o.delivered_date < TIMESTAMP '1997-01-01 00:00:00'