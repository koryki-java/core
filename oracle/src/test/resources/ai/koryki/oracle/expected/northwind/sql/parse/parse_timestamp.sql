-- parse_timestamp: filter orders delivered after a parsed timestamp string.
SELECT
  o.order_id
, o.delivered_date
FROM
 orders o
WHERE
  o.delivered_date >= TO_TIMESTAMP('1996-07-01 00:00:00', 'YYYY-MM-DD HH24:MI:SS')