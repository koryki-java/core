-- Lists made by lists made by lists: each lambda takes the position as well as the element, which
-- names the list it works on more than once, so the SQL must evaluate it once or grow with every
-- nesting. The same on every dialect that can build the list.
SELECT
  t.nr
, (SELECT CASE WHEN BO_2.L IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x - _l0_i FROM (SELECT BO_2.L[_i] AS _l0_x, _i AS _l0_i FROM generate_subscripts(BO_2.L, 1) AS _i) AS _u ORDER BY _l0_i) END FROM (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 2 FROM (SELECT BO_1.L[_i] AS _l0_x, _i AS _l0_i FROM generate_subscripts(BO_1.L, 1) AS _i) AS _u ORDER BY _l0_i) END AS L FROM (SELECT CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x + _l0_i FROM (SELECT t.scores[_i] AS _l0_x, _i AS _l0_i FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _l0_i) END AS L) BO_1) BO_2) AS deep
FROM
 check_complex t