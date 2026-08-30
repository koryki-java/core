--
-- This project includes a modified version of the Microsoft Northwind sample database.
-- Modifications by Johannes Zemlin, 2025.
--
-- Original database (c) Microsoft Corporation, available at:
-- https://github.com/microsoft/sql-server-samples
--
-- Primary keys first, then foreign keys. The countries primary key is declared
-- inline in the self-contained countries.sql.
--


--
-- Primary keys
--

ALTER TABLE categories
    ADD CONSTRAINT pk_categories PRIMARY KEY (category_id);

ALTER TABLE customer_customer_demo
    ADD CONSTRAINT pk_customer_customer_demo PRIMARY KEY (customer_id, customer_type_id);

ALTER TABLE customer_demographics
    ADD CONSTRAINT pk_customer_demographics PRIMARY KEY (customer_type_id);

ALTER TABLE customers
    ADD CONSTRAINT pk_customers PRIMARY KEY (customer_id);

ALTER TABLE employees
    ADD CONSTRAINT pk_employees PRIMARY KEY (employee_id);

ALTER TABLE employee_territories
    ADD CONSTRAINT pk_employee_territories PRIMARY KEY (employee_id, territory_id);

ALTER TABLE order_details
    ADD CONSTRAINT pk_order_details PRIMARY KEY (order_id, product_id);

ALTER TABLE orders
    ADD CONSTRAINT pk_orders PRIMARY KEY (order_id);

ALTER TABLE products
    ADD CONSTRAINT pk_products PRIMARY KEY (product_id);

ALTER TABLE region
    ADD CONSTRAINT pk_region PRIMARY KEY (region_id);

ALTER TABLE shippers
    ADD CONSTRAINT pk_shippers PRIMARY KEY (shipper_id);

ALTER TABLE suppliers
    ADD CONSTRAINT pk_suppliers PRIMARY KEY (supplier_id);

ALTER TABLE territories
    ADD CONSTRAINT pk_territories PRIMARY KEY (territory_id);

ALTER TABLE us_states
    ADD CONSTRAINT pk_usstates PRIMARY KEY (state_id);


--
-- Foreign keys
--

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_categories_root FOREIGN KEY (root_category_id) REFERENCES categories (category_id);

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_categories_super FOREIGN KEY (super_category_id) REFERENCES categories (category_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_employees FOREIGN KEY (employee_id) REFERENCES employees (employee_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_shippers FOREIGN KEY (ship_via) REFERENCES shippers (shipper_id);

ALTER TABLE order_details
    ADD CONSTRAINT fk_order_details_products FOREIGN KEY (product_id) REFERENCES products (product_id);

ALTER TABLE order_details
    ADD CONSTRAINT fk_order_details_orders FOREIGN KEY (order_id) REFERENCES orders (order_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_categories FOREIGN KEY (category_id) REFERENCES categories (category_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_suppliers FOREIGN KEY (supplier_id) REFERENCES suppliers (supplier_id);

ALTER TABLE territories
    ADD CONSTRAINT fk_territories_region FOREIGN KEY (region_id) REFERENCES region (region_id);

ALTER TABLE employee_territories
    ADD CONSTRAINT fk_employee_territories_territories FOREIGN KEY (territory_id) REFERENCES territories (territory_id);

ALTER TABLE employee_territories
    ADD CONSTRAINT fk_employee_territories_employees FOREIGN KEY (employee_id) REFERENCES employees (employee_id);

ALTER TABLE customer_customer_demo
    ADD CONSTRAINT fk_customer_customer_demo_customer_demographics FOREIGN KEY (customer_type_id) REFERENCES customer_demographics (customer_type_id);

ALTER TABLE customer_customer_demo
    ADD CONSTRAINT fk_customer_customer_demo_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id);

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_employees FOREIGN KEY (reports_to) REFERENCES employees (employee_id);

ALTER TABLE countries
    ADD CONSTRAINT pk_countries PRIMARY KEY (country_name);


--
-- constraints complete
--
