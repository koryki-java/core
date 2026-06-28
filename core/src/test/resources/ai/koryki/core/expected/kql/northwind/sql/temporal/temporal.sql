-- to_date / to_time / to_timestamp construction and parsing overloads.
SELECT
  o.order_id
, MAKE_DATE(year(o.order_date), month(o.order_date), 1) AS month_start
, CAST(o.delivered_date AS DATE) AS delivery_day
, strptime('2022-07-16', '%Y-%m-%d')::DATE AS literal_date
, MAKE_TIME(8, 30, 0::DOUBLE) AS work_start
, CAST(o.delivered_date AS TIME) AS delivery_time
, strptime('08:30:00', '%H:%M:%S')::TIME AS literal_time
, MAKE_TIMESTAMP(year(o.order_date), month(o.order_date), day(o.order_date), 12, 0, 0) AS noon
, CAST(o.order_date AS TIMESTAMP) AS order_ts
, strptime('2022-07-16 12:00:00', '%Y-%m-%d %H:%M:%S') AS literal_ts
, timezone('Europe/Berlin', o.delivered_date::TIMESTAMPTZ)::DATE AS delivery_day_tz
, timezone('Europe/Berlin', o.delivered_date::TIMESTAMPTZ)::TIME AS delivery_time_tz
, timezone('Europe/Berlin', strptime('2022-07-16 12:00:00', '%Y-%m-%d %H:%M:%S')::TIMESTAMPTZ) AS literal_ts_tz
, timezone('Europe/Berlin', MAKE_TIMESTAMP(year(o.order_date), month(o.order_date), day(o.order_date), 12, 0, 0)::TIMESTAMPTZ) AS noon_tz
, to_days(30) AS thirty_days
, to_months(3) AS three_months
, to_hours(2) AS two_hours
, to_seconds(90) AS ninety_seconds
, to_milliseconds(500) AS half_second
, to_years(0) + to_months(1) + to_days(15) + to_hours(0) + to_minutes(0) + to_seconds(0) AS one_month_fifteen_days
, o.order_date + to_days(30) AS due_date
, o.order_date + to_years(0) + to_months(1) + to_days(0) + to_hours(0) + to_minutes(0) + to_seconds(0) AS next_month
, o.order_date + INTERVAL '2 day 4 hour' AS later
, o.order_date + INTERVAL '1 year 2 month 15 day' AS far_future
, o.order_date + INTERVAL '1 hour 30 minute' AS meeting_end
FROM
 orders o
WHERE
  o.order_id <= 10250