-- The functions work where a condition is: the rows whose list holds a value, and the rows with
-- something in the list at all.
SELECT
  t.nr
, cardinality(t.scores) AS n
FROM
 check_complex t
WHERE
   (array_position(t.tags, 'red') > 0)
  OR
   cardinality(t.scores) > 2