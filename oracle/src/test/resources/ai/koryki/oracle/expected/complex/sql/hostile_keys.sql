-- Keys written to break out of the literal or the path they end up in: a quote, a backslash, a
-- bracket, a terminator, a comment. Every one is just a key that is not there, so each answers NULL,
-- the statement is still one statement and the table is still there for the queries that follow.
-- KQL has one escape, \', and a backslash is otherwise itself. So 'x\\' OR 1=1 --' is the value
-- x\' OR 1=1 -- : a backslash, then a quote, the shape that leaves its literal on an engine that
-- reads a backslash as an escape. A backslash at the very end is written 'x\', which the lexer reads
-- as a backslash only while no later quote is left in the query, so that item has to be the last.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$."x'' OR 1=1 --"') END AS quote_key
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$."x\\y"') END AS backslash_key
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$."x\\'' OR 1=1 --"') END AS backslash_quote_key
, CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$."x\"]; DROP TABLE check_complex; --"') END AS bracket_key
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x'' OR 1=1 --"' RETURNING JSON) END AS quote_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."a\\b"' RETURNING JSON) END AS backslash_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x\\'' OR 1=1 --"' RETURNING JSON) END AS backslash_quote_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x\"] || $ ; DROP TABLE check_complex; --"' RETURNING JSON) END AS path_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' AND JSON_VALUE(t.doc, '$.a.type()') = 'object' THEN JSON_QUERY(t.doc, '$.a."b.c"' RETURNING JSON) END AS dotted_member
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_QUERY(t.doc, '$."x\\"' RETURNING JSON) END AS trailing_member
FROM
 check_complex t