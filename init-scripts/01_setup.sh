#!/bin/bash

echo "--- Debug: Checking Environment ---"
echo "Username: $ORACLE_USERNAME"
echo "--- Debug: Checking Files ---"
ls -l /opt/oracle/template/01_setup.sql.tmpl || echo "Template NOT FOUND"

# 使用 sed 代替 envsubst (因為 sed 是 Linux 標配)
# 注意：這裡直接寫死容器內部的絕對路徑
sed "s/\${ORACLE_USERNAME}/$ORACLE_USERNAME/g; s/\${ORACLE_PASSWORD}/$ORACLE_PASSWORD/g" \
    /opt/oracle/template/01_setup.sql.tmpl > /tmp/setup_final.sql

echo "--- Debug: Checking Generated SQL ---"
cat /tmp/setup_final.sql

# 執行 SQL
sqlplus -S / as sysdba @/tmp/setup_final.sql

echo "--- Custom Setup Finished ---"