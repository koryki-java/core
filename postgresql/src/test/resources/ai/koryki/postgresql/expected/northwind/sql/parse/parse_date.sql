-- parse_date: filter orders where order_date matches a European-format date string.
SELECT
  count(o.order_id)
FROM
 orders o
WHERE
  o.order_date > TO_DATE('04.07.1996', 'DD.MM.YYYY')