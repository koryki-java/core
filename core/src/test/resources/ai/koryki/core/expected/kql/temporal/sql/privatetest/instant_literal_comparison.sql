-- An INSTANT (timestamptz) column vs a timestamp literal: koryki reconciles the literal to its absolute
-- instant (the literal interpreted in the model zone) and renders it as each dialect's instant literal,
-- so the bare column stays index-friendly and the comparison does not rely on the engine implicitly
-- coercing a naive string -- which fails on a DATETIMEOFFSET column (SQL Server) or where the literal
-- syntax differs (Trino). Supported on every dialect.
SELECT
  c.timestamp_zoned AS instant
FROM
 check_temporal c
WHERE
  c.timestamp_zoned > TIMESTAMP '2024-06-01 00:00:00'