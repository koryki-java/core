-- A lambda sees the columns of the query: the row's own nr added to every score, and compared with
-- every score.
SELECT
  t.nr
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x + t.nr FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.scores AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u ORDER BY _pos__l0_x) END AS shifted
, CASE WHEN t.scores IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT _s._a[_i] AS _l0_x, _i AS _pos__l0_x FROM (SELECT t.scores AS _a) AS _s, generate_subscripts(_s._a, 1) AS _i) AS _u WHERE _l0_x > t.nr * 5 ORDER BY _pos__l0_x) END AS above_five_times_nr
FROM
 check_complex t