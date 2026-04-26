SELECT
  c.company_name
, c.contact_name
, c.phone
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
    INNER JOIN order_details od ON
     o.order_id = od.order_id
      INNER JOIN products p ON
       od.product_id = p.product_id
        INNER JOIN categories cat ON
         p.category_id = cat.category_id
WHERE
  cat.category_name = 'Seafood'
 AND
  NOT (
   EXISTS (
    SELECT
     1
   FROM
    orders o2
     INNER JOIN order_details od2 ON
      o2.order_id = od2.order_id
       INNER JOIN products p2 ON
        od2.product_id = p2.product_id
         INNER JOIN categories cat2 ON
          p2.category_id = cat2.category_id
   WHERE
    c.customer_id = o2.customer_id
   ))
GROUP BY
  c.company_name
, c.contact_name
, c.phone
HAVING
  count(od.order_id) > 3
