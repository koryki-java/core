--
-- This project includes a modified version of the Microsoft Northwind sample database.
-- Modifications by Johannes Zemlin, 2025.
--
-- Original database © Microsoft Corporation, available at:
-- https://github.com/microsoft/sql-server-samples
--
-- MariaDB dialect. Translated from the DuckDB blueprint
-- (northwind/.../duckdb/northwind/db/tables.sql). MariaDB has no native INTERVAL
-- or TIMESTAMPTZ type: INTERVAL columns are stored as ISO-8601 duration text
-- (character varying), and zone-aware instants use TIMESTAMP (read back under
-- SET time_zone = '+00:00', see docs/TEMPORAL.md). Large VARCHAR(32672) columns
-- become TEXT. The countries table is created by the self-contained countries.sql.
--


--
-- Name: categories; Type: TABLE
--

CREATE TABLE categories (
    category_id smallint NOT NULL,
    category_name character varying(15) NOT NULL,
    description TEXT,
    picture BLOB,
    root_category_id smallint NOT NULL,
    super_category_id smallint
) COLLATE=utf8mb4_bin;


--
-- Name: customer_customer_demo; Type: TABLE
--

CREATE TABLE customer_customer_demo (
    customer_id character varying(5) NOT NULL,
    customer_type_id character varying(5) NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: customer_demographics; Type: TABLE
--

CREATE TABLE customer_demographics (
    customer_type_id character varying(5) NOT NULL,
    customer_desc TEXT
) COLLATE=utf8mb4_bin;


--
-- Name: customers; Type: TABLE
--

CREATE TABLE customers (
    customer_id character varying(5) NOT NULL,
    company_name character varying(40) NOT NULL,
    contact_name character varying(30),
    contact_title character varying(30),
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    phone character varying(24),
    mail character varying(24)
) COLLATE=utf8mb4_bin;


--
-- Name: employees; Type: TABLE
--

CREATE TABLE employees (
    employee_id smallint NOT NULL,
    last_name character varying(20) NOT NULL,
    first_name character varying(10) NOT NULL,
    title character varying(30),
    title_of_courtesy character varying(25),
    birth_date date,
    hire_date date,
    working_hour_from time,
    working_hour_to time,
    break_minutes character varying(30),
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    home_phone character varying(24),
    extension character varying(4),
    photo BLOB,
    notes TEXT,
    reports_to smallint,
    photo_path character varying(255)
) COLLATE=utf8mb4_bin;


--
-- Name: employee_territories; Type: TABLE
--

CREATE TABLE employee_territories (
    employee_id smallint NOT NULL,
    territory_id character varying(20) NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: order_details; Type: TABLE
--

CREATE TABLE order_details (
    order_id smallint NOT NULL,
    product_id smallint NOT NULL,
    unit_price DOUBLE NOT NULL,
    quantity SMALLINT NOT NULL,
    discount DOUBLE NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: orders; Type: TABLE
--

CREATE TABLE orders (
    order_id smallint NOT NULL,
    customer_id character varying(5),
    employee_id smallint,
    order_date date,
    required_date date,
    shipped_date date,
    delivered_date timestamp NULL,
    ship_via smallint,
    freight NUMERIC(10,3),
    ship_name character varying(40),
    ship_address character varying(60),
    ship_city character varying(15),
    ship_region character varying(15),
    ship_postal_code character varying(10),
    ship_country character varying(15)
) COLLATE=utf8mb4_bin;


--
-- Name: products; Type: TABLE
--

CREATE TABLE products (
    product_id smallint NOT NULL,
    product_name character varying(40) NOT NULL,
    supplier_id smallint,
    category_id smallint,
    quantity_per_unit character varying(20),
    unit_price NUMERIC(18,2),
    units_in_stock smallint,
    units_on_order smallint,
    reorder_level smallint,
    discontinued integer NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: region; Type: TABLE
--

CREATE TABLE region (
    region_id smallint NOT NULL,
    region_description character varying(60) NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: shippers; Type: TABLE
--

CREATE TABLE shippers (
    shipper_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    phone character varying(24)
) COLLATE=utf8mb4_bin;


--
-- Name: suppliers; Type: TABLE
--

CREATE TABLE suppliers (
    supplier_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    contact_name character varying(30),
    contact_title character varying(30),
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    phone character varying(24),
    mail character varying(24),
    homepage TEXT
) COLLATE=utf8mb4_bin;


--
-- Name: territories; Type: TABLE
--

CREATE TABLE territories (
    territory_id character varying(20) NOT NULL,
    territory_description character varying(60) NOT NULL,
    region_id smallint NOT NULL
) COLLATE=utf8mb4_bin;


--
-- Name: us_states; Type: TABLE
--

CREATE TABLE us_states (
    state_id smallint NOT NULL,
    state_name character varying(100),
    state_abbr character varying(2),
    state_region character varying(50)
) COLLATE=utf8mb4_bin;

--
-- Name: countries; Type: TABLE; Schema: public; Owner: -; Tablespace:
--

CREATE TABLE countries (
                           country_name character varying(15) NOT NULL,
                           iso_code character varying(2) NOT NULL,
                           continent character varying(15) NOT NULL,
                           latitude numeric(8,4) NOT NULL,
                           longitude numeric(9,4) NOT NULL,
                           geometry VARCHAR(4000)
) COLLATE=utf8mb4_bin;


--
-- Name: check_type; Type: TABLE
--

CREATE TABLE check_type (
    nr              smallint NOT NULL,
    type_smallint   SMALLINT,
    type_integer    INTEGER,
    type_bigint     BIGINT,
    type_decimal    DECIMAL(18,4),
    type_double     DOUBLE,
    type_boolean    BOOLEAN,
    type_date       DATE,
    type_time       TIME,
    type_timestamp  TIMESTAMP NULL
) COLLATE=utf8mb4_bin;


--
-- Name: check_temporal; Type: TABLE
--

CREATE TABLE check_temporal
(
    nr           SMALLINT NOT NULL,
    date_date    DATE,                     -- wall-clock UTC
    timestamp_timestamp TIMESTAMP NULL,    -- wall-clock UTC

    time_time TIME,
    time_sec_from_midnight INTEGER,

    ts_new_york TIMESTAMP NULL,            -- wall-clock America/New_York
    date_new_york DATE,                    -- wall-clock America/New_York
    time_from_integer INTEGER,             -- HHMMSS, e.g. 123050
    time_from_string character varying(8), -- 'HH:MM:SS'
    time_from_timestamp TIMESTAMP NULL,    -- time of day in the time part

    time_from_date DATE,                   -- TIME_FROM_DATE (time part of a DATE)
    date_epoch_day INTEGER,                -- DATE_FROM_EPOCH_DAY (days since 1970-01-01)
    bool_from_int INTEGER,                 -- BOOLEAN_FROM_INTEGER (0/1)
    bool_native BOOLEAN,                   -- native BOOLEAN
    uuid_binary BLOB,                      -- UUID_FROM_BINARY (16-byte)
    uuid_string character varying(36),     -- UUID_FROM_STRING (CHAR(36))
    timestamp_zoned TIMESTAMP NULL,        -- INSTANT (zone-aware; stored UTC)
    timestamp_unix_epoche BIGINT,          -- EPOCH:SECONDS (Unix epoch, seconds)
    timestamp_java_epoche BIGINT,          -- EPOCH:MILLIS (Java epoch, milliseconds)
    ts_diff_base TIMESTAMP NULL,           -- elapsed-span fixture anchor (2024-06-01 09:00:00)
    ts_diff_intraday TIMESTAMP NULL,       --   anchor + 03:20:30 (< 1 day)
    ts_diff_multiday TIMESTAMP NULL,       --   anchor + 50:00:00 (> 1 day; shown as clock hours, not days)
    money_scaled BIGINT,                   -- SCALED:2 (minor units, 1299 -> 12.99)

    interval_seconds        INT,           -- interval in seconds
    interval_millis         BIGINT,        -- interval in milliseconds
    interval_char           character varying(30),  -- interval as text (ISO 8601)
    interval_interval       character varying(30),  -- calendar amount; ISO-8601 text (no native INTERVAL)
    interval_year_month     character varying(30),  -- calendar amount (Period); ISO-8601 text
    interval_day_second     character varying(30)   -- exact-time amount (Duration); ISO-8601 text
) COLLATE=utf8mb4_bin;
