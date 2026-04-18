# Sample Query


User enters:

    Find customers who have placed 
    more than 10 orders in January 2023,
    return companyname and count, sort by count.

With the help of published resources about koryki.ai, **KQL**-grammar, and the entity-relationship model
AI may create a query in **KQL**-form:

    FIND customers c, orders o
    FILTER count(o) > 10 AND 
        o.order_date BETWEEN 
          DATE '2023-01-01' AND DATE '2023-01-31'
    FETCH c.company_name, count(o) DESC

The same query in **IQL**:


    SELECT
     customers c
      OUT c.company_name 1
      GROUP c.company_name
      JOIN same_customer orders o
      OUT count(o.order_id) 2
        FILTER
         o.order_date BETWEEN 
           DATE '2023-01-01' AND DATE '2023-12-31'
        HAVING
         count(o.order_id) > 10
        ORDER count(o.order_id) DESC
      OWNER


Applied rules:
1. resolve FILTER-Clause into **IQL**-FILTER or **IQL**-HAVING, in dependence on aggregation
2. add GROUP-BY-Clause where required
3. resolve primary-key columns for identity in the count function

Join Clauses are still not resolved to Join Columns.

The same query in **SQL**, resolved Join Columns:

    SELECT
        c.company_name
    ,   count(o.order_id)
    FROM
        customers c
        INNER JOIN orders o ON
            c.customer_id = o.customer_id
    WHERE
        o.order_date BETWEEN 
             DATE '2023-01-01' AND DATE '2023-01-31'
    GROUP BY
        c.company_name
    HAVING
        count(o.order_id) > 10
    ORDER BY
        count(o.order_id) DESC


