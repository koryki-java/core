-- Every scalar family a catalog may declare inside JSON (.s TEXT, .b BOOLEAN, .d DECIMAL, next to .n
-- INTEGER): none is in the data, so each is NULL, but each is read as its type, which the database
-- has to accept: a text without the quotes of a JSON string, a decimal that adds up.
SELECT
  t.nr
, json_extract_scalar(t.doc, '$.s') AS s
, upper(json_extract_scalar(t.doc, '$.s')) AS shouted
, CAST(json_extract_scalar(t.doc, '$.b') AS BOOLEAN) AS b
, CAST(json_extract_scalar(t.doc, '$.d') AS DECIMAL(38, 10)) + 1 AS d_plus_one
, sum(CAST(json_extract_scalar(t.doc, '$.d') AS DECIMAL(38, 10))) AS total
FROM
 check_complex t
GROUP BY
  t.nr
, json_extract_scalar(t.doc, '$.s')
, upper(json_extract_scalar(t.doc, '$.s'))
, CAST(json_extract_scalar(t.doc, '$.b') AS BOOLEAN)
, CAST(json_extract_scalar(t.doc, '$.d') AS DECIMAL(38, 10)) + 1