-- A JSON array whose elements the catalog declares (.a.b[] INTEGER) is a list: the list functions
-- and lambdas apply to it. Row 1 holds [5, 6]; the others have no such array.
SELECT
  t.nr
, ARRAY_SIZE(TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER))) AS size
, (SELECT CASE WHEN ARRAY_SIZE(BO_1.L) = 0 THEN NULL ELSE REDUCE(BO_1.L, 0, (a, x) -> a + x) END FROM (SELECT ARRAY_COMPACT(TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER))) AS L) BO_1) AS total
, ARRAY_CONTAINS(6, TRANSFORM(CAST(GET_PATH(t.doc, 'a.b') AS ARRAY), e -> CAST(e AS INTEGER))) AS has_six
FROM
 check_complex t