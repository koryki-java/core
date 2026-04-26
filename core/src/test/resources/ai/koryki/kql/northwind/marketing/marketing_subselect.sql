SELECT
  c.company_name
, c.contact_name
, c.mail
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
  NOT (c.customer_id IN ((
     SELECT
       c2.customer_id
     FROM
      customers c2
       INNER JOIN orders o2 ON
        c2.customer_id = o2.customer_id
         INNER JOIN order_details od2 ON
          o2.order_id = od2.order_id
           INNER JOIN products p2 ON
            od2.product_id = p2.product_id
             INNER JOIN categories cat2 ON
              p2.category_id = cat2.category_id
     WHERE
       cat2.category_name = 'Condiments'
    )))
 AND
  cat.category_name = 'Seafood'
GROUP BY
  c.company_name
, c.contact_name
, c.mail
HAVING
  count(od.order_id) > 3
