-- A JSON column is its own family, not a nested type: an object in row 1, SQL NULL in row 2 and
-- an array in row 3.
SELECT
  t.nr
, t.doc
FROM
 check_complex t