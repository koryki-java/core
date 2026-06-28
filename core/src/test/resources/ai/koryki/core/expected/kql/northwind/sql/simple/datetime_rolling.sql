-- Distances and Rolling (TEMPORAL.md): whole-unit distances, computed adds,
-- roll down to the unit start, roll up to the last day of the unit.
SELECT
  o.order_id
, date_trunc('month', o.order_date)
, last_day(o.order_date)
, (o.order_date + INTERVAL (1) MONTH)
, (CAST(EXTRACT(YEAR FROM age(o.shipped_date, o.order_date)) * 12 + EXTRACT(MONTH FROM age(o.shipped_date, o.order_date)) AS INTEGER))
FROM
 orders o
WHERE
  date_diff('day', o.order_date, o.shipped_date) > 7