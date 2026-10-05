-- A JSON member is no number until it is cast: to_integer, to_float and to_text read the scalar at the
-- path, a number as a number. The filter is on the cast, so only the object (row 1) passes; the array
-- of row 3 has no member n, and a cast of nothing is NULL.
SELECT
  t.nr
, CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) AS n
, CAST((GET_PATH(t.doc, 'a.b[0]'))::STRING AS INTEGER) + 1 AS inner_plus_one
, CAST((GET_PATH(t.doc, 'n'))::STRING AS TEXT) AS n_text
, CAST((GET_PATH(t.doc, 'a.b[1]'))::STRING AS FLOAT) AS second
FROM
 check_complex t
WHERE
  CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) > 0