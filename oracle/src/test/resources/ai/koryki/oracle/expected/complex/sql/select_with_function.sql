-- A function on a selection and a selection on a function: coalesce keeps the structure of its
-- branches, so its result can be selected from.
SELECT
  t.nr
, upper((SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.tags) Z) E1 WHERE E1.RN = 1)) AS shouted
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(coalesce(t.tags, t.tags)) Z) E1 WHERE E1.RN = 1) AS first_tag
, (SELECT E1.V.price FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1) * 2 AS double_price
FROM
 check_complex t