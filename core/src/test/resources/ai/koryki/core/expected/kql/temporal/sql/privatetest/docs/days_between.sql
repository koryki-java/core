-- days_between: signed day distance between two dates.
SELECT
  date_diff('day', DATE '2023-03-01', DATE '2023-02-01') AS signed_minus28
FROM
 check_temporal c