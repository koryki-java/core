-- Whether some or every element passes a test. An empty list has no element that passes and every
-- element of it does; a test that is NULL does not pass (row 3 has NULL scores and a NULL quantity).
SELECT
  t.nr
, (ARRAY_SIZE(FILTER(t.scores, _l0_x -> _l0_x > 25)) > 0) AS any_big
, (ARRAY_SIZE(FILTER(t.scores, _l0_x -> _l0_x > 5)) = ARRAY_SIZE(t.scores)) AS all_big
, (ARRAY_SIZE(FILTER(t.items, _l0_it -> GET(_l0_it, 'qty') > 1)) > 0) AS any_many
, (ARRAY_SIZE(FILTER(t.items, _l0_it -> GET(_l0_it, 'qty') > 0)) = ARRAY_SIZE(t.items)) AS all_stocked
FROM
 check_complex t