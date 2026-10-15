-- Revenue by sku, the analysis a list column alone cannot answer.
SELECT
  i.sku
, count(i.ordinal) AS lines
, sum(i.qty * i.price) AS revenue
FROM
 check_complex_items i
GROUP BY
  i.sku