-- Lists of scalars come back as one value each: a list of text, a list of integers. An empty list
-- is not NULL, and an element may be NULL: row 1 is full, row 2 holds empty lists, row 3 has NULL
-- elements inside the lists.
SELECT
  t.nr
, t.tags
, t.scores
FROM
 check_complex t