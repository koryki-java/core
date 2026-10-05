-- A struct list flattened by a generated view: one row per element, joined back to its parent.
SELECT
  t.nr
, i.ordinal
, i.sku
, i.qty * i.price AS total
FROM
 check_complex_items i
  INNER JOIN check_complex t ON
   i.nr = t.nr