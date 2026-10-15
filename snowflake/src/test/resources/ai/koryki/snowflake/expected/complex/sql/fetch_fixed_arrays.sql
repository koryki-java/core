-- A fixed-size array and an array of arrays. DOUBLE[3][2] is two rows of three values: the outer
-- size is the last bracket. Row 2 has no value at all, which is NULL and not an empty array.
SELECT
  t.nr
, t.vec
, t.matrix
FROM
 check_complex t