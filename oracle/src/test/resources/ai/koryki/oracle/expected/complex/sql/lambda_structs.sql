-- The parameter is the element, whatever it is: a struct from a list of structs, and a step selects
-- from it. A lambda inside a lambda sees both parameters.
-- (Oracle cannot build a list of lists, so the nested transform is not here.)
SELECT
  t.nr
, CASE WHEN t.items IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X.price * LA0.X.qty FROM (SELECT VALUE(Z) X, ROWNUM I FROM TABLE(t.items) Z) LA0 ORDER BY LA0.I) AS SYS.ODCINUMBERLIST) END AS line_totals
, CASE WHEN t.items IS NULL THEN NULL ELSE CAST(MULTISET(SELECT LA0.X.sku FROM (SELECT VALUE(Z) X, ROWNUM I FROM TABLE(t.items) Z) LA0 ORDER BY LA0.I) AS SYS.ODCIVARCHAR2LIST) END AS skus
FROM
 check_complex t