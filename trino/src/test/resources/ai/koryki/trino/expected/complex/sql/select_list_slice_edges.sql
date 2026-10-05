-- A range that starts or ends before the first element, or reaches back further than the list is
-- long, is cut to the list: @-5: on three elements is all of them, @-5:-4 is none. The same on every
-- dialect; Trino's slice() reads a start of 0 as an error and a negative one as counting from the end.
SELECT
  t.nr
, slice(t.tags, greatest(cardinality(t.tags) + -4, 1), greatest(cardinality(t.tags) - (greatest(cardinality(t.tags) + -4, 1)) + 1, 0)) AS far_back
, slice(t.tags, greatest(cardinality(t.tags) + -1, 1), greatest(cardinality(t.tags) - (greatest(cardinality(t.tags) + -1, 1)) + 1, 0)) AS last_two
, slice(t.tags, 1, greatest(cardinality(t.tags) + -1 - (1) + 1, 0)) AS all_but_last
, slice(t.tags, greatest(cardinality(t.tags) + -4, 1), greatest(cardinality(t.tags) + -3 - (greatest(cardinality(t.tags) + -4, 1)) + 1, 0)) AS before_start
FROM
 check_complex t