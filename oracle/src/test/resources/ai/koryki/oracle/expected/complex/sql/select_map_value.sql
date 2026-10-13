-- A name selects the value of a text key, a position the value of an integer key: the same @1 that
-- is a list position on a list. A key the map does not have is NULL, not an error.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.color') END AS color
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.size') END AS "size"
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.zz') END AS missing
, CAST(CASE WHEN JSON_VALUE(t.counts, '$.type()') = 'object' THEN JSON_VALUE(t.counts, '$.a') END AS INTEGER) AS count_a
, CASE WHEN JSON_VALUE(t.lookup, '$.type()') = 'object' THEN JSON_VALUE(t.lookup, '$."1"') END AS one
, CASE WHEN JSON_VALUE(t.lookup, '$.type()') = 'object' THEN JSON_VALUE(t.lookup, '$."2"') END AS two
FROM
 check_complex t