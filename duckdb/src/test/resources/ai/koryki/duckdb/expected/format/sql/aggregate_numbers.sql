-- count -> BIGINT, sum(freight) -> DECIMAL: both type-driven, no forced ".0" on the count
SELECT
  count(o.order_id)
, sum(o.freight)
FROM
 orders o