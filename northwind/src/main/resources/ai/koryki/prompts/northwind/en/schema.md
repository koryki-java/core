# northwind
## Tables

The northwind database stores salaries for the northwind company.
Northwind sells products to other customer companies and buys products from supplier companies.
Northwind employees manage orders and ship products via shippers to customers.

## categories
Organizes products into different categories.
- category_name: Name of a category

- description: Description of a category

- picture: A picture to visualize the category

## customers
Contains information about the company's customers, with fields like company_name, contact_name.
- company_name: The cusomers company name

- contact_name: Name of a contact person

- contact_title: The title or role the contact person has in the customer company (e.g. Owner, Sales Representative, Marketing Manager).

- address: The first address line of the customer.

- city: The city of the customer address.

- region: The region of the customer address.

- postal_code: The postal code of the customer address.

- country: The country of the customer address.

- phone: The telephone number to call the contact person.

- fax: The fax number to call the contact person.

## customer_customer_demo
Stores the relationships between customer and costomer demographics.
- customer_id: A foreign key identifying the customer.

- customer_type_id: A foreign key identifying the customer demographic.

## customer_demographics
A customer democraphics describes customer segments, e.g. Retail, Wholesale, Corporate, etc.
- customer_desc: A description to the customer demographic.

## employees
Stores employee information, such as their names and titles.
- last_name: Last name of employee.

- first_name: First name of employee.

- title: The title or role the employee has in the company

- title_of_courtesy: The employees courtesy

- birth_date: The employees date of birth.

- hire_date: The date the employee was hired.

- address: The address line of the employee.

- city: The city of the employee address

- region: The region of the employee address.

- postal_code: The postal code of the employee address.

- country: The country of the employee address

- home_phone: The telephone number of the employee.

- extension:

- photo: The portrait photo of the employee

- notes: Notes to the employee

- photo_path: Path or URL to the photo

## employee_territories
Stores the relationships between employees and territories.
- employee_id: A foreign key identifying the employee.

- territory_id: A foreign key identifying the territory.

## orders
Holds records of customer orders, including OrderID, OrderDate, and CustomerID.
- order_date:
- required_date: The date the customer requires the order

- shipped_date: The date the order is shipped.

- ship_via: A foreign key identifying the shipper.

- freight: The shipping freight.

- ship_name: The name of the company to ship the order to.

- ship_address: The address of the company the ship goes to.

- ship_city: The city of the company the ship goes to.

- ship_region: The region of the company the ship goes to.

- ship_postal_code: ; The postal code of the company the ship goes to.
- ship_country: ; The country of the company the ship goes to.
## order_details
Links orders to products, detailing which products are included in each order.
- unit_price: The unit price valid to the order.

- quantity: The quantity of the order.

- discount: The discount to the order.

## products
Stores details about products, including name, supplier, category, and unit price.
- product_name: The name of the product.

- quantity_per_unit: The quantity per unit the product is sold.

- unit_price: The unit price the product is sold before discount.

- units_in_stock: The units in stock.

- units_on_order: The units on order.

- reorder_level: Minimum units on stock before ordering new products.

- discontinued: Signs whether the product is discontinued

## region
The region products are sold to.
- region_description: A description to the region.

## shippers
Stores information to shippers.
- company_name: The name of the shipping company.

- phone: The telephone number.

## suppliers
Stores information about suppliers delivering products.
- company_name: The name of the suppliers company.

- contact_name: The name of the person to get in contact to the supplier.

- contact_title: The title or role of the person to get in contact to the supplier.

- address: Address of the contact person.

- city: City of the contact person.

- region: Region of the supplier.

- postal_code: Postal code of the supplier.

- country: Country for the supplier.

- phone: Telephone number of the contact person.

- fax: Fax number of the company.

- homepage: Homepage of the supplier company.

## territories
Stores Territories.
- territory_description: Description of the territory.

## Relations / Links
## same_product

both tables refer to the same product data record

### Relations / Links:
- order_details - products
- products - products


## same_customer_demographic

both tables refer to the same customer-demographic data record

### Relations / Links:
- customer_customer_demo - customer_demographics
- customer_demographics - customer_demographics


## same_shipper

both tables refer to the same shipper data record

### Relations / Links:
- orders - shippers
- shippers - shippers


## root_category_of

The category is the topmost root category of the second category
The link is directed, order of categories matter. The second category is the root category.

### Relations / Links:
- categories - categories


## belongs_to_root

The category is a direct or indirect subcategory for the second category (root category)
The link is directed, order of categories matter. The second category is the root category.

### Relations / Links:
- categories - categories


## same_supplier

both tables refer to the same supplier data record

### Relations / Links:
- products - suppliers
- suppliers - suppliers


## same_employee

both tables refer to the same employee data record

### Relations / Links:
- employee_territories - employees
- orders - employees
- employees - employees


## same_territory

both tables refer to the same territory data record

### Relations / Links:
- employee_territories - territories
- territories - territories


## same_customer_customer_demo

both tables refer to the same customer-demographic data record

### Relations / Links:
- customer_customer_demo - customer_customer_demo


## same_us_state

both tables refer to the same state data record

### Relations / Links:


## same_region

both tables refer to the same region data record

### Relations / Links:
- territories - region
- region - region


## same_order_detail

both tables refer to the same order-detail data record

### Relations / Links:
- order_details - order_details


## reports_to

the first employee reports to the second employee.
The link is directed, order of employees matter. The first employee is team member, the second employee is the supervisor.

### Relations / Links:
- employees - employees


## supervisor_of

the first employee is supervisor of the second employee.
The link is directed, order of employees matter. The first employee is team leader, the second employee is team member.

### Relations / Links:
- employees - employees


## parent_of

this first category is a supercategory of the second.
The link is directed, order of categories matter. Root categories have no parent categories

### Relations / Links:
- categories - categories


## child_of

this first category is a subcategory of the second.
The link is directed, order of categories matter. Root categories have not parent categories

### Relations / Links:
- categories - categories


## same_employee_territory

both tables refer to the same territory data record

### Relations / Links:
- employee_territories - employee_territories


## same_category

both tables refer to the same category data record

### Relations / Links:
- products - categories
- categories - categories


## same_order

both tables refer to the same order data record

### Relations / Links:
- order_details - orders
- orders - orders


## same_customer

both tables refer to the same customer data record

### Relations / Links:
- customer_customer_demo - customers
- orders - customers
- customers - customers


