-- x IN list is the membership of a value in a list column: 'red' IN t.tags is list_contains(t.tags, 'red').
-- The value meets the elements, so a number against a list of texts is refused. NOT (...) is the negation.
SELECT
  t.nr
, cardinality(t.tags) AS n
FROM
 check_complex t
WHERE
  (array_position(t.tags, 'red') > 0)
 AND
  NOT ((
(array_position(t.scores, 4) > 0)
   ))