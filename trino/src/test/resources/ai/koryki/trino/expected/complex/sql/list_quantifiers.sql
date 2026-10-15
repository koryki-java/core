-- Whether some or every element passes a test. An empty list has no element that passes and every
-- element of it does; a test that is NULL does not pass (row 3 has NULL scores and a NULL quantity).
SELECT
  t.nr
, (cardinality(filter(t.scores, _l0_x -> _l0_x > 25)) > 0) AS any_big
, (cardinality(filter(t.scores, _l0_x -> _l0_x > 5)) = cardinality(t.scores)) AS all_big
, (cardinality(filter(t.items, _l0_it -> (_l0_it).qty > 1)) > 0) AS any_many
, (cardinality(filter(t.items, _l0_it -> (_l0_it).qty > 0)) = cardinality(t.items)) AS all_stocked
FROM
 check_complex t