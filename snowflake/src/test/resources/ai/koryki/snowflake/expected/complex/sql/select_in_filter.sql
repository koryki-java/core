-- A selection is a value like any other: it filters, it compares with a literal.
SELECT
  t.nr
, GET(t.tags, 0) AS first_tag
FROM
 check_complex t
WHERE
  GET(t.attrs, 'color') = 'red'
 AND
  GET(t.scores, 1) > 10