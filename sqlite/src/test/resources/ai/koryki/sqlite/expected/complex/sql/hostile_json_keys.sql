-- The same for the dialects that hold only JSON: the member of a path. See hostile_keys for how a
-- backslash is written: 'x\\' OR 1=1 --' is the value x\' OR 1=1 -- , and a backslash at the very
-- end, 'x\', has to be in the last item of the query.
SELECT
  t.nr
, (t.doc -> '$."x'' OR 1=1 --"') AS quote_member
, (t.doc -> '$."a\\b"') AS backslash_member
, (t.doc -> '$."x\\'' OR 1=1 --"') AS backslash_quote_member
, (t.doc -> '$."x\"] || $ ; DROP TABLE check_complex; --"') AS path_member
, (t.doc -> '$.a."b.c"') AS dotted_member
, (t.doc -> '$."x\\"') AS trailing_member
FROM
 check_complex t