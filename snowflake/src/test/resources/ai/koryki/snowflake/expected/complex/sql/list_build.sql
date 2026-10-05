-- list_agg(x) makes a list out of the rows of a group. It leaves NULLs out and promises no order, so what
-- is shown is sorted: the first tags of the rows are red and x (row 2 has no first tag), the second
-- scores 5 and 20 (row 2 has none).
SELECT
  ARRAY_SORT(CASE WHEN COUNT(t.nr) = 0 THEN NULL ELSE ARRAY_AGG(t.nr)::ARRAY(INTEGER) END, TRUE, FALSE) AS all_nrs
, ARRAY_SORT(CASE WHEN COUNT(GET(t.tags, 0)) = 0 THEN NULL ELSE ARRAY_AGG(GET(t.tags, 0))::ARRAY(VARCHAR) END, TRUE, FALSE) AS first_tags
, ARRAY_SORT(CASE WHEN COUNT(GET(t.scores, 1)) = 0 THEN NULL ELSE ARRAY_AGG(GET(t.scores, 1))::ARRAY(INTEGER) END, TRUE, FALSE) AS second_scores
FROM
 check_complex t