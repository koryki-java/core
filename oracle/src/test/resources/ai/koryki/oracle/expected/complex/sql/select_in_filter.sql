-- A selection is a value like any other: it filters, it compares with a literal.
SELECT
  t.nr
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 1) AS first_tag
FROM
 check_complex t
WHERE
  t.address.street = 'Main St 1'
 AND
  (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.scores) Z) E1 WHERE E1.RN = 2) > 10