-- Elements of a fixed-size array and of an array of arrays: matrix@2@1 is the first value of the
-- second row. The first index selects the row, because DOUBLE[3][2] is two rows of three.
SELECT
  t.nr
, GET(t.vec, 2) AS vec_last
, GET(t.matrix, 0) AS first_row
, GET(GET(t.matrix, 1), 0) AS cell
, CASE WHEN ARRAY_SIZE(CASE WHEN ARRAY_SIZE(t.matrix) >= 1 THEN GET(t.matrix, ARRAY_SIZE(t.matrix) - 1) END) >= 1 THEN GET(CASE WHEN ARRAY_SIZE(t.matrix) >= 1 THEN GET(t.matrix, ARRAY_SIZE(t.matrix) - 1) END, ARRAY_SIZE(CASE WHEN ARRAY_SIZE(t.matrix) >= 1 THEN GET(t.matrix, ARRAY_SIZE(t.matrix) - 1) END) - 1) END AS corner
FROM
 check_complex t