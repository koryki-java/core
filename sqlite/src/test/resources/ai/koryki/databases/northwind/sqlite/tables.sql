--
-- Reverse-engineered from the committed northwind.sqlite (which realises the DuckDB blueprint).
-- CREATE statements from sqlite_master (every table except countries, which countries.sql creates).
-- Rebuild the binary with the :sqlite:SqliteNorthwindScript task.
--

CREATE TABLE categories (
    category_id smallint NOT NULL,
    category_name character varying(15) NOT NULL,
    description VARCHAR(32672),
    picture BLOB,
    root_category_id smallint NOT NULL,
    super_category_id smallint
);

CREATE TABLE customer_customer_demo (
    customer_id character varying(5) NOT NULL,
    customer_type_id character varying(5) NOT NULL
);

CREATE TABLE customer_demographics (
    customer_type_id character varying(5) NOT NULL,
    customer_desc VARCHAR(32672)
);

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
);

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
    break_minutes INTERVAL,
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    home_phone character varying(24),
    extension character varying(4),
    photo BLOB,
    notes VARCHAR(32672),
    reports_to smallint,
    photo_path character varying(255)
);

CREATE TABLE employee_territories (
    employee_id smallint NOT NULL,
    territory_id character varying(20) NOT NULL
);

CREATE TABLE order_details (
    order_id smallint NOT NULL,
    product_id smallint NOT NULL,
    unit_price NUMERIC(18,6) NOT NULL,
    quantity NUMERIC(18,6) NOT NULL,
    discount DECIMAL(5,4)  NOT NULL
);

CREATE TABLE orders (
    order_id smallint NOT NULL,
    customer_id character varying(5),
    employee_id smallint,
    order_date date,
    required_date date,
    shipped_date date,
    delivered_date timestamp,
    ship_via smallint,
    freight NUMERIC(10,3),
    ship_name character varying(40),
    ship_address character varying(60),
    ship_city character varying(15),
    ship_region character varying(15),
    ship_postal_code character varying(10),
    ship_country character varying(15)
);

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
);

CREATE TABLE region (
    region_id smallint NOT NULL,
    region_description character varying(60) NOT NULL
);

CREATE TABLE shippers (
    shipper_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    phone character varying(24)
);

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
    homepage VARCHAR(32672)
);

CREATE TABLE territories (
    territory_id character varying(20) NOT NULL,
    territory_description character varying(60) NOT NULL,
    region_id smallint NOT NULL
);

CREATE TABLE us_states (
    state_id smallint NOT NULL,
    state_name character varying(100),
    state_abbr character varying(2),
    state_region character varying(50)
);

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
    type_timestamp  TIMESTAMP
);

CREATE TABLE check_temporal
(
    nr           SMALLINT NOT NULL,

    time_time TIME,
    time_sec_from_midnight INTEGER,

    ts_new_york TEXT,               
    date_new_york TEXT,             
    date_date TEXT,                 
    time_from_integer INTEGER,      
    time_from_string TEXT,          
    time_from_timestamp TEXT,       

    date_epoch_day INTEGER,         
    bool_from_int INTEGER,          
    uuid_binary BLOB,               
    uuid_string TEXT,               
    timestamp_zoned TEXT,                
    timestamp_unix_epoche INTEGER,  
    timestamp_java_epoche INTEGER,  
    timestamp_timestamp TIMESTAMP,  
    ts_diff_base TIMESTAMP,         
    ts_diff_intraday TIMESTAMP,     
    ts_diff_multiday TIMESTAMP,     
    interval_seconds INTEGER,   
    interval_millis INTEGER,    
    money_scaled INTEGER            
);

