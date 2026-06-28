-- parse_timestamp: filter orders delivered after a parsed timestamp string.
SELECT
  o.order_id
, o.delivered_date
FROM
 orders o
WHERE
  o.delivered_date >= strptime('1996-07-01 00:00:00', '%Y-%m-%d %H:%M:%S')