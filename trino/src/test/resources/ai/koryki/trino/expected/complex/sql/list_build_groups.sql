-- One list per group. A group whose values are all NULL has nothing to collect: row 2 has no first tag.
SELECT
  t.nr
, array_agg(element_at(t.tags, 1)) FILTER (WHERE element_at(t.tags, 1) IS NOT NULL) AS first_tag
FROM
 check_complex t
GROUP BY
  t.nr