-- list_agg(x) makes a list out of the rows of a group. It leaves NULLs out and promises no order, so what
-- is shown is sorted: the first tags of the rows are red and x (row 2 has no first tag), the second
-- scores 5 and 20 (row 2 has none).
SELECT
  array_sort(array_agg(t.nr) FILTER (WHERE t.nr IS NOT NULL)) AS all_nrs
, array_sort(array_agg(element_at(t.tags, 1)) FILTER (WHERE element_at(t.tags, 1) IS NOT NULL)) AS first_tags
, array_sort(array_agg(element_at(t.scores, 2)) FILTER (WHERE element_at(t.scores, 2) IS NOT NULL)) AS second_scores
FROM
 check_complex t