-- One list per group. A group whose values are all NULL has nothing to collect: row 2 has no first tag.
SELECT
  t.nr
, CASE WHEN COUNT(GET(t.tags, 0)) = 0 THEN NULL ELSE ARRAY_AGG(GET(t.tags, 0))::ARRAY(VARCHAR) END AS first_tag
FROM
 check_complex t
GROUP BY
  t.nr