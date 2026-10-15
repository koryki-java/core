-- What a list answers about itself. The size counts NULL elements and is NULL for a list that is NULL
-- (vec in row 2); the aggregates ignore NULL elements and are NULL when nothing is left (row 2 has an
-- empty list, row 3 has two NULLs and a 5); a list that is there either holds the value or it does not.
SELECT
  t.nr
, cardinality(t.tags) AS tags
, cardinality(t.vec) AS vec
, (array_position(t.tags, 'red') > 0) AS has_red
, (SELECT sum(_e) FROM UNNEST(t.scores) AS _u(_e)) AS total
, (SELECT min(_e) FROM UNNEST(t.scores) AS _u(_e)) AS lowest
, (SELECT max(_e) FROM UNNEST(t.scores) AS _u(_e)) AS highest
, (SELECT avg(CAST(_e AS DOUBLE)) FROM UNNEST(t.scores) AS _u(_e)) AS average
FROM
 check_complex t