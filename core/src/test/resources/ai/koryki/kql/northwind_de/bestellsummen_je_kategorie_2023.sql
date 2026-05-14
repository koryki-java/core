SELECT
  k.category_name
, sum(p.unit_price * p.quantity)
FROM
 orders b
  INNER JOIN order_details p ON
   b.order_id = p.order_id
    INNER JOIN products pr ON
     p.product_id = pr.product_id
      INNER JOIN categories k ON
       pr.category_id = k.category_id
WHERE
  b.order_date BETWEEN DATE '2023-01-01' AND DATE '2023-12-31'
GROUP BY
  k.category_name
ORDER BY
  sum(p.unit_price * p.quantity) DESC
