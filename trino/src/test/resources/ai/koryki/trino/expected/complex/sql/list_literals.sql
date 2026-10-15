-- list(a, b, ...) makes a list out of values, in the order they are written: it is a list like any
-- other, so it is sorted, counted and searched.
SELECT
  t.nr
, array_sort(ARRAY[3, 1, 2]) AS sorted_literal
, cardinality(ARRAY[1, 2, 3]) AS n_literal
, (array_position(ARRAY['red', 'x'], 'red') > 0) AS has_red
FROM
 check_complex t