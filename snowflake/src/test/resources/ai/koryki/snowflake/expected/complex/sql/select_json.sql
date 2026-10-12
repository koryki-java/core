-- (Snowflake's JSON paths have no position from the end, so there is no @-1 here.)
-- A selector on JSON is a path. The one count that differs from the lists: the SQL of JSON counts from
-- 0, KQL from 1, and the rendering takes care of it. The result is JSON again, so the object in row 1
-- and the array in row 3 both answer.
SELECT
  t.nr
, CAST((GET_PATH(t.doc, 'n'))::STRING AS INTEGER) AS n
, GET_PATH(t.doc, 'a.b') AS inner_list
, CAST((GET_PATH(t.doc, 'a.b[0]'))::STRING AS INTEGER) AS inner_first
, GET_PATH(t.doc, '[0]') AS array_first
FROM
 check_complex t