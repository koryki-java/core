-- JSON whose members the catalog declares (.n INTEGER, .a.b[] INTEGER) needs no cast: the member is
-- a number in comparisons, arithmetic and aggregates. A member that is not declared stays JSON.
SELECT
  t.nr
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER) + 1 AS n_plus_one
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.b.type()') = 'array' THEN JSON_VALUE(t.doc, '$.a.b[1]') END AS INTEGER) * 2 AS second_doubled
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'array' THEN JSON_QUERY(t.doc, '$[0]' RETURNING JSON) END AS undeclared
FROM
 check_complex t
WHERE
  CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.n') END AS INTEGER) > 0