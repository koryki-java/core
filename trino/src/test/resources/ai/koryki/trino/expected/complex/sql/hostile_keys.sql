-- Keys written to break out of the literal or the path they end up in: a quote, a backslash, a
-- bracket, a terminator, a comment. Every one is just a key that is not there, so each answers NULL,
-- the statement is still one statement and the table is still there for the queries that follow.
-- KQL has one escape, \', and a backslash is otherwise itself. So 'x\\' OR 1=1 --' is the value
-- x\' OR 1=1 -- : a backslash, then a quote, the shape that leaves its literal on an engine that
-- reads a backslash as an escape. A backslash at the very end is written 'x\', which the lexer reads
-- as a backslash only while no later quote is left in the query, so that item has to be the last.
SELECT
  t.nr
, element_at(t.attrs, 'x'' OR 1=1 --') AS quote_key
, element_at(t.attrs, 'x\y') AS backslash_key
, element_at(t.attrs, 'x\'' OR 1=1 --') AS backslash_quote_key
, element_at(t.attrs, 'x"]; DROP TABLE check_complex; --') AS bracket_key
, json_extract(t.doc, '$["x'' OR 1=1 --"]') AS quote_member
, json_extract(t.doc, '$["a\\b"]') AS backslash_member
, json_extract(t.doc, '$["x\\'' OR 1=1 --"]') AS backslash_quote_member
, json_extract(t.doc, '$["x\"] || $ ; DROP TABLE check_complex; --"]') AS path_member
, json_extract(t.doc, '$.a["b.c"]') AS dotted_member
, json_extract(t.doc, '$["x\\"]') AS trailing_member
FROM
 check_complex t