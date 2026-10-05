-- Containers inside containers: a list of structs with a DECIMAL and a list among its fields, a
-- list of structs with temporal fields, and four levels (a list of maps of structs holding a list
-- and a map of lists). The leaves keep their type: a price is a decimal, a timestamp a timestamp.
SELECT
  t.nr
, t.items
, t.events
, t.deep
FROM
 check_complex t