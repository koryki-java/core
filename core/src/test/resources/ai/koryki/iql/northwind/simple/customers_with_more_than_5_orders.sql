-- find customers having more than 5 orders,
-- sort descending by count

SELECT
  c.company_name AS cn
, count(o.order_id) AS ordercount
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
GROUP BY
  c.company_name
HAVING
  count(o.order_id) > 5
ORDER BY
  count(o.order_id) DESC
