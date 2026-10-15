-- JSON whose members the catalog declares (.n INTEGER, .a.b[] INTEGER) needs no cast: the member is
-- a number in comparisons, arithmetic and aggregates. A member that is not declared stays JSON.
SELECT
  t.nr
, CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) + 1 AS n_plus_one
, CAST((GET_PATH(t.doc, 'a.b[1]'))::STRING AS INTEGER) * 2 AS second_doubled
, GET_PATH(t.doc, '[0]') AS undeclared
FROM
 check_complex t
WHERE
  CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) > 0