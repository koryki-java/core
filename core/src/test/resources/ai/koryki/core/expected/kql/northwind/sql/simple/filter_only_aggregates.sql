-- HavingRule: when every FILTER conjunct is an aggregate comparison,
-- all of them move to HAVING and no WHERE clause may remain.
SELECT
  c.company_name
, count(o.order_id)
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
GROUP BY
  c.company_name
HAVING
  count(o.order_id) < 100
 AND
  count(o.order_id) > 10