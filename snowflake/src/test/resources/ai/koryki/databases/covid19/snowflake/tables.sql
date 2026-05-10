-- Optional: Schema setzen
-- USE SCHEMA PUBLIC;

CREATE OR REPLACE TABLE categories (
    category_id NUMBER(5) NOT NULL,
    category_name VARCHAR(15) NOT NULL,
    description VARCHAR,
    picture BINARY,
    root_category_id NUMBER(5) NOT NULL,
    super_category_id NUMBER(5)
);

CREATE OR REPLACE TABLE customer_customer_demo (
    customer_id VARCHAR(5) NOT NULL,
    customer_type_id VARCHAR(5) NOT NULL
);

CREATE OR REPLACE TABLE customer_demographics (
    customer_type_id VARCHAR(5) NOT NULL,
    customer_desc VARCHAR
);

CREATE OR REPLACE TABLE customers (
    customer_id VARCHAR(5) NOT NULL,
    company_name VARCHAR(40) NOT NULL,
    contact_name VARCHAR(30),
    contact_title VARCHAR(30),
    address VARCHAR(60),
    city VARCHAR(15),
    region VARCHAR(15),
    postal_code VARCHAR(10),
    country VARCHAR(15),
    phone VARCHAR(24),
    mail VARCHAR(24)
);

CREATE OR REPLACE TABLE employees (
    employee_id NUMBER(5) NOT NULL,
    last_name VARCHAR(20) NOT NULL,
    first_name VARCHAR(10) NOT NULL,
    title VARCHAR(30),
    title_of_courtesy VARCHAR(25),
    birth_date DATE,
    hire_date DATE,
    address VARCHAR(60),
    city VARCHAR(15),
    region VARCHAR(15),
    postal_code VARCHAR(10),
    country VARCHAR(15),
    home_phone VARCHAR(24),
    extension VARCHAR(4),
    photo BINARY,
    notes VARCHAR,
    reports_to NUMBER(5),
    photo_path VARCHAR(255)
);

CREATE OR REPLACE TABLE employee_territories (
    employee_id NUMBER(5) NOT NULL,
    territory_id VARCHAR(20) NOT NULL
);

CREATE OR REPLACE TABLE order_details (
    order_id NUMBER(5) NOT NULL,
    product_id NUMBER(5) NOT NULL,
    unit_price FLOAT NOT NULL,
    quantity NUMBER(5) NOT NULL,
    discount FLOAT NOT NULL
);

CREATE OR REPLACE TABLE orders (
    order_id NUMBER(5) NOT NULL,
    customer_id VARCHAR(5),
    employee_id NUMBER(5),
    order_date DATE,
    required_date DATE,
    shipped_date DATE,
    ship_via NUMBER(5),
    freight DECIMAL(10,2),
    ship_name VARCHAR(40),
    ship_address VARCHAR(60),
    ship_city VARCHAR(15),
    ship_region VARCHAR(15),
    ship_postal_code VARCHAR(10),
    ship_country VARCHAR(15)
);

CREATE OR REPLACE TABLE products (
    product_id NUMBER(5) NOT NULL,
    product_name VARCHAR(40) NOT NULL,
    supplier_id NUMBER(5),
    category_id NUMBER(5),
    quantity_per_unit VARCHAR(20),
    unit_price FLOAT,
    units_in_stock NUMBER(5),
    units_on_order NUMBER(5),
    reorder_level NUMBER(5),
    discontinued BOOLEAN NOT NULL
);

CREATE OR REPLACE TABLE region (
    region_id NUMBER(5) NOT NULL,
    region_description VARCHAR(60) NOT NULL
);

CREATE OR REPLACE TABLE shippers (
    shipper_id NUMBER(5) NOT NULL,
    company_name VARCHAR(40) NOT NULL,
    phone VARCHAR(24)
);

CREATE OR REPLACE TABLE suppliers (
    supplier_id NUMBER(5) NOT NULL,
    company_name VARCHAR(40) NOT NULL,
    contact_name VARCHAR(30),
    contact_title VARCHAR(30),
    address VARCHAR(60),
    city VARCHAR(15),
    region VARCHAR(15),
    postal_code VARCHAR(10),
    country VARCHAR(15),
    phone VARCHAR(24),
    mail VARCHAR(24),
    homepage VARCHAR
);

CREATE OR REPLACE TABLE territories (
    territory_id VARCHAR(20) NOT NULL,
    territory_description VARCHAR(60) NOT NULL,
    region_id NUMBER(5) NOT NULL
);

CREATE OR REPLACE TABLE us_states (
    state_id NUMBER(5) NOT NULL,
    state_name VARCHAR(100),
    state_abbr VARCHAR(2),
    state_region VARCHAR(50)
);