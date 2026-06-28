podman run -d --name mssql \
-e "ACCEPT_EULA=Y" \
-e "MSSQL_SA_PASSWORD=Strong_passw0rd!" \
-e "MSSQL_PID=Developer" \
-e "MSSQL_COLLATION=SQL_Latin1_General_CP1_CS_AS" \
-e "TZ=UTC" \
-p 1433:1433 \
mcr.microsoft.com/mssql/server:2022-latest
