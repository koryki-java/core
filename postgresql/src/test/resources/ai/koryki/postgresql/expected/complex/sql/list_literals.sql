-- list(a, b, ...) makes a list out of values, in the order they are written: it is a list like any
-- other, so it is sorted, counted and searched.
SELECT
  t.nr
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(BO_1.L) AS _u(_e) ORDER BY _e NULLS LAST) END FROM (SELECT ARRAY[3, 1, 2] AS L) BO_1) AS sorted_literal
, cardinality(ARRAY[1, 2, 3]) AS n_literal
, (ARRAY['red', 'x'] @> ARRAY['red']) AS has_red
FROM
 check_complex t