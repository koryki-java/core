# llmql is a simplified query language

## Main concepts

Llmql is an entity oriented query language with an antlr4 grammar. An entity represents some kind of categoried information 
stored in a relational database. Similar to tables entities have properties. 
Links connecting entities have just a name, defining the semantic meaning.
llmql will not care about join-columns or surrogate keys used in relational databases. 

Our very first sample in natural language:

    'Show me payables for customers in france.'

Translated to llmQL-form will look like:

    FIND customer 
    WHERE customer.country = "France" 
    RETURN invoice.total

We will explain how it works with northwind sample Database.

### Northwind database

The northwind database stores salaries for the northwind company.
Northwind sells products to other customer companies and buys products from supplier companies.
Northwind employees manage orders and ship products via shippers to customers.

#### Tables:

- **customers**  
  Contains information about the company's customers, with fields like company_name, contact_name
    - **company_name**  
      The cusomers company name
    - **contact_name**  
      Name of a contact person
    - **contact_title**  
      The title or role the contact person has in the customer company (e.g. Owner, Sales Representative, Marketing Manager).
    - **address**  
      The first address line of the customer.
    - **citiy**  
      The city of the customer address.
    - **region**  
      The region of the customer address.
    - **postal_code**  
      The postal code of the customer address.
    - **country**  
      The country of the customer address.
    - **phone**  
      The telephone number to call the contact person.

- **employees**  
  Stores employee information, such as their names and titles.
    - **last_name**  
      Last name of employee.
    - **first_name**  
      First name of employee.
    - **title**  
      The title or role the employee has in northwind, e.g. Sales Representive, Sales Manager, Inside Sales Coordinator.
    - **birth_date**  
      The employees date of birth.
    - **hire_date**  
      The date the employee was hired.
    - **address**  
      The address line of the employee.
    - **city**  
      The city of the employee address.
    - **region**  
      The region of the employee address.
    - **postal_code**  
      The postal code of the employee address.
    - **home_phone**  
      The telephone number of the employee.




- 2nd Item  
   Second item having index value 2, beside we gave it 1, which indicates that markdown parser does not break the list.
- **3rd Item:**  
   &nbsp;&nbsp;&nbsp;&nbsp;If you want to do something fancy with your list

Table: customers
Description: Contains information about the company's customers, with fields like company_name, contact_name

Table: customers
Description: Contains information about the company's customers, with fields like company_name, contact_name

