-- Duration literal: filter orders placed within the last 30 days.
SELECT
  o.order_id
, o.order_date
FROM
 orders o
WHERE
  o.order_date >= (CURRENT_TIMESTAMP - INTERVAL '30 day')::DATE