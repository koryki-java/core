-- list(a, b, ...) makes a list out of values, in the order they are written: it is a list like any
-- other, so it is sorted, counted and searched.
SELECT
  t.nr
, ARRAY_SORT(ARRAY_CONSTRUCT(3, 1, 2)::ARRAY(INTEGER), TRUE, FALSE) AS sorted_literal
, ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2, 3)::ARRAY(INTEGER)) AS n_literal
, ARRAY_CONTAINS('red', ARRAY_CONSTRUCT('red', 'x')::ARRAY(VARCHAR)) AS has_red
FROM
 check_complex t