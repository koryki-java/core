-- A composite type comes back as a struct, field by field in the types of the catalog: a struct
-- inside a struct, a list inside one, an array of composites, temporal fields, a quoted field name.
SELECT
  t.nr
, t.dims
, t.address
, t.items
, t.events
, t.odd
FROM
 check_complex t