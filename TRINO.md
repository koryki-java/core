# Trino und MariaDB

## podman installieren

    brew install podman

## podman konfigurieren

    podman machine stop
    podman machine set --memory 8192 --cpus 4
    podman machine start


## Create a network

    podman network create trino-net

## mariaDB


    podman run -d --name mariadb --network trino-net \
    -e MARIADB_ROOT_PASSWORD=secret \
    -e MARIADB_DATABASE=demo \
    -e MARIADB_USER=trino \
    -e MARIADB_PASSWORD=trino \
    -p 3306:3306 \
    docker.io/library/mariadb:11.8

## (Optional) seed a little data so there's something to query:

    podman exec -i mariadb mariadb -uroot -psecret demo <<'SQL'
    CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50));
    INSERT INTO customers VALUES (1,'Ada'), (2,'Linus');
    SQL

## Create the Trino catalog file

Trino reads catalogs from /etc/trino/catalog/. Put one on disk (under your home so the Podman machine can mount it):

    mkdir -p ~/trino/catalog
    cat > ~/trino/catalog/mariadb.properties <<'EOF'
    connector.name=mariadb
    connection-url=jdbc:mariadb://mariadb:3306
    connection-user=root
    connection-password=secret
    EOF

The host mariadb resolves because both containers are on trino-net.
## Start Trino

    podman run -d --name trino --network trino-net \
    -p 8080:8080 \
    -v ~/trino/catalog/mariadb.properties:/etc/trino/catalog/mariadb.properties:ro \
    docker.io/trinodb/trino:latest

Give it ~15–30 s to boot, then watch the log until you see SERVER STARTED:

podman logs -f trino

5. Query it

Use the built-in CLI inside the container:

podman exec -it trino trino

SHOW CATALOGS;                  -- should list "mariadb"
SHOW SCHEMAS FROM mariadb;
SELECT * FROM mariadb.demo.customers;

The web UI is at http://localhost:8080 (any username, no password).

## Postgresql

    podman run -d --name postgres --network trino-net \
    -e POSTGRES_DB=demo \
    -e POSTGRES_USER=trino \
    -e POSTGRES_PASSWORD=trino \
    -p 5432:5432 \
    docker.io/library/postgres:17

## mssql

    podman run -d --name mssql --network trino-net \
    --platform linux/amd64 \
    -e ACCEPT_EULA=Y \
    -e MSSQL_SA_PASSWORD='Strong_passw0rd!' \
    -e MSSQL_PID=Developer \
    -e MSSQL_MEMORY_LIMIT_MB=2048 \
    -p 1433:1433 \
    mcr.microsoft.com/mssql/server:2022-latest

## sqledge

    podman run -d \
    --name sqledge \
    -e ACCEPT_EULA=1 \
    -e MSSQL_SA_PASSWORD='Strong_passw0rd!' \
    -p 1433:1433 \
    mcr.microsoft.com/azure-sql-edge:1.0.7


podman run --rm -it --platform linux/amd64 \
mcr.microsoft.com/mssql-tools \
/opt/mssql-tools/bin/sqlcmd -S host.containers.internal,1433 \
-U sa -P 'Strong_passw0rd!' \
-Q "CREATE DATABASE demo COLLATE Latin1_General_100_BIN2_UTF8;"


## Next

ClickHouse, Spark SQL, BigQuery, DB2

## ORACLE 
    
    podman run -d \
    --name oracle \
    -p 1521:1521 \
    -e ORACLE_PASSWORD=oracle \
    docker.io/gvenzl/oracle-free:latest

podman exec -it oracle sqlplus system/oracle@FREEPDB1


CREATE USER demo IDENTIFIED BY demo;

GRANT CONNECT, RESOURCE TO demo;

GRANT CREATE TABLE TO demo;

ALTER USER demo QUOTA 100M ON users;