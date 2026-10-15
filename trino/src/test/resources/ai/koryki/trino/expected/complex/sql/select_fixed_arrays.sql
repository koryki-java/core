-- Elements of a fixed-size array and of an array of arrays: matrix@2@1 is the first value of the
-- second row. The first index selects the row, because DOUBLE[3][2] is two rows of three.
SELECT
  t.nr
, element_at(t.vec, 3) AS vec_last
, element_at(t.matrix, 1) AS first_row
, element_at(element_at(t.matrix, 2), 1) AS cell
, element_at(element_at(t.matrix, -1), -1) AS corner
FROM
 check_complex t