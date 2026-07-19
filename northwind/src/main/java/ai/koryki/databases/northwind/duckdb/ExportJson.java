package ai.koryki.databases.northwind.duckdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExportJson {

    public static final String CATEGORIES = "categories";
    public static final String COUNTRIES = "countries";
    public static final String CUSTOMER_CUSTOMER_DEMO = "customer_customer_demo";
    public static final String CUSTOMER_DEMOGRAPHICS = "customer_demographics";
    public static final String CUSTOMERS = "customers";
    public static final String EMPLOYEES = "employees";
    public static final String EMPLOYEE_TERRITORIES = "employee_territories";
    public static final String ORDER_DETAILS = "orderDetails";
    public static final String ORDERS = "orders";
    public static final String PRODUCTS = "products";
    public static final String REGIONS = "regions";
    public static final String SHIPPERS = "shippers";
    public static final String SUPPLIERS = "suppliers";
    public static final String TERRITORIES = "territories";
    public static final String US_STATES = "usStates";
    public static final String CHECK_TYPE    = "checkType";
    public static final String CHECK_TEMPORAL = "checkTemporal";

    public record Category(
            short categoryId,
            String categoryName,
            String description,
            short rootCategoryId,
            Short superCategoryId
    ) {}

    public record Country(
            String countryName,
            String isoCode,
            String continent,
            BigDecimal latitude,
            BigDecimal longitude,
            String geometry
    ) {}

    public record CustomerCustomerDemo(
            String customerId,
            String customerTypeId
    ) {}

    public record CustomerDemographics(
            String customerTypeId,
            String customerDesc
    ) {}

    public record Customer(
            String customerId,
            String companyName,
            String contactName,
            String contactTitle,
            String address,
            String city,
            String region,
            String postalCode,
            String country,
            String phone,
            String mail
    ) {}

    public record Employee(
            short employeeId,
            String lastName,
            String firstName,
            String title,
            String titleOfCourtesy,
            String birthDate,
            String hireDate,
            String workingHourFrom,
            String workingHourTo,
            String address,
            String city,
            String region,
            String postalCode,
            String country,
            String homePhone,
            String extension,
            String notes,
            Short reportsTo,
            String photoPath
    ) {}

    public record EmployeeTerritory(
            short employeeId,
            String territoryId
    ) {}

    public record OrderDetail(
            short orderId,
            short productId,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal discount
    ) {}

    public record Order(
            short orderId,
            String customerId,
            Short employeeId,
            String orderDate,
            String requiredDate,
            String shippedDate,
            String deliveredDate,
            Short shipVia,
            BigDecimal freight,
            String shipName,
            String shipAddress,
            String shipCity,
            String shipRegion,
            String shipPostalCode,
            String shipCountry
    ) {}

    public record Product(
            short productId,
            String productName,
            Short supplierId,
            Short categoryId,
            String quantityPerUnit,
            BigDecimal unitPrice,
            Short unitsInStock,
            Short unitsOnOrder,
            Short reorderLevel,
            int discontinued
    ) {}

    public record Region(
            short regionId,
            String regionDescription
    ) {}

    public record Shipper(
            short shipperId,
            String companyName,
            String phone
    ) {}

    public record Supplier(
            short supplierId,
            String companyName,
            String contactName,
            String contactTitle,
            String address,
            String city,
            String region,
            String postalCode,
            String country,
            String phone,
            String mail,
            String homepage
    ) {}

    public record Territory(
            String territoryId,
            String territoryDescription,
            short regionId
    ) {}

    public record UsState(
            short stateId,
            String stateName,
            String stateAbbr,
            String stateRegion
    ) {}

    public record CheckType(
            short nr,
            Byte typeTinyint,
            Short typeSmallint,
            Integer typeInteger,
            Long typeBigint,
            BigDecimal typeHugeint,
            Short typeUtinyint,
            Integer typeUsmallint,
            Long typeUinteger,
            BigDecimal typeUbigint,
            BigDecimal typeDecimal,
            BigDecimal typeNumeric,
            Float typeReal,
            Float typeFloat,
            Double typeDouble,
            Boolean typeBoolean,
            String typeChar,
            String typeVarchar,
            String typeText,
            String typeString,
            String typeBlob,
            String typeBit,
            String typeDate,
            String typeTime,
            String typeTimestamp,
            String typeTimestampS,
            String typeTimestampMs,
            String typeTimestampNs,
            String typeTimestamptz,
            String typeInterval,
            String typeUuid,
            String typeJson
    ) {}

    public record CheckTemporal(
            short nr,
            String timeTime,
            Integer timeSecFromMidnight
    ) {}

    public static void main(String[] args) throws Exception {
        String output = args.length > 0 ? args[0] : "build/northwind.json";

        try (Connection conn = NorthwindDuckdb.fromResource(NorthwindDuckdb.DUCKDB)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(CATEGORIES, readCategories(conn));
            data.put(COUNTRIES, readCountries(conn));
            data.put(CUSTOMER_CUSTOMER_DEMO, readCustomerCustomerDemo(conn));
            data.put(CUSTOMER_DEMOGRAPHICS, readCustomerDemographics(conn));
            data.put(CUSTOMERS, readCustomers(conn));
            data.put(EMPLOYEES, readEmployees(conn));
            data.put(EMPLOYEE_TERRITORIES, readEmployeeTerritories(conn));
            data.put(ORDER_DETAILS, readOrderDetails(conn));
            data.put(ORDERS, readOrders(conn));
            data.put(PRODUCTS, readProducts(conn));
            data.put(REGIONS, readRegions(conn));
            data.put(SHIPPERS, readShippers(conn));
            data.put(SUPPLIERS, readSuppliers(conn));
            data.put(TERRITORIES, readTerritories(conn));
            data.put(US_STATES, readUsStates(conn));
            data.put(CHECK_TYPE, readCheckType(conn));
            data.put(CHECK_TEMPORAL, readCheckTemporal(conn));

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(output), data);

            System.out.println("Exported to " + output);
        }
    }

    private static List<Category> readCategories(Connection conn) throws Exception {
        List<Category> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT category_id, category_name, description, root_category_id, super_category_id FROM categories")) {
            while (rs.next()) {
                short superCatRaw = rs.getShort("super_category_id");
                Short superCategoryId = rs.wasNull() ? null : superCatRaw;
                list.add(new Category(
                        rs.getShort("category_id"),
                        rs.getString("category_name"),
                        rs.getString("description"),
                        rs.getShort("root_category_id"),
                        superCategoryId
                ));
            }
        }
        return list;
    }

    private static List<Country> readCountries(Connection conn) throws Exception {
        List<Country> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT country_name, iso_code, continent, latitude, longitude, geometry FROM countries")) {
            while (rs.next()) {
                list.add(new Country(
                        rs.getString("country_name"),
                        rs.getString("iso_code"),
                        rs.getString("continent"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getString("geometry")
                ));
            }
        }
        return list;
    }

    private static List<CustomerCustomerDemo> readCustomerCustomerDemo(Connection conn) throws Exception {
        List<CustomerCustomerDemo> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT customer_id, customer_type_id FROM customer_customer_demo")) {
            while (rs.next()) {
                list.add(new CustomerCustomerDemo(rs.getString("customer_id"), rs.getString("customer_type_id")));
            }
        }
        return list;
    }

    private static List<CustomerDemographics> readCustomerDemographics(Connection conn) throws Exception {
        List<CustomerDemographics> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT customer_type_id, customer_desc FROM customer_demographics")) {
            while (rs.next()) {
                list.add(new CustomerDemographics(rs.getString("customer_type_id"), rs.getString("customer_desc")));
            }
        }
        return list;
    }

    private static List<Customer> readCustomers(Connection conn) throws Exception {
        List<Customer> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT customer_id, company_name, contact_name, contact_title, address, city, region, postal_code, country, phone, mail FROM customers")) {
            while (rs.next()) {
                list.add(new Customer(
                        rs.getString("customer_id"),
                        rs.getString("company_name"),
                        rs.getString("contact_name"),
                        rs.getString("contact_title"),
                        rs.getString("address"),
                        rs.getString("city"),
                        rs.getString("region"),
                        rs.getString("postal_code"),
                        rs.getString("country"),
                        rs.getString("phone"),
                        rs.getString("mail")
                ));
            }
        }
        return list;
    }

    private static List<Employee> readEmployees(Connection conn) throws Exception {
        List<Employee> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT employee_id, last_name, first_name, title, title_of_courtesy, birth_date, hire_date, " +
                     "working_hour_from, working_hour_to, address, city, region, postal_code, country, home_phone, extension, notes, reports_to, photo_path FROM employees")) {
            while (rs.next()) {
                short reportsToRaw = rs.getShort("reports_to");
                Short reportsTo = rs.wasNull() ? null : reportsToRaw;
                list.add(new Employee(
                        rs.getShort("employee_id"),
                        rs.getString("last_name"),
                        rs.getString("first_name"),
                        rs.getString("title"),
                        rs.getString("title_of_courtesy"),
                        rs.getString("birth_date"),
                        rs.getString("hire_date"),
                        rs.getString("working_hour_from"),
                        rs.getString("working_hour_to"),
                        rs.getString("address"),
                        rs.getString("city"),
                        rs.getString("region"),
                        rs.getString("postal_code"),
                        rs.getString("country"),
                        rs.getString("home_phone"),
                        rs.getString("extension"),
                        rs.getString("notes"),
                        reportsTo,
                        rs.getString("photo_path")
                ));
            }
        }
        return list;
    }

    private static List<EmployeeTerritory> readEmployeeTerritories(Connection conn) throws Exception {
        List<EmployeeTerritory> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT employee_id, territory_id FROM employee_territories")) {
            while (rs.next()) {
                list.add(new EmployeeTerritory(rs.getShort("employee_id"), rs.getString("territory_id")));
            }
        }
        return list;
    }

    private static List<OrderDetail> readOrderDetails(Connection conn) throws Exception {
        List<OrderDetail> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT order_id, product_id, unit_price, quantity, discount FROM order_details")) {
            while (rs.next()) {
                list.add(new OrderDetail(
                        rs.getShort("order_id"),
                        rs.getShort("product_id"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("discount")
                ));
            }
        }
        return list;
    }

    private static List<Order> readOrders(Connection conn) throws Exception {
        List<Order> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT order_id, customer_id, employee_id, order_date, required_date, shipped_date, delivered_date, " +
                     "ship_via, freight, ship_name, ship_address, ship_city, ship_region, ship_postal_code, ship_country FROM orders")) {
            while (rs.next()) {
                short employeeIdRaw = rs.getShort("employee_id");
                Short employeeId = rs.wasNull() ? null : employeeIdRaw;
                short shipViaRaw = rs.getShort("ship_via");
                Short shipVia = rs.wasNull() ? null : shipViaRaw;
                list.add(new Order(
                        rs.getShort("order_id"),
                        rs.getString("customer_id"),
                        employeeId,
                        rs.getString("order_date"),
                        rs.getString("required_date"),
                        rs.getString("shipped_date"),
                        rs.getString("delivered_date"),
                        shipVia,
                        rs.getBigDecimal("freight"),
                        rs.getString("ship_name"),
                        rs.getString("ship_address"),
                        rs.getString("ship_city"),
                        rs.getString("ship_region"),
                        rs.getString("ship_postal_code"),
                        rs.getString("ship_country")
                ));
            }
        }
        return list;
    }

    private static List<Product> readProducts(Connection conn) throws Exception {
        List<Product> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT product_id, product_name, supplier_id, category_id, quantity_per_unit, " +
                     "unit_price, units_in_stock, units_on_order, reorder_level, discontinued FROM products")) {
            while (rs.next()) {
                short supplierIdRaw = rs.getShort("supplier_id");
                Short supplierId = rs.wasNull() ? null : supplierIdRaw;
                short categoryIdRaw = rs.getShort("category_id");
                Short categoryId = rs.wasNull() ? null : categoryIdRaw;
                short unitsInStockRaw = rs.getShort("units_in_stock");
                Short unitsInStock = rs.wasNull() ? null : unitsInStockRaw;
                short unitsOnOrderRaw = rs.getShort("units_on_order");
                Short unitsOnOrder = rs.wasNull() ? null : unitsOnOrderRaw;
                short reorderLevelRaw = rs.getShort("reorder_level");
                Short reorderLevel = rs.wasNull() ? null : reorderLevelRaw;
                list.add(new Product(
                        rs.getShort("product_id"),
                        rs.getString("product_name"),
                        supplierId,
                        categoryId,
                        rs.getString("quantity_per_unit"),
                        rs.getBigDecimal("unit_price"),
                        unitsInStock,
                        unitsOnOrder,
                        reorderLevel,
                        rs.getInt("discontinued")
                ));
            }
        }
        return list;
    }

    private static List<Region> readRegions(Connection conn) throws Exception {
        List<Region> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT region_id, region_description FROM region")) {
            while (rs.next()) {
                list.add(new Region(rs.getShort("region_id"), rs.getString("region_description")));
            }
        }
        return list;
    }

    private static List<Shipper> readShippers(Connection conn) throws Exception {
        List<Shipper> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT shipper_id, company_name, phone FROM shippers")) {
            while (rs.next()) {
                list.add(new Shipper(rs.getShort("shipper_id"), rs.getString("company_name"), rs.getString("phone")));
            }
        }
        return list;
    }

    private static List<Supplier> readSuppliers(Connection conn) throws Exception {
        List<Supplier> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT supplier_id, company_name, contact_name, contact_title, address, city, region, " +
                     "postal_code, country, phone, mail, homepage FROM suppliers")) {
            while (rs.next()) {
                list.add(new Supplier(
                        rs.getShort("supplier_id"),
                        rs.getString("company_name"),
                        rs.getString("contact_name"),
                        rs.getString("contact_title"),
                        rs.getString("address"),
                        rs.getString("city"),
                        rs.getString("region"),
                        rs.getString("postal_code"),
                        rs.getString("country"),
                        rs.getString("phone"),
                        rs.getString("mail"),
                        rs.getString("homepage")
                ));
            }
        }
        return list;
    }

    private static List<Territory> readTerritories(Connection conn) throws Exception {
        List<Territory> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT territory_id, territory_description, region_id FROM territories")) {
            while (rs.next()) {
                list.add(new Territory(rs.getString("territory_id"), rs.getString("territory_description"), rs.getShort("region_id")));
            }
        }
        return list;
    }

    private static List<UsState> readUsStates(Connection conn) throws Exception {
        List<UsState> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT state_id, state_name, state_abbr, state_region FROM us_states")) {
            while (rs.next()) {
                list.add(new UsState(
                        rs.getShort("state_id"),
                        rs.getString("state_name"),
                        rs.getString("state_abbr"),
                        rs.getString("state_region")
                ));
            }
        }
        return list;
    }

    private static String blobToHex(Blob blob) throws SQLException {
        if (blob == null) return null;
        byte[] bytes = blob.getBytes(1, (int) blob.length());
        return HexFormat.of().formatHex(bytes);
    }

    private static List<CheckType> readCheckType(Connection conn) throws Exception {
        List<CheckType> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT nr, type_tinyint, type_smallint, type_integer, type_bigint, type_hugeint, " +
                     "type_utinyint, type_usmallint, type_uinteger, type_ubigint, " +
                     "type_decimal, type_numeric, type_real, type_float, type_double, type_boolean, " +
                     "type_char, type_varchar, type_text, type_string, type_blob, type_bit, " +
                     "type_date, type_time, type_timestamp, type_timestamp_s, type_timestamp_ms, " +
                     "type_timestamp_ns, type_timestamptz, type_interval, type_uuid, type_json FROM check_type")) {
            while (rs.next()) {
                byte tinyintRaw = rs.getByte("type_tinyint");
                Byte typeTinyint = rs.wasNull() ? null : tinyintRaw;
                short smallintRaw = rs.getShort("type_smallint");
                Short typeSmallint = rs.wasNull() ? null : smallintRaw;
                int integerRaw = rs.getInt("type_integer");
                Integer typeInteger = rs.wasNull() ? null : integerRaw;
                long bigintRaw = rs.getLong("type_bigint");
                Long typeBigint = rs.wasNull() ? null : bigintRaw;
                short utinyintRaw = rs.getShort("type_utinyint");
                Short typeUtinyint = rs.wasNull() ? null : utinyintRaw;
                int usmallintRaw = rs.getInt("type_usmallint");
                Integer typeUsmallint = rs.wasNull() ? null : usmallintRaw;
                long uintegerRaw = rs.getLong("type_uinteger");
                Long typeUinteger = rs.wasNull() ? null : uintegerRaw;
                float realRaw = rs.getFloat("type_real");
                Float typeReal = rs.wasNull() ? null : realRaw;
                float floatRaw = rs.getFloat("type_float");
                Float typeFloat = rs.wasNull() ? null : floatRaw;
                double doubleRaw = rs.getDouble("type_double");
                Double typeDouble = rs.wasNull() ? null : doubleRaw;
                boolean booleanRaw = rs.getBoolean("type_boolean");
                Boolean typeBoolean = rs.wasNull() ? null : booleanRaw;
                list.add(new CheckType(
                        rs.getShort("nr"),
                        typeTinyint,
                        typeSmallint,
                        typeInteger,
                        typeBigint,
                        rs.getBigDecimal("type_hugeint"),
                        typeUtinyint,
                        typeUsmallint,
                        typeUinteger,
                        rs.getBigDecimal("type_ubigint"),
                        rs.getBigDecimal("type_decimal"),
                        rs.getBigDecimal("type_numeric"),
                        typeReal,
                        typeFloat,
                        typeDouble,
                        typeBoolean,
                        rs.getString("type_char"),
                        rs.getString("type_varchar"),
                        rs.getString("type_text"),
                        rs.getString("type_string"),
                        blobToHex(rs.getBlob("type_blob")),
                        rs.getString("type_bit"),
                        rs.getString("type_date"),
                        rs.getString("type_time"),
                        rs.getString("type_timestamp"),
                        rs.getString("type_timestamp_s"),
                        rs.getString("type_timestamp_ms"),
                        rs.getString("type_timestamp_ns"),
                        rs.getString("type_timestamptz"),
                        rs.getString("type_interval"),
                        rs.getString("type_uuid"),
                        rs.getString("type_json")
                ));
            }
        }
        return list;
    }

    private static List<CheckTemporal> readCheckTemporal(Connection conn) throws Exception {
        List<CheckTemporal> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT nr, time_time, time_sec_from_midnight FROM check_temporal")) {
            while (rs.next()) {
                int secRaw = rs.getInt("time_sec_from_midnight");
                Integer timeSecFromMidnight = rs.wasNull() ? null : secRaw;
                list.add(new CheckTemporal(
                        rs.getShort("nr"),
                        rs.getString("time_time"),
                        timeSecFromMidnight
                ));
            }
        }
        return list;
    }
}
