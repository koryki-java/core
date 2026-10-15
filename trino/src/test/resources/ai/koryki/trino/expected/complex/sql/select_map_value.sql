-- A name selects the value of a text key, a position the value of an integer key: the same @1 that
-- is a list position on a list. A key the map does not have is NULL, not an error.
SELECT
  t.nr
, element_at(t.attrs, 'color') AS color
, element_at(t.attrs, 'size') AS size
, element_at(t.attrs, 'zz') AS missing
, element_at(t.counts, 'a') AS count_a
, element_at(t.lookup, 1) AS one
, element_at(t.lookup, 2) AS two
FROM
 check_complex t