-- A range selects a list: @1:2 the first two, @2: from the second to the end, @:2 up to the second.
-- Both ends are included. The slice of a fixed-size array is a list.
SELECT
  t.nr
, ARRAY_SLICE(t.tags, 0, 2) AS first_two
, ARRAY_SLICE(t.tags, 1, ARRAY_SIZE(t.tags)) AS from_second
, ARRAY_SLICE(t.tags, 0, 2) AS up_to_second
, ARRAY_SLICE(t.vec, 0, 2) AS vec_head
FROM
 check_complex t