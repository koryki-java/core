-- Maps with text keys and with integer keys, text and integer values. An empty map is not NULL.
SELECT
  t.nr
, t.attrs
, t.counts
, t.lookup
FROM
 check_complex t