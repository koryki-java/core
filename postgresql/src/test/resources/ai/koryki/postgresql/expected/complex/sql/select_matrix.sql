-- An array of arrays is one two-dimensional array here: a position in every dimension at once, the
-- first for the row. The dimension a negative position counts in is the one it is written in.
SELECT
  t.nr
, t.matrix[2][1] AS cell
, t.matrix[array_length(t.matrix, 1)][array_length(t.matrix, 2)] AS corner
, t.matrix[1][array_length(t.matrix, 2)] AS end_of_first_row
FROM
 check_complex t