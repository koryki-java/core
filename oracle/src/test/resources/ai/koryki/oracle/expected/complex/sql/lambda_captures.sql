-- A lambda sees the columns of the query as well as its parameters: here the row's own nr, added to
-- every score, and every score combined with every score.
-- (Oracle cannot build a list of lists, so the nested transform is not here.)
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X + t.nr FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.scores) Z) LA0 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST) END AS shifted
FROM
 check_complex t