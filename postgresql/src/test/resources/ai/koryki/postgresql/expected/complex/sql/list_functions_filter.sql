-- The functions work where a condition is: the rows whose list holds a value, and the rows with
-- something in the list at all.
SELECT
  t.nr
, cardinality(t.scores) AS n
FROM
 check_complex t
WHERE
   (t.tags @> ARRAY['red'])
  OR
   cardinality(t.scores) > 2