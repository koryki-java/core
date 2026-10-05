-- The functions work where a condition is: the rows whose list holds a value, and the rows with
-- something in the list at all.
SELECT
  t.nr
, ARRAY_SIZE(t.scores) AS n
FROM
 check_complex t
WHERE
   ARRAY_CONTAINS('red', t.tags)
  OR
   ARRAY_SIZE(t.scores) > 2