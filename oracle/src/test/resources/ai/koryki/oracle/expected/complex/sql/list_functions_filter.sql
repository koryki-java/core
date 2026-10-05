-- The functions work where a condition is: the rows whose list holds a value, and the rows with
-- something in the list at all.
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.scores)) END AS n
FROM
 check_complex t
WHERE
   CASE WHEN t.tags IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM TABLE(t.tags) WHERE COLUMN_VALUE = 'red') > 0) END
  OR
   CASE WHEN t.scores IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.scores)) END > 2