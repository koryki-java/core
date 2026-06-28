-- PostgreSQL math chapter as functions (no operator forms in KQL):
-- mod/sqrt/power instead of % |/ ^, plus the round(value, scale) overload.
SELECT
  o.order_id
, round(o.freight, 2)
, sqrt(o.freight)
, power(o.freight, 2)
FROM
 orders o
WHERE
  mod(o.order_id, 7) = 0