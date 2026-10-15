-- The list can be anything that is a list, a lambda's result as well as a column. Row 2 has an empty
-- list, row 3 NULL elements among its values: nothing matches, and the sum of what is left is 10.
SELECT
  t.nr
, (array_position(transform(t.tags, _l0_x -> upper(_l0_x)), 'RED') > 0) AS has_red_upper
, cardinality(filter(t.scores, _l0_x -> _l0_x > 5)) AS big
, (SELECT sum(_e) FROM UNNEST(transform(t.scores, _l0_x -> _l0_x * 2)) AS _u(_e)) AS doubled_total
FROM
 check_complex t