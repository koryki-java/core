-- The elements of the JSON array are rows: a sum, a count and an extreme over them.
SELECT
  count(b.ordinal) AS elements
, sum(b.value) AS total
, max(b.value) AS biggest
FROM
 check_complex_doc_a_b b