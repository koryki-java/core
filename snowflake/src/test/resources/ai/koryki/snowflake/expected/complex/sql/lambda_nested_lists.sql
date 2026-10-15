-- Lists made by lists made by lists: each lambda takes the position as well as the element, which
-- names the list it works on more than once, so the SQL must evaluate it once or grow with every
-- nesting. The same on every dialect that can build the list.
SELECT
  t.nr
, (SELECT TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(BO_2.L) + 1), _l0_i -> GET(BO_2.L, CAST(_l0_i AS INTEGER) - 1) - _l0_i) FROM (SELECT TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(BO_1.L) + 1), _l0_i -> GET(BO_1.L, CAST(_l0_i AS INTEGER) - 1) * 2) AS L FROM (SELECT TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(t.scores) + 1), _l0_i -> GET(t.scores, CAST(_l0_i AS INTEGER) - 1) + _l0_i) AS L) BO_1) BO_2) AS deep
FROM
 check_complex t