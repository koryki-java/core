-- Time literal: BETWEEN range on employee working hours.
SELECT
  e.last_name
, e.working_hour_from
, e.working_hour_to
FROM
 employees e
WHERE
  e.working_hour_to <= TIME '17:00:00'
 AND
  e.working_hour_from >= TIME '08:00:00'