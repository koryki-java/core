-- list_transform does something to every element of a list and gives the list back, element for
-- element. @x is the element; a second parameter is its position, from 1. A NULL element stays NULL.
SELECT
  t.nr
, CASE WHEN t.tags IS NULL THEN NULL ELSE CAST(MULTISET(SELECT upper(LA0.X) FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.tags) Z) LA0 ORDER BY LA0.I) AS SYS.ODCIVARCHAR2LIST) END AS shouted
, CASE WHEN t.scores IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X * 2 FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.scores) Z) LA0 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST) END AS doubled
, CASE WHEN t.scores IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X * LA0.I FROM (SELECT COLUMN_VALUE X, ROWNUM I FROM TABLE(t.scores) Z) LA0 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST) END AS weighted
FROM
 check_complex t