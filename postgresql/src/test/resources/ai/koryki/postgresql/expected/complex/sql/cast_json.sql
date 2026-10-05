-- A JSON member is no number until it is cast: to_integer, to_float and to_text read the scalar at the
-- path, a number as a number. The filter is on the cast, so only the object (row 1) passes; the array
-- of row 3 has no member n, and a cast of nothing is NULL.
SELECT
  t.nr
, CAST((t.doc ->> 'n') AS INTEGER) AS n
, CAST((t.doc -> 'a' -> 'b' ->> 0) AS INTEGER) + 1 AS inner_plus_one
, CAST((t.doc ->> 'n') AS TEXT) AS n_text
, CAST((t.doc -> 'a' -> 'b' ->> 1) AS FLOAT) AS second
FROM
 check_complex t
WHERE
  CAST((t.doc ->> 'n') AS INTEGER) > 0