package ai.koryki.snowflake.northwind;

import ai.koryki.databases.Script;
import ai.koryki.databases.northwind.duckdb.ExportJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class BuildNorthwind {

    private static final int BATCH_SIZE = 500;

    @FunctionalInterface
    private interface RowBinder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }

    public static void main(String[] args) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = BuildNorthwind.class.getResourceAsStream("/ai/koryki/databases/northwind/snowflake/data.json");
        JsonNode root = mapper.readTree(inputStream);

        try (Connection connection = NorthwindSnowflake.connection()) {
            connection.setAutoCommit(false);

            Script.executeScript(connection, "/ai/koryki/databases/northwind/snowflake/drop.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/snowflake/tables.sql");

            // check_temporal / check_type are dialect-specific and tiny — populated from SQL
            // scripts (single INSERTs), not the dialect-neutral JSON batch path.
            Script.executeScript(connection, "/ai/koryki/databases/northwind/snowflake/data_check_type.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/snowflake/data_check_temporal.sql");

            importTable(connection,
                    "INSERT INTO categories (category_id, category_name, description, root_category_id, super_category_id) VALUES (?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.CATEGORIES), new TypeReference<List<ExportJson.Category>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.categoryId());
                        ps.setString(2, r.categoryName());
                        setNullableString(ps, 3, r.description());
                        ps.setShort(4, r.rootCategoryId());
                        setNullableShort(ps, 5, r.superCategoryId());
                    });

            importTable(connection,
                    "INSERT INTO countries (country_name, iso_code, continent, latitude, longitude, geometry) VALUES (?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.COUNTRIES), new TypeReference<List<ExportJson.Country>>() {}),
                    (ps, r) -> {
                        ps.setString(1, r.countryName());
                        ps.setString(2, r.isoCode());
                        ps.setString(3, r.continent());
                        ps.setBigDecimal(4, r.latitude());
                        ps.setBigDecimal(5, r.longitude());
                        setNullableString(ps, 6, r.geometry());
                    });

            importTable(connection,
                    "INSERT INTO customer_customer_demo (customer_id, customer_type_id) VALUES (?,?)",
                    mapper.convertValue(root.get(ExportJson.CUSTOMER_CUSTOMER_DEMO), new TypeReference<List<ExportJson.CustomerCustomerDemo>>() {}),
                    (ps, r) -> {
                        ps.setString(1, r.customerId());
                        ps.setString(2, r.customerTypeId());
                    });

            importTable(connection,
                    "INSERT INTO customer_demographics (customer_type_id, customer_desc) VALUES (?,?)",
                    mapper.convertValue(root.get(ExportJson.CUSTOMER_DEMOGRAPHICS), new TypeReference<List<ExportJson.CustomerDemographics>>() {}),
                    (ps, r) -> {
                        ps.setString(1, r.customerTypeId());
                        setNullableString(ps, 2, r.customerDesc());
                    });

            importTable(connection,
                    "INSERT INTO customers (customer_id, company_name, contact_name, contact_title, address, city, region, postal_code, country, phone, mail) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.CUSTOMERS), new TypeReference<List<ExportJson.Customer>>() {}),
                    (ps, r) -> {
                        ps.setString(1, r.customerId());
                        ps.setString(2, r.companyName());
                        setNullableString(ps, 3, r.contactName());
                        setNullableString(ps, 4, r.contactTitle());
                        setNullableString(ps, 5, r.address());
                        setNullableString(ps, 6, r.city());
                        setNullableString(ps, 7, r.region());
                        setNullableString(ps, 8, r.postalCode());
                        setNullableString(ps, 9, r.country());
                        setNullableString(ps, 10, r.phone());
                        setNullableString(ps, 11, r.mail());
                    });

            importTable(connection,
                    "INSERT INTO employees (employee_id, last_name, first_name, title, title_of_courtesy, birth_date, hire_date, working_hour_from, working_hour_to, address, city, region, postal_code, country, home_phone, extension, notes, reports_to, photo_path) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.EMPLOYEES), new TypeReference<List<ExportJson.Employee>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.employeeId());
                        ps.setString(2, r.lastName());
                        ps.setString(3, r.firstName());
                        setNullableString(ps, 4, r.title());
                        setNullableString(ps, 5, r.titleOfCourtesy());
                        setNullableString(ps, 6, r.birthDate());
                        setNullableString(ps, 7, r.hireDate());
                        setNullableString(ps, 8, r.workingHourFrom());
                        setNullableString(ps, 9, r.workingHourTo());
                        setNullableString(ps, 10, r.address());
                        setNullableString(ps, 11, r.city());
                        setNullableString(ps, 12, r.region());
                        setNullableString(ps, 13, r.postalCode());
                        setNullableString(ps, 14, r.country());
                        setNullableString(ps, 15, r.homePhone());
                        setNullableString(ps, 16, r.extension());
                        setNullableString(ps, 17, r.notes());
                        setNullableShort(ps, 18, r.reportsTo());
                        setNullableString(ps, 19, r.photoPath());
                    });

            importTable(connection,
                    "INSERT INTO employee_territories (employee_id, territory_id) VALUES (?,?)",
                    mapper.convertValue(root.get(ExportJson.EMPLOYEE_TERRITORIES), new TypeReference<List<ExportJson.EmployeeTerritory>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.employeeId());
                        ps.setString(2, r.territoryId());
                    });

            importTable(connection,
                    "INSERT INTO order_details (order_id, product_id, unit_price, quantity, discount) VALUES (?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.ORDER_DETAILS), new TypeReference<List<ExportJson.OrderDetail>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.orderId());
                        ps.setShort(2, r.productId());
                        ps.setBigDecimal(3, r.unitPrice());
                        ps.setBigDecimal(4, r.quantity());
                        ps.setBigDecimal(5, r.discount());
                    });

            importTable(connection,
                    "INSERT INTO orders (order_id, customer_id, employee_id, order_date, required_date, shipped_date, delivered_date, ship_via, freight, ship_name, ship_address, ship_city, ship_region, ship_postal_code, ship_country) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.ORDERS), new TypeReference<List<ExportJson.Order>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.orderId());
                        setNullableString(ps, 2, r.customerId());
                        setNullableShort(ps, 3, r.employeeId());
                        setNullableString(ps, 4, r.orderDate());
                        setNullableString(ps, 5, r.requiredDate());
                        setNullableString(ps, 6, r.shippedDate());
                        setNullableString(ps, 7, r.deliveredDate());
                        setNullableShort(ps, 8, r.shipVia());
                        setNullableBigDecimal(ps, 9, r.freight());
                        setNullableString(ps, 10, r.shipName());
                        setNullableString(ps, 11, r.shipAddress());
                        setNullableString(ps, 12, r.shipCity());
                        setNullableString(ps, 13, r.shipRegion());
                        setNullableString(ps, 14, r.shipPostalCode());
                        setNullableString(ps, 15, r.shipCountry());
                    });

            importTable(connection,
                    "INSERT INTO products (product_id, product_name, supplier_id, category_id, quantity_per_unit, unit_price, units_in_stock, units_on_order, reorder_level, discontinued) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.PRODUCTS), new TypeReference<List<ExportJson.Product>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.productId());
                        ps.setString(2, r.productName());
                        setNullableShort(ps, 3, r.supplierId());
                        setNullableShort(ps, 4, r.categoryId());
                        setNullableString(ps, 5, r.quantityPerUnit());
                        setNullableBigDecimal(ps, 6, r.unitPrice());
                        setNullableShort(ps, 7, r.unitsInStock());
                        setNullableShort(ps, 8, r.unitsOnOrder());
                        setNullableShort(ps, 9, r.reorderLevel());
                        ps.setInt(10, r.discontinued());
                    });

            importTable(connection,
                    "INSERT INTO region (region_id, region_description) VALUES (?,?)",
                    mapper.convertValue(root.get(ExportJson.REGIONS), new TypeReference<List<ExportJson.Region>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.regionId());
                        ps.setString(2, r.regionDescription());
                    });

            importTable(connection,
                    "INSERT INTO shippers (shipper_id, company_name, phone) VALUES (?,?,?)",
                    mapper.convertValue(root.get(ExportJson.SHIPPERS), new TypeReference<List<ExportJson.Shipper>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.shipperId());
                        ps.setString(2, r.companyName());
                        setNullableString(ps, 3, r.phone());
                    });

            importTable(connection,
                    "INSERT INTO suppliers (supplier_id, company_name, contact_name, contact_title, address, city, region, postal_code, country, phone, mail, homepage) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.SUPPLIERS), new TypeReference<List<ExportJson.Supplier>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.supplierId());
                        ps.setString(2, r.companyName());
                        setNullableString(ps, 3, r.contactName());
                        setNullableString(ps, 4, r.contactTitle());
                        setNullableString(ps, 5, r.address());
                        setNullableString(ps, 6, r.city());
                        setNullableString(ps, 7, r.region());
                        setNullableString(ps, 8, r.postalCode());
                        setNullableString(ps, 9, r.country());
                        setNullableString(ps, 10, r.phone());
                        setNullableString(ps, 11, r.mail());
                        setNullableString(ps, 12, r.homepage());
                    });

            importTable(connection,
                    "INSERT INTO territories (territory_id, territory_description, region_id) VALUES (?,?,?)",
                    mapper.convertValue(root.get(ExportJson.TERRITORIES), new TypeReference<List<ExportJson.Territory>>() {}),
                    (ps, r) -> {
                        ps.setString(1, r.territoryId());
                        ps.setString(2, r.territoryDescription());
                        ps.setShort(3, r.regionId());
                    });

            importTable(connection,
                    "INSERT INTO us_states (state_id, state_name, state_abbr, state_region) VALUES (?,?,?,?)",
                    mapper.convertValue(root.get(ExportJson.US_STATES), new TypeReference<List<ExportJson.UsState>>() {}),
                    (ps, r) -> {
                        ps.setShort(1, r.stateId());
                        setNullableString(ps, 2, r.stateName());
                        setNullableString(ps, 3, r.stateAbbr());
                        setNullableString(ps, 4, r.stateRegion());
                    });


            connection.commit();
        }
    }

    private static <T> void importTable(Connection conn, String sql, List<T> rows, RowBinder<T> binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (T row : rows) {
                binder.bind(ps, row);
                ps.addBatch();
                if (++count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.println("  imported " + rows.size() + " rows via: " + sql.substring(0, sql.indexOf('(')));
    }

    private static void setNullableShort(PreparedStatement ps, int idx, Short value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.SMALLINT);
        else ps.setShort(idx, value);
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, value);
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int idx, BigDecimal value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.DECIMAL);
        else ps.setBigDecimal(idx, value);
    }

}
