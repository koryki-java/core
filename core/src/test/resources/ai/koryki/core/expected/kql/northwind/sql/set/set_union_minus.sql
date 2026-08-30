  SELECT
    c1.contact_name AS cn
  FROM
   customers c1
  WHERE
    c1.contact_name like 'a%'
UNION
  SELECT
    c2.contact_name AS cn
  FROM
   customers c2
  WHERE
    c2.contact_name like 'b%'
EXCEPT
 SELECT
   c3.contact_name AS cn
 FROM
  customers c3
 WHERE
   c3.contact_name like 'ab%'