-- list_agg(x) makes a list out of the rows of a group. It leaves NULLs out and promises no order, so what
-- is shown is sorted: the first tags of the rows are red and x (row 2 has no first tag), the second
-- scores 5 and 20 (row 2 has none).
SELECT
  (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(BO_1.L) AS _u(_e) ORDER BY _e NULLS LAST) END FROM (SELECT array_agg(t.nr) FILTER (WHERE t.nr IS NOT NULL) AS L) BO_1) AS all_nrs
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(BO_1.L) AS _u(_e) ORDER BY _e NULLS LAST) END FROM (SELECT array_agg(t.tags[1]) FILTER (WHERE t.tags[1] IS NOT NULL) AS L) BO_1) AS first_tags
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _e FROM unnest(BO_1.L) AS _u(_e) ORDER BY _e NULLS LAST) END FROM (SELECT array_agg(t.scores[2]) FILTER (WHERE t.scores[2] IS NOT NULL) AS L) BO_1) AS second_scores
FROM
 check_complex t