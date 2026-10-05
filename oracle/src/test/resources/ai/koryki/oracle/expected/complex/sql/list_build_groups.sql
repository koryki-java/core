-- One list per group. A group whose values are all NULL has nothing to collect: row 2 has no first tag.
SELECT
  t.nr
, CASE WHEN COUNT((SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 1)) = 0 THEN NULL ELSE CAST(COLLECT((SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 1)) AS SYS.ODCIVARCHAR2LIST) END AS first_tag
FROM
 check_complex t
GROUP BY
  t.nr