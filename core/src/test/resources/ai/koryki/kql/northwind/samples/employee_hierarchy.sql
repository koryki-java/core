WITH RECURSIVE roots (fname, lname, ttl, lvl, employee_id) AS (
 SELECT
   e.first_name AS fname
 , e.last_name AS lname
 , e.title AS ttl
 , 0 AS lvl
 , e.employee_id
 FROM
  employees e
 WHERE
   NOT (
    EXISTS (
     SELECT
      1
    FROM
     employees r
    WHERE
     e.reports_to = r.employee_id
    ))
UNION ALL
 SELECT
   e.first_name AS fname
 , e.last_name AS lname
 , e.title AS ttl
 , r.lvl + 1 AS lvl
 , e.employee_id
 FROM
  employees e
   INNER JOIN roots r ON
    e.reports_to = r.employee_id
)
SELECT
  r2.fname
, r2.lname
, r2.ttl
, r2.lvl
FROM
 roots r2
ORDER BY
  r2.lvl ASC
