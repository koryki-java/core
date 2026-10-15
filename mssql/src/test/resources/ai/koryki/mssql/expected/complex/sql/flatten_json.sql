-- An array inside JSON, flattened by a generated view: one row per element, joined back to the row.
SELECT
  t.nr
, b.ordinal
, b.value
, b.value * 10 AS tenfold
FROM
 check_complex_doc_a_b b
  INNER JOIN check_complex t ON
   b.nr = t.nr