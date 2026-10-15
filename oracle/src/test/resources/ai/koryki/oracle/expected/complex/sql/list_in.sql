-- x IN list is the membership of a value in a list column: 'red' IN t.tags is list_contains(t.tags, 'red').
-- The value meets the elements, so a number against a list of texts is refused. NOT (...) is the negation.
SELECT
  t.nr
, CASE WHEN t.tags IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.tags)) END AS n
FROM
 check_complex t
WHERE
  CASE WHEN t.tags IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM TABLE(t.tags) WHERE COLUMN_VALUE = 'red') > 0) END
 AND
  NOT ((
CASE WHEN t.scores IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM TABLE(t.scores) WHERE COLUMN_VALUE = 4) > 0) END
   ))