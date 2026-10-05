-- x IN list is the membership of a value in a list column: 'red' IN t.tags is list_contains(t.tags, 'red').
-- The value meets the elements, so a number against a list of texts is refused. NOT (...) is the negation.
SELECT
  t.nr
, ARRAY_SIZE(t.tags) AS n
FROM
 check_complex t
WHERE
  ARRAY_CONTAINS('red', t.tags)
 AND
  NOT ((
ARRAY_CONTAINS(4, t.scores)
   ))