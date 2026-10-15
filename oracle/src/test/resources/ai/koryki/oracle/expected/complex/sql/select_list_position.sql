-- A position selects one element of a list: @1 the first, @-1 the last. Positions count from 1, as
-- in a list of DuckDB; a position outside the list, and any position of an empty list, is NULL.
SELECT
  t.nr
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 1 AND t.tags IS NOT NULL) AS first_tag
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = (SELECT COUNT(*) FROM TABLE(t.tags)) - 1 + 1 AND t.tags IS NOT NULL) AS last_tag
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 5 AND t.tags IS NOT NULL) AS fifth_tag
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.scores) Z) E1 WHERE E1.RN = 2 AND t.scores IS NOT NULL) AS second_score
FROM
 check_complex t