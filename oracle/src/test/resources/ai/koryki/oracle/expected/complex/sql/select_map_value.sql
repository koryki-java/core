-- (Oracle's maps are JSON objects: an integer key is the text key '1', and a JSON @1 is a list position.)
-- A name selects the value of a text key, a position the value of an integer key: the same @1 that
-- is a list position on a list. A key the map does not have is NULL, not an error.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$.color' RETURNING JSON) END AS color
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$.size' RETURNING JSON) END AS "size"
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$.zz' RETURNING JSON) END AS missing
, CASE WHEN JSON_VALUE(t.counts, '$.type()') = 'object' THEN JSON_QUERY(t.counts, '$.a' RETURNING JSON) END AS count_a
, CASE WHEN JSON_VALUE(t.lookup, '$.type()') = 'object' THEN JSON_QUERY(t.lookup, '$."1"' RETURNING JSON) END AS one
, CASE WHEN JSON_VALUE(t.lookup, '$.type()') = 'object' THEN JSON_QUERY(t.lookup, '$."2"' RETURNING JSON) END AS two
FROM
 check_complex t