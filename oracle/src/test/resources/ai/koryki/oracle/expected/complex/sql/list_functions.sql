-- What a list answers about itself. The size counts NULL elements and is NULL for a list that is NULL
-- (vec in row 2); the aggregates ignore NULL elements and are NULL when nothing is left (row 2 has an
-- empty list, row 3 has two NULLs and a 5); a list that is there either holds the value or it does not.
SELECT
  t.nr
, CASE WHEN t.tags IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.tags)) END AS tags
, CASE WHEN t.vec IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE(t.vec)) END AS vec
, CASE WHEN t.tags IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM TABLE(t.tags) WHERE COLUMN_VALUE = 'red') > 0) END AS has_red
, (SELECT SUM(COLUMN_VALUE) FROM TABLE(t.scores)) AS total
, (SELECT MIN(COLUMN_VALUE) FROM TABLE(t.scores)) AS lowest
, (SELECT MAX(COLUMN_VALUE) FROM TABLE(t.scores)) AS highest
, (SELECT CAST(AVG(COLUMN_VALUE) AS BINARY_DOUBLE) FROM TABLE(t.scores)) AS average
FROM
 check_complex t