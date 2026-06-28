-- to_date / to_time / to_timestamp construction and parsing overloads.
SELECT
  o.order_id
, TO_DATE(EXTRACT(YEAR FROM o.order_date) || '-' || EXTRACT(MONTH FROM o.order_date) || '-' || 1, 'YYYY-MM-DD') AS month_start
, CAST(o.delivered_date AS DATE) AS delivery_day
, TO_DATE('2022-07-16', 'YYYY-MM-DD') AS literal_date
, LPAD(8, 2, '0') || ':' || LPAD(30, 2, '0') || ':' || LPAD(0, 2, '0') AS work_start
, TO_CHAR(o.delivered_date, 'HH24:MI:SS') AS delivery_time
, TO_CHAR(TO_TIMESTAMP('08:30:00', 'HH24:MI:SS'), 'HH24:MI:SS') AS literal_time
, TO_TIMESTAMP(EXTRACT(YEAR FROM o.order_date) || '-' || EXTRACT(MONTH FROM o.order_date) || '-' || EXTRACT(DAY FROM o.order_date) || ' ' || 12 || ':' || 0 || ':' || 0, 'YYYY-MM-DD HH24:MI:SS') AS noon
, CAST(o.order_date AS TIMESTAMP) AS order_ts
, TO_TIMESTAMP('2022-07-16 12:00:00', 'YYYY-MM-DD HH24:MI:SS') AS literal_ts
, CAST(FROM_TZ(CAST(o.delivered_date AS TIMESTAMP), 'UTC') AT TIME ZONE 'Europe/Berlin' AS DATE) AS delivery_day_tz
, TO_CHAR(FROM_TZ(CAST(o.delivered_date AS TIMESTAMP), 'UTC') AT TIME ZONE 'Europe/Berlin', 'HH24:MI:SS') AS delivery_time_tz
, FROM_TZ(TO_TIMESTAMP('2022-07-16 12:00:00', 'YYYY-MM-DD HH24:MI:SS'), 'UTC') AT TIME ZONE 'Europe/Berlin' AS literal_ts_tz
, FROM_TZ(TO_TIMESTAMP(EXTRACT(YEAR FROM o.order_date) || '-' || EXTRACT(MONTH FROM o.order_date) || '-' || EXTRACT(DAY FROM o.order_date) || ' ' || 12 || ':' || 0 || ':' || 0, 'YYYY-MM-DD HH24:MI:SS'), 'UTC') AT TIME ZONE 'Europe/Berlin' AS noon_tz
, NUMTODSINTERVAL(30, 'DAY') AS thirty_days
, NUMTOYMINTERVAL(3, 'MONTH') AS three_months
, NUMTODSINTERVAL(2, 'HOUR') AS two_hours
, NUMTODSINTERVAL(90, 'SECOND') AS ninety_seconds
, NUMTODSINTERVAL(500 / 1000.0, 'SECOND') AS half_second
, o.order_date + NUMTODSINTERVAL(30, 'DAY') AS due_date
, o.order_date + NUMTOYMINTERVAL(1, 'MONTH') AS next_month
, o.order_date + NUMTODSINTERVAL(2, 'DAY') + NUMTODSINTERVAL(4, 'HOUR') AS later
, ADD_MONTHS(o.order_date, 14) + NUMTODSINTERVAL(15, 'DAY') AS far_future
, o.order_date + NUMTODSINTERVAL(1, 'HOUR') + NUMTODSINTERVAL(30, 'MINUTE') AS meeting_end
FROM
 orders o
WHERE
  o.order_id <= 10250
