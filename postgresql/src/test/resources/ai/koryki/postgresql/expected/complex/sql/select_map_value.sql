-- A name selects the value of a text key, a position the value of an integer key: the same @1 that
-- is a list position on a list. A key the map does not have is NULL, not an error.
SELECT
  t.nr
, (t.attrs ->> 'color') AS color
, (t.attrs ->> 'size') AS size
, (t.attrs ->> 'zz') AS missing
, CAST((t.counts ->> 'a') AS INTEGER) AS count_a
, (t.lookup ->> '1') AS one
, (t.lookup ->> '2') AS two
FROM
 check_complex t