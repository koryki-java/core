-- A scalar-free struct list with temporal fields.
SELECT
  e.nr
, e.ordinal
, e.day
FROM
 check_complex_events e