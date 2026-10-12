-- Every scalar family a catalog may declare inside JSON (.s TEXT, .b BOOLEAN, .d DECIMAL, next to .n
-- INTEGER): none is in the data, so each is NULL, but each is read as its type, which the database
-- has to accept: a text without the quotes of a JSON string, a decimal that adds up.
SELECT
  t.nr
, COALESCE(JSON_QUERY(t.doc, '$.s'), JSON_VALUE(t.doc, '$.s')) AS s
, upper(COALESCE(JSON_QUERY(t.doc, '$.s'), JSON_VALUE(t.doc, '$.s'))) AS shouted
, CAST(COALESCE(JSON_QUERY(t.doc, '$.b'), JSON_VALUE(t.doc, '$.b')) AS BIT) AS b
, CAST(COALESCE(JSON_QUERY(t.doc, '$.d'), JSON_VALUE(t.doc, '$.d')) AS DECIMAL(38, 10)) + 1 AS d_plus_one
, sum(CAST(COALESCE(JSON_QUERY(t.doc, '$.d'), JSON_VALUE(t.doc, '$.d')) AS DECIMAL(38, 10))) AS total
FROM
 check_complex t
GROUP BY
  t.nr
, COALESCE(JSON_QUERY(t.doc, '$.s'), JSON_VALUE(t.doc, '$.s'))
, upper(COALESCE(JSON_QUERY(t.doc, '$.s'), JSON_VALUE(t.doc, '$.s')))
, CAST(COALESCE(JSON_QUERY(t.doc, '$.b'), JSON_VALUE(t.doc, '$.b')) AS BIT)
, CAST(COALESCE(JSON_QUERY(t.doc, '$.d'), JSON_VALUE(t.doc, '$.d')) AS DECIMAL(38, 10)) + 1