-- A cast of what the JSON does not have is NULL, not an error: row 2 has no document, row 1 is an
-- object and has no position 1, row 3 is an array and has no member n.
SELECT
  t.nr
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER) AS n
, CAST(CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$[0]'), JSON_VALUE(t.doc, '$[0]')) END AS INTEGER) AS first_element
FROM
 check_complex t