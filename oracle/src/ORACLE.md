
# Cast Functions

Oracle does not support all standard SQL types, so several KQL cast functions render differently than on other dialects.

| KQL Function | Standard SQL | Oracle rendering | Reason |
|---|---|---|---|
| `to_boolean(value)` | `CAST(value AS BOOLEAN)` | `value = 1` | Oracle SQL has no native BOOLEAN type. |
| `to_text(value)` | `CAST(value AS TEXT)` | `CAST(value AS VARCHAR(4000))` | Oracle SQL has no `TEXT` type; `VARCHAR2` is the closest equivalent. |
| `to_double(value)` | `CAST(value AS DOUBLE)` | `CAST(value AS BINARY_DOUBLE)` | Oracle uses `BINARY_DOUBLE` for IEEE 754 double-precision. |
| `to_varchar(value, n)` | `CAST(value AS VARCHAR(n))` | `CAST(value AS VARCHAR2(n))` | Oracle uses `VARCHAR2` for variable-length strings. |

For locale-specific or custom date/time formats use the KQL `parse_date`, `parse_time`, and `parse_timestamp` functions, which accept Oracle format strings directly:

```
parse_date(o.date_text, 'DD/MM/YYYY')          → TO_DATE(o.date_text, 'DD/MM/YYYY')
parse_timestamp(o.ts_text, 'DD/MM/YYYY HH24:MI:SS') → TO_TIMESTAMP(o.ts_text, 'DD/MM/YYYY HH24:MI:SS')
parse_time(o.time_text, 'HH24:MI:SS')          → TO_DATE(o.time_text, 'HH24:MI:SS')
```

Note: Oracle has no native TIME type. `parse_time` renders as `TO_DATE`, which returns an Oracle DATE carrying the time component.

# TIME_ENCODINGS

| ENCODING                   | Sample                  | Description |
|----------------------------|-------------------------|-------------|
| SECONDS_FROM_MIDNIGHT      | 30615                   |             |
| MILLISECONDS_FROM_MIDNIGHT | 30615000                |             |
| HHMMSS_INTEGER             | 83015                   |             |
| TEXT_HH24_MI_SS            | 08:30:15                |             |
| FIXED_DATE                 | 1970-01-01 08:30:15     |             |
| DATETIME_TIME_PART         | 2026-05-19 08:30:15     |             |
| TIMESTAMP_TIME_PART        | 2026-05-19 08:30:15.123 |             |
| INTERVAL_TIME              | 0 08:30:15              |             |

# DURATION_ENCODINGS

| ENCODING               | Sample     | Description |
|------------------------|------------|-------------|
| SECONDS                | 5400       |             |
| MINUTES                | 90         |             |
| MILLISECONDS           | 5400000    |             |
| TEXT_HH24_MI_SS        | 01:30:00   |             |
| INTERVAL_DAY_TO_SECOND | 0 01:30:00 |             |
