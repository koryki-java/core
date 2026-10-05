-- A cast of what the JSON does not have is NULL, not an error: row 2 has no document, row 1 is an
-- object and has no position 1, row 3 is an array and has no member n.
SELECT
  t.nr
, CAST(json_extract_scalar(t.doc, '$.n') AS INTEGER) AS n
, CAST(json_extract_scalar(t.doc, '$[0]') AS INTEGER) AS first_element
FROM
 check_complex t