-- Elements of a fixed-size array and of an array of arrays: matrix@2@1 is the first value of the
-- second row. The first index selects the row, because DOUBLE[3][2] is two rows of three.
SELECT
  t.nr
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.vec) Z) E1 WHERE E1.RN = 3) AS vec_last
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.matrix) Z) E1 WHERE E1.RN = 1) AS first_row
, (SELECT (SELECT E2.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(E1.V) Z) E2 WHERE E2.RN = 1) FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.matrix) Z) E1 WHERE E1.RN = 2) AS cell
, (SELECT (SELECT E2.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(E1.V) Z) E2 WHERE E2.RN = (SELECT COUNT(*) FROM TABLE(E1.V)) - 1 + 1) FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.matrix) Z) E1 WHERE E1.RN = (SELECT COUNT(*) FROM TABLE(t.matrix)) - 1 + 1) AS corner
FROM
 check_complex t