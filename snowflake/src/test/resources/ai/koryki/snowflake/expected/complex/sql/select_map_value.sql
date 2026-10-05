-- A name selects the value of a text key, a position the value of an integer key: the same @1 that
-- is a list position on a list. A key the map does not have is NULL, not an error.
SELECT
  t.nr
, GET(t.attrs, 'color') AS color
, GET(t.attrs, 'size') AS size
, GET(t.attrs, 'zz') AS missing
, GET(t.counts, 'a') AS count_a
, GET(t.lookup, 1) AS one
, GET(t.lookup, 2) AS two
FROM
 check_complex t