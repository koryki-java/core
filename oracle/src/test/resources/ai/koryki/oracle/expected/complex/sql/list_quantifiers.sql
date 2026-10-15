-- Whether some or every element passes a test. An empty list has no element that passes and every
-- element of it does; a test that is NULL does not pass (row 3 has NULL scores and a NULL quantity).
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM (SELECT COLUMN_VALUE X FROM TABLE(t.scores) Z) LA0 WHERE LA0.X > 25) > 0) END AS any_big
, CASE WHEN t.scores IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM (SELECT COLUMN_VALUE X FROM TABLE(t.scores) Z) LA0 WHERE (LA0.X > 5) IS NOT TRUE) = 0) END AS all_big
, CASE WHEN t.items IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM (SELECT VALUE(Z) X FROM TABLE(t.items) Z) LA0 WHERE LA0.X.qty > 1) > 0) END AS any_many
, CASE WHEN t.items IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM (SELECT VALUE(Z) X FROM TABLE(t.items) Z) LA0 WHERE (LA0.X.qty > 0) IS NOT TRUE) = 0) END AS all_stocked
FROM
 check_complex t