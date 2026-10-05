-- A JSON value that is an expression, not a column, is evaluated once where the path asks for it
-- more than once.
SELECT
  t.nr
, (SELECT COALESCE(JSON_QUERY(BO_1.L, '$.n'), JSON_VALUE(BO_1.L, '$.n')) FROM (SELECT coalesce(t.doc, t.doc) AS L) BO_1) AS n
, (SELECT COALESCE(JSON_QUERY(BO_1.L, '$.a.b[0]'), JSON_VALUE(BO_1.L, '$.a.b[0]')) FROM (SELECT coalesce(t.doc, t.doc) AS L) BO_1) AS inner_first
FROM
 check_complex t