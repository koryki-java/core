SELECT
  c.company_name
, c.contact_name
FROM
 customers c
WHERE
  NOT (
   EXISTS (
    SELECT
     1
   FROM
    orders o
   WHERE
    c.customer_id = o.customer_id
   AND
     o.order_date BETWEEN DATE '2023-01-01' AND DATE '2023-12-31'
   ))
