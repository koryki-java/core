-- A JSON string is its text without the quotes once it is cast, so it compares with a text literal:
-- the map of this dialect is a JSON object, and its member is no TEXT until to_text says so.
SELECT
  t.nr
, CAST((t.attrs ->> 'color') AS TEXT) AS color
, CAST((t.attrs ->> 'size') AS TEXT) AS size
FROM
 check_complex t
WHERE
  CAST((t.attrs ->> 'color') AS TEXT) = 'red'