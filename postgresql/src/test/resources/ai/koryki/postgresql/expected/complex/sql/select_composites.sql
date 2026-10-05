-- A name selects a field of a composite type, a position the field at that place, which has no SQL
-- of its own: the name is read from the type. @'...' finds a field whatever the case it is written in.
SELECT
  t.nr
, (t.dims).w AS width
, (t.dims).w AS first_field
, (t.dims).h AS last_field
, (t.address).street AS street
, ((t.address).geo).lat AS latitude
, ((t.address).phones)[1] AS first_phone
, (t.odd)."Odd Name" AS odd_name
, (t.odd)."Odd Name" AS odd_name_upper
, (t.odd).x AS odd_x
FROM
 check_complex t