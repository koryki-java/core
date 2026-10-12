-- Keys written to break out of the literal or the path they end up in: a quote, a backslash, a
-- bracket, a terminator, a comment. Every one is just a key that is not there, so each answers NULL,
-- the statement is still one statement and the table is still there for the queries that follow.
SELECT
  t.nr
, GET(t.attrs, 'x'' OR 1=1 --') AS quote_key
, GET(t.attrs, 'x\\\\y') AS backslash_key
, GET(t.attrs, 'x"]; DROP TABLE check_complex; --') AS bracket_key
, GET_PATH(t.doc, '"x'' OR 1=1 --"') AS quote_member
, GET_PATH(t.doc, '"a\\\\\\\\b"') AS backslash_member
, GET_PATH(t.doc, '"x\\"] || $ ; DROP TABLE check_complex; --"') AS path_member
, GET_PATH(t.doc, 'a."b.c"') AS dotted_member
FROM
 check_complex t