-- Every scalar family a catalog may declare inside JSON (.s TEXT, .b BOOLEAN, .d DECIMAL, next to .n
-- INTEGER): none is in the data, so each is NULL, but each is read as its type, which the database
-- has to accept: a text without the quotes of a JSON string, a decimal that adds up.
SELECT
  t.nr
, (GET_PATH(t.doc, 's'))::STRING AS s
, upper((GET_PATH(t.doc, 's'))::STRING) AS shouted
, CAST((GET_PATH(t.doc, 'b'))::STRING AS BOOLEAN) AS b
, CAST((GET_PATH(t.doc, 'd'))::STRING AS DECIMAL(38, 10)) + 1 AS d_plus_one
, sum(CAST((GET_PATH(t.doc, 'd'))::STRING AS DECIMAL(38, 10))) AS total
FROM
 check_complex t
GROUP BY
  t.nr
, (GET_PATH(t.doc, 's'))::STRING
, upper((GET_PATH(t.doc, 's'))::STRING)
, CAST((GET_PATH(t.doc, 'b'))::STRING AS BOOLEAN)
, CAST((GET_PATH(t.doc, 'd'))::STRING AS DECIMAL(38, 10)) + 1