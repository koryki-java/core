-- The same for the dialects that hold only JSON: the member of a path. See hostile_keys for how a
-- backslash is written: 'x\\' OR 1=1 --' is the value x\' OR 1=1 -- , and a backslash at the very
-- end, 'x\', has to be in the last item of the query.
SELECT
  t.nr
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$."x'' OR 1=1 --"'), JSON_VALUE(t.doc, '$."x'' OR 1=1 --"')) END AS quote_member
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$."a\\\\b"'), JSON_VALUE(t.doc, '$."a\\\\b"')) END AS backslash_member
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$."x\\\\'' OR 1=1 --"'), JSON_VALUE(t.doc, '$."x\\\\'' OR 1=1 --"')) END AS backslash_quote_member
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$."x\\"] || $ ; DROP TABLE check_complex; --"'), JSON_VALUE(t.doc, '$."x\\"] || $ ; DROP TABLE check_complex; --"')) END AS path_member
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' AND JSON_TYPE(JSON_QUERY(t.doc, '$.a')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.a."b.c"'), JSON_VALUE(t.doc, '$.a."b.c"')) END AS dotted_member
, CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$."x\\\\"'), JSON_VALUE(t.doc, '$."x\\\\"')) END AS trailing_member
FROM
 check_complex t