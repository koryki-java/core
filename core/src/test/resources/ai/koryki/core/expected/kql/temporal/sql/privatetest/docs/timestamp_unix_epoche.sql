-- timestamp_unix_epoche: read an instant stored as Unix epoch seconds (EPOCH:SECONDS encoding).
SELECT
  c.timestamp_unix_epoche AS unix_instant
FROM
 check_temporal c