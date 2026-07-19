--
-- This project includes a modified version of the Microsoft Northwind sample database.
-- Modifications by Johannes Zemlin, 2025.
--
-- Original database (c) Microsoft Corporation, available at:
-- https://github.com/microsoft/sql-server-samples
--

--
-- drop tables. FK checks are disabled so drop order is irrelevant and a
-- partially-built schema from a previous run is always removed cleanly.
--

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS customer_customer_demo;
DROP TABLE IF EXISTS employee_territories;
DROP TABLE IF EXISTS order_details;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS customer_demographics;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS shippers;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS territories;
DROP TABLE IF EXISTS us_states;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS region;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS countries;
DROP TABLE IF EXISTS check_type;
DROP TABLE IF EXISTS check_temporal;

SET FOREIGN_KEY_CHECKS = 1;
