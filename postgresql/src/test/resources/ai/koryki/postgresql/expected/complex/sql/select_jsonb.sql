-- A selector on jsonb is a path; positions count from 1 in KQL and from 0 in jsonb, and the
-- rendering translates. The maps are objects: a name is a key, and where the catalog says the keys are
-- integers (lookup) the key is written as on any other dialect, @1.
SELECT
  t.nr
, (t.attrs ->> 'color') AS color
, CAST((t.counts ->> 'a') AS INTEGER) AS count_a
, (t.lookup ->> '1') AS one
, CAST((t.doc ->> 'n') AS INTEGER) AS n
, CAST((t.doc -> 'a' -> 'b' ->> 0) AS INTEGER) AS inner_first
, (t.doc -> 0) AS array_first
, (t.doc -> -1) AS array_last
, CAST((t.deep -> 0 -> 'm' -> 'a' ->> 1) AS INTEGER) AS deep_number
FROM
 check_complex t