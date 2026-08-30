SELECT
  c.company_name
, count(o.order_id)
FROM
 customers c
  LEFT OUTER JOIN orders o ON
   c.customer_id = o.customer_id
  AND
    o.order_date >= DATE '2023-01-01'
   AND
    o.order_date < DATE '2024-01-01'
WHERE
  c.country = 'Germany'
GROUP BY
  c.company_name
ORDER BY
  c.company_name DESC