-- A range selects a list: @1:2 the first two, @2: from the second to the end, @:2 up to the second.
-- Both ends are included. The slice of a fixed-size array is a list.
SELECT
  t.nr
, slice(t.tags, 1, 2) AS first_two
, slice(t.tags, 2, greatest(cardinality(t.tags) - (2) + 1, 0)) AS from_second
, slice(t.tags, 1, greatest(2 - (1) + 1, 0)) AS up_to_second
, slice(t.vec, 1, 2) AS vec_head
FROM
 check_complex t