-- string_agg is an aggregate: it must trigger GROUP BY inference for the
-- scalar columns and must not itself be added to GROUP BY.
SELECT
  c.company_name
, string_agg(o.ship_city, ',')
FROM
 customers c
  INNER JOIN orders o ON
   c.customer_id = o.customer_id
GROUP BY
  c.company_name