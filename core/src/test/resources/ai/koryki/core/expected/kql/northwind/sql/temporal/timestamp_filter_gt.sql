-- Timestamp literal: greater-than on delivered_date (space separator in double quotes).
SELECT
  o.order_id
, o.delivered_date
FROM
 orders o
WHERE
  o.delivered_date > TIMESTAMP '1997-01-01 12:00:00'