-- A lambda sees the columns of the query as well as its parameters: here the row's own nr, added to
-- every score, and every score combined with every score.
SELECT
  t.nr
, transform(t.scores, _l0_x -> _l0_x + t.nr) AS shifted
, transform(t.scores, _l0_x -> transform(t.scores, _l1_y -> _l0_x + _l1_y)) AS sums
FROM
 check_complex t