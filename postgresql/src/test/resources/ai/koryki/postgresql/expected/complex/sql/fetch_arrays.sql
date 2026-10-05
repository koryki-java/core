-- Arrays come back as lists: text[], integer[], double precision[], and the two-dimensional matrix as a
-- list of lists. NULL and empty stay apart, and so do NULL elements.
SELECT
  t.nr
, t.tags
, t.scores
, t.vec
, t.matrix
FROM
 check_complex t