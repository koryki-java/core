-- The same for the dialects that hold only JSON: the member of a path.
SELECT
  t.nr
, COALESCE(JSON_QUERY(t.doc, '$."x'' OR 1=1 --"'), JSON_VALUE(t.doc, '$."x'' OR 1=1 --"')) AS quote_member
, COALESCE(JSON_QUERY(t.doc, '$."a\\\\b"'), JSON_VALUE(t.doc, '$."a\\\\b"')) AS backslash_member
, COALESCE(JSON_QUERY(t.doc, '$."x\"] || $ ; DROP TABLE check_complex; --"'), JSON_VALUE(t.doc, '$."x\"] || $ ; DROP TABLE check_complex; --"')) AS path_member
, COALESCE(JSON_QUERY(t.doc, '$.a."b.c"'), JSON_VALUE(t.doc, '$.a."b.c"')) AS dotted_member
FROM
 check_complex t