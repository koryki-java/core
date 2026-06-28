-- date_epoch_day: read a DATE stored as days since 1970-01-01 (DATE_FROM_EPOCH_DAY encoding).
SELECT
  c.date_epoch_day AS day
FROM
 check_temporal c