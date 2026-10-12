-- Words that are reserved in SQL are legal names for a lambda parameter.
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_select * 2 FROM (SELECT t.scores[_i] AS _l0_select, _i AS _pos__l0_select FROM generate_subscripts(t.scores, 1) AS _i) AS _u ORDER BY _pos__l0_select) END AS doubled
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_end FROM (SELECT t.scores[_i] AS _l0_end, _i AS _pos__l0_end FROM generate_subscripts(t.scores, 1) AS _i) AS _u WHERE _l0_end > 1 ORDER BY _pos__l0_end) END AS big
FROM
 check_complex t