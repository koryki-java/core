WITH RECURSIVE roots (fname, lname, boss, lvl, path, employee_id) AS (
 SELECT
   e.first_name AS fname
 , e.last_name AS lname
 , CAST(NULL AS TEXT) AS boss
 , CAST(1 AS INTEGER) AS lvl
 , lpad(CAST(e.employee_id AS TEXT), 6, '0') AS path
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
 , r.lname AS boss
 , CAST(r.lvl + 1 AS INTEGER) AS lvl
 , concat(r.path, '/', lpad(CAST(e.employee_id AS TEXT), 6, '0')) AS path
 , e.employee_id
 FROM
  employees e
   INNER JOIN roots r ON
    e.reports_to = r.employee_id
)
SELECT
  r2.lvl
, concat(rpad(' ', r2.lvl * 2, ' '), r2.fname, ' ', r2.lname) AS hierarchy
, r2.path
FROM
 roots r2
ORDER BY
  r2.path ASC
