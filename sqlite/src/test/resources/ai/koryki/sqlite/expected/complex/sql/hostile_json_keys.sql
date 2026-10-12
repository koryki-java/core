-- The same for the dialects that hold only JSON: the member of a path.
SELECT
  t.nr
, (t.doc -> '$."x'' OR 1=1 --"') AS quote_member
, (t.doc -> '$."a\\\\b"') AS backslash_member
, (t.doc -> '$."x\"] || $ ; DROP TABLE check_complex; --"') AS path_member
, (t.doc -> '$.a."b.c"') AS dotted_member
FROM
 check_complex t