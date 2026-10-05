-- list_filter keeps the elements for which the test is true, in order. An element the test cannot
-- decide, a NULL, is not kept. The same lambda works on a list of structs.
-- (Oracle cannot build a list of objects, so the filter of the items is not here.)
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.scores) Z) LA0 WHERE LA0.X > 10 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST) END AS big
, CASE WHEN t.tags IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.tags) Z) LA0 WHERE LA0.X <> 'green' ORDER BY LA0.I) AS SYS.ODCIVARCHAR2LIST) END AS without_green
FROM
 check_complex t