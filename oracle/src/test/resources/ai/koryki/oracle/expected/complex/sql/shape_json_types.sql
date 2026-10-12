-- Every scalar family a catalog may declare inside JSON (.s TEXT, .b BOOLEAN, .d DECIMAL, next to .n
-- INTEGER): none is in the data, so each is NULL, but each is read as its type, which the database
-- has to accept: a text without the quotes of a JSON string, a decimal that adds up.
SELECT
  t.nr
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.s') END AS s
, upper(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.s') END) AS shouted
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.b') END AS BOOLEAN) AS b
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.d') END AS DECIMAL(38, 10)) + 1 AS d_plus_one
, sum(CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.d') END AS DECIMAL(38, 10))) AS total
FROM
 check_complex t
GROUP BY
  t.nr
, CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.s') END
, upper(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.s') END)
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.b') END AS BOOLEAN)
, CAST(CASE WHEN JSON_VALUE(t.doc, '$.type()') = 'object' THEN JSON_VALUE(t.doc, '$.d') END AS DECIMAL(38, 10)) + 1