-- EPOCH:<unit> / DATE_FROM_EPOCH_DAY column vs a date/timestamp literal: the literal is reconciled to
-- the column's integer epoch count (index-friendly), so an INTEGER/BIGINT column is compared to an
-- integer, not a literal timestamp. Selective filter — only the 2025 row exceeds all three bounds.
-- (INSTANT columns are zone-aware; their literal-side conversion is the not-yet-wired zone subsystem.)
SELECT
  c.timestamp_unix_epoche AS unix_epoch
FROM
 check_temporal c
WHERE
  c.date_epoch_day > 19875
 AND
  c.timestamp_java_epoche > 1717200000000
 AND
  c.timestamp_unix_epoche > 1717200000