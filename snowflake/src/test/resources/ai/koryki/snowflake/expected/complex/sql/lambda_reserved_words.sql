-- Words that are reserved in SQL are legal names for a lambda parameter.
SELECT
  t.nr
, TRANSFORM(t.scores, _l0_select -> _l0_select * 2) AS doubled
, FILTER(t.scores, _l0_end -> _l0_end > 1) AS big
FROM
 check_complex t