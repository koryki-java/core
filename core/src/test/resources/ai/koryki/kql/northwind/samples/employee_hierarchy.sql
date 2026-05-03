WITH RECURSIVE roots (fname, lname, boss, lvl, path, employee_id) AS (
 SELECT
   e.first_name AS fname
 , e.last_name AS lname
 , CAST(NULL AS TEXT) AS boss
 , CAST(1 AS INTEGER) AS lvl
 , [e.employee_id] AS path
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
 , list_concat(r.path, [e.employee_id]) AS path
 , e.employee_id
 FROM
  employees e
   INNER JOIN roots r ON
    e.reports_to = r.employee_id
)
SELECT
  concat(repeat('  ', r2.lvl), r2.fname, ' ', r2.lname) AS hierarchy
, r2.path
FROM
 roots r2
ORDER BY
  r2.path ASC
