-- A JSON string is its text without the quotes once it is cast, so it compares with a text literal:
-- the map of this dialect is a JSON object, and its member is no TEXT until to_text says so.
SELECT
  t.nr
, CAST(CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.color') END AS VARCHAR(4000)) AS color
, CAST(CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.size') END AS VARCHAR(4000)) AS "size"
FROM
 check_complex t
WHERE
  CAST(CASE WHEN JSON_VALUE(t.attrs, '$.type()') = 'object' THEN JSON_VALUE(t.attrs, '$.color') END AS VARCHAR(4000)) = 'red'