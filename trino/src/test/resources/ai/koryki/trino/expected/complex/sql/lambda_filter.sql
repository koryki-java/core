-- list_filter keeps the elements for which the test is true, in order. An element the test cannot
-- decide, a NULL, is not kept. The same lambda works on a list of structs.
SELECT
  t.nr
, filter(t.scores, _l0_x -> _l0_x > 10) AS big
, filter(t.tags, _l0_x -> _l0_x <> 'green') AS without_green
, filter(t.items, _l0_it -> (_l0_it).qty > 1) AS many
FROM
 check_complex t