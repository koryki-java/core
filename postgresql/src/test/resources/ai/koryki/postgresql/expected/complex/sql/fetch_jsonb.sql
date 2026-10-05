-- PostgreSQL has no map: the map columns and the list of maps are jsonb, which is JSON here.
SELECT
  t.nr
, t.attrs
, t.counts
, t.lookup
, t.deep
, t.doc
FROM
 check_complex t