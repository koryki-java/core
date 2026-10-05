-- A selection is a value like any other: it filters, it compares with a literal.
SELECT
  t.nr
, element_at(t.tags, 1) AS first_tag
FROM
 check_complex t
WHERE
  element_at(t.scores, 2) > 10
 AND
  element_at(t.attrs, 'color') = 'red'