-- Duration literal: filter employees hired within the last year.
SELECT
  e.last_name
, e.hire_date
FROM
 employees e
WHERE
  e.hire_date >= (CURRENT_TIMESTAMP - INTERVAL '1 year')::DATE