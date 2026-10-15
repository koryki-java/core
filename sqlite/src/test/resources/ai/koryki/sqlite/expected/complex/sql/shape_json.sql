-- JSON whose members the catalog declares (.n INTEGER, .a.b[] INTEGER) needs no cast: the member is
-- a number in comparisons, arithmetic and aggregates. A member that is not declared stays JSON.
SELECT
  t.nr
, CAST(ROUND((t.doc ->> '$.n'), 0) AS INTEGER) + 1 AS n_plus_one
, CAST(ROUND((t.doc ->> '$.a.b[1]'), 0) AS INTEGER) * 2 AS second_doubled
, (t.doc -> '$[0]') AS undeclared
FROM
 check_complex t
WHERE
  CAST(ROUND((t.doc ->> '$.n'), 0) AS INTEGER) > 0