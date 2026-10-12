-- Keys written to break out of the literal or the path they end up in: a quote, a backslash, a
-- bracket, a terminator, a comment. Every one is just a key that is not there, so each answers NULL,
-- the statement is still one statement and the table is still there for the queries that follow.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$."x'' OR 1=1 --"' RETURNING JSON) END AS quote_key
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$."x\\\\y"' RETURNING JSON) END AS backslash_key
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_QUERY(t.attrs, '$."x\"]; DROP TABLE check_complex; --"' RETURNING JSON) END AS bracket_key
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x'' OR 1=1 --"' RETURNING JSON) END AS quote_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."a\\\\b"' RETURNING JSON) END AS backslash_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x\"] || $ ; DROP TABLE check_complex; --"' RETURNING JSON) END AS path_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' THEN JSON_QUERY(t.doc, '$.a."b.c"' RETURNING JSON) END AS dotted_member
FROM
 check_complex t