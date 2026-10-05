-- A selector on jsonb is a path; positions count from 1 in KQL and from 0 in jsonb, and the
-- rendering translates. The maps are objects: a name is a key, an integer key is written as a name.
SELECT
  t.nr
, (t.attrs -> 'color') AS color
, (t.counts -> 'a') AS count_a
, (t.lookup -> '1') AS one
, (t.doc -> 'n') AS n
, (t.doc -> 'a' -> 'b' -> 0) AS inner_first
, (t.doc -> 0) AS array_first
, (t.doc -> -1) AS array_last
, (t.deep -> 0 -> 'm' -> 'a' -> 1) AS deep_number
FROM
 check_complex t