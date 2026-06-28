-- TIME stored as an HHMMSS integer (TIME_FROM_INTEGER): arithmetic decodes to seconds, adds, re-wraps.
SELECT
  c.time_from_integer AS time_from_integer
, CAST(FLOOR(c.time_from_integer / 10000.0) AS INTEGER) * 3600 + MOD(CAST(FLOOR(c.time_from_integer / 100.0) AS INTEGER), 100) * 60 + MOD(c.time_from_integer, 100) + 3600 AS time_from_integer_plus1h
, CAST(FLOOR(c.time_from_integer / 10000.0) AS INTEGER) * 3600 + MOD(CAST(FLOOR(c.time_from_integer / 100.0) AS INTEGER), 100) * 60 + MOD(c.time_from_integer, 100) - 3600 AS time_from_integer_minus1h
FROM
 check_temporal c