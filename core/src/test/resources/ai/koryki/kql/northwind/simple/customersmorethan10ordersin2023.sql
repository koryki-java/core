-- Find customers who have placed more than 10 orders in January 2023,
-- return companyname and count, sort by count.

SELECT
  c.company_name
, count(o.order_id)
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
WHERE
  o.order_date BETWEEN DATE '2023-01-01' AND DATE '2023-12-31'
GROUP BY
  c.company_name
HAVING
  count(o.order_id) > 10
ORDER BY
  count(o.order_id) DESC
