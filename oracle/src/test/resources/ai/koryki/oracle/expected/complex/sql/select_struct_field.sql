-- A name selects a struct field, a position the field at that place. @'...' takes a field whose
-- name needs quoting, and finds it regardless of case, as DuckDB does.
SELECT
  t.nr
, t.dims.w AS width
, t.dims.h AS height
, t.dims.w AS first_field
, t.dims.h AS last_field
, t.address.street AS street
, t.address.geo.lat AS latitude
, (SELECT E1.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(t.address.phones) Z) E1 WHERE E1.RN = 1) AS first_phone
, t.odd."Odd Name" AS odd_name
, t.odd."Odd Name" AS odd_name_upper
, t.odd.x AS odd_x
FROM
 check_complex t