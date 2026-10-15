-- A name selects a struct field, a position the field at that place. @'...' takes a field whose
-- name needs quoting, and finds it regardless of case, as DuckDB does.
SELECT
  t.nr
, GET(t.dims, 'w') AS width
, GET(t.dims, 'h') AS height
, GET(t.dims, 'w') AS first_field
, GET(t.dims, 'h') AS last_field
, GET(t.address, 'street') AS street
, GET(GET(t.address, 'geo'), 'lat') AS latitude
, GET(GET(t.address, 'phones'), 0) AS first_phone
, GET(t.odd, 'Odd Name') AS odd_name
, GET(t.odd, 'Odd Name') AS odd_name_upper
, GET(t.odd, 'x') AS odd_x
FROM
 check_complex t