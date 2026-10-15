-- Positions from 1, as in KQL; from the end through the length, which PostgreSQL has no negative
-- position for. A position outside the array is NULL. A range is inclusive, with open ends.
SELECT
  t.nr
, t.tags[1] AS first_tag
, t.tags[array_length(t.tags, 1)] AS last_tag
, t.tags[array_length(t.tags, 1) - 1] AS last_but_one
, t.tags[5] AS fifth_tag
, t.scores[2] AS second_score
, t.tags[1:2] AS first_two
, t.tags[2:] AS from_second
, t.tags[:2] AS up_to_second
, t.vec[3] AS vec_last
FROM
 check_complex t