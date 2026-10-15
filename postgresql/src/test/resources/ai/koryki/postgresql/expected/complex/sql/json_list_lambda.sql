-- A lambda over a JSON array: its elements are the declared integers.
SELECT
  t.nr
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x * 10 FROM (SELECT BO_1.L[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(BO_1.L, 1) AS _i) AS _u ORDER BY _pos__l0_x) END FROM (SELECT CASE WHEN jsonb_typeof((t.doc -> 'a' -> 'b')) = 'array' THEN ARRAY(SELECT CAST(e.v AS INTEGER) FROM jsonb_array_elements_text((t.doc -> 'a' -> 'b')) WITH ORDINALITY AS e(v, i) ORDER BY e.i) END AS L) BO_1) AS tenfold
, (SELECT CASE WHEN BO_1.L IS NULL THEN NULL ELSE ARRAY(SELECT _l0_x FROM (SELECT BO_1.L[_i] AS _l0_x, _i AS _pos__l0_x FROM generate_subscripts(BO_1.L, 1) AS _i) AS _u WHERE _l0_x > 5 ORDER BY _pos__l0_x) END FROM (SELECT CASE WHEN jsonb_typeof((t.doc -> 'a' -> 'b')) = 'array' THEN ARRAY(SELECT CAST(e.v AS INTEGER) FROM jsonb_array_elements_text((t.doc -> 'a' -> 'b')) WITH ORDINALITY AS e(v, i) ORDER BY e.i) END AS L) BO_1) AS big
FROM
 check_complex t
WHERE
  cardinality(CASE WHEN jsonb_typeof((t.doc -> 'a' -> 'b')) = 'array' THEN ARRAY(SELECT CAST(e.v AS INTEGER) FROM jsonb_array_elements_text((t.doc -> 'a' -> 'b')) WITH ORDINALITY AS e(v, i) ORDER BY e.i) END) > 0