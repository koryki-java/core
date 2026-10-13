-- Steps follow the structure to any depth: a list of structs, a struct holding a list, a list of
-- maps of structs holding a list and a map of lists. Each step is checked against the type it meets.
-- (A list of objects cannot be sliced on Oracle, and the map inside deep is a JSON object with the text key '1'.)
SELECT
  t.nr
, (SELECT E1.V.sku FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1) AS first_sku
, (SELECT E1.V.price FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1) AS first_price
, (SELECT (SELECT E2.V FROM (SELECT COLUMN_VALUE V, ROWNUM RN FROM TABLE(E1.V.opts) Z) E2 WHERE E2.RN = 2) FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 1) AS second_option
, (SELECT E1.V.opts FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.items) Z) E1 WHERE E1.RN = 2) AS empty_options
, (SELECT E1.V.ts FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.events) Z) E1 WHERE E1.RN = 1) AS first_event
, (SELECT E1.V.day FROM (SELECT VALUE(Z) V, ROWNUM RN FROM TABLE(t.events) Z) E1 WHERE E1.RN = 1) AS first_day
, CAST(CASE WHEN JSON_VALUE(t.deep, '$.type()') = 'array' AND JSON_VALUE(t.deep, '$[0].type()') = 'object' AND JSON_VALUE(t.deep, '$[0].m.type()') = 'object' AND JSON_VALUE(t.deep, '$[0].m.a.type()') = 'array' THEN JSON_VALUE(t.deep, '$[0].m.a[1]') END AS INTEGER) AS deep_number
, CASE WHEN JSON_VALUE(t.deep, '$.type()') = 'array' AND JSON_VALUE(t.deep, '$[0].type()') = 'object' AND JSON_VALUE(t.deep, '$[0].m.type()') = 'object' AND JSON_VALUE(t.deep, '$[0].m.b.type()') = 'object' AND JSON_VALUE(t.deep, '$[0].m.b."1".type()') = 'array' THEN JSON_VALUE(t.deep, '$[0].m.b."1"[0]') END AS deep_text
FROM
 check_complex t