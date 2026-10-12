-- Every scalar family a catalog may declare inside JSON (.s TEXT, .b BOOLEAN, .d DECIMAL, next to .n
-- INTEGER): none is in the data, so each is NULL, but each is read as its type, which the database
-- has to accept: a text without the quotes of a JSON string, a decimal that adds up.
SELECT
  t.nr
, (t.doc ->> 's') AS s
, upper((t.doc ->> 's')) AS shouted
, CAST((t.doc ->> 'b') AS BOOLEAN) AS b
, CAST((t.doc ->> 'd') AS DECIMAL(38, 10)) + 1 AS d_plus_one
, sum(CAST((t.doc ->> 'd') AS DECIMAL(38, 10))) AS total
FROM
 check_complex t
GROUP BY
  t.nr
, (t.doc ->> 's')
, upper((t.doc ->> 's'))
, CAST((t.doc ->> 'b') AS BOOLEAN)
, CAST((t.doc ->> 'd') AS DECIMAL(38, 10)) + 1