# A Guide to the Koryki Query Language



Marketing managers are creative and imaginative; they often have questions  
IT isn't prepared for, same applies to research, investigative work, and many other areas.

Today, if a marketing manager wanted to know something special, they had three choices:

- Wait for IT: Submit a ticket and wait days for a report.
- Learn SQL: Spend months learning technical syntax like INNER JOIN, GROUP BY, and HAVING.
- Ask an LLM: Let AI translate natural language to SQL, without further verification the result is 
known to be uncertain.


## The cross selling opportunity - "Gourmet Gap"

**The Goal**: Identify customers who are obsessed with Seafood but haven't yet discovered our Condiments.
Marketing Strategy: Send these customers a "Perfect Pairing" email featuring sauces and spices specifically for fish.

**Business Question**: Find customers who have ordered more than 5 Seafood items but have 0 orders for Condiments.

## The Anatomy of a Query

Let's look at the example of finding customers:

**FIND**: Tell the system which "entities" you are interested in - we are interested in customers, orders and product categories.

**FILTER**: Set your rules - our customers ordered Seafood more than 5 times, but havent ordered Condiments.

**FETCH**: Decide what information you want to see in your final report - the company_name and mail are fine.





    FIND customers c, orders o, order_details od, products p, categories cat
    FILTER cat.category_name = 'Seafood' 
      AND count(od.product_id) > 3
      AND NOT c.customer_id IN (
          FIND customers c2, orders o2, order_details od2, products p2, categories cat2
          FILTER cat2.category_name = 'Condiments'
          FETCH c2.customer_id
      )
    FETCH c.company_name, c.contact_name, c.phone


    FIND customers c, orders o, order_details od, products p, categories cat
    FILTER cat.category_name = 'Seafood' 
      AND count(od) > 3
      AND NOT EXISTS (c orders o2, order_details od2, products p2, categories cat2
          FILTER cat2.category_name = 'Condiments'
      )
    FETCH c.company_name, c.contact_name, c.phone

Neither is efficient. Business moves too fast for IT tickets, and SQL is filled with technical "noise" that has nothing to do with business goals.

The Solution: A Human-Centric Bridge

Koryki.ai introduces a "Business Model" layer. This layer hides the messy technical details (like how tables are connected or how the computer optimizes its search) and presents the database to you using the terms you use every day, like Customers, Orders, and Product Names.

Behind this simple navigation syntax is a formal grammar.