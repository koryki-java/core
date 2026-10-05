-- A lambda sees the columns of the query as well as its parameters: here the row's own nr, added to
-- every score. (Snowflake's lambda does not see the parameter of the lambda around it, so the nested
-- form of the shared corpus is not here.)
SELECT
  t.nr
, TRANSFORM(t.scores, _l0_x -> _l0_x + t.nr) AS shifted
FROM
 check_complex t