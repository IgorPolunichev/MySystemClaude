#!/bin/bash
# Создаёт отдельную БД и пользователя для каждого сервиса (database-per-service) и для Keycloak.
# Выполняется один раз при первом старте пустого тома PostgreSQL.
set -euo pipefail

for db in ${SERVICE_DATABASES}; do
  echo "Создаю БД и пользователя: ${db}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE USER ${db} WITH PASSWORD '${db}';
    CREATE DATABASE ${db} OWNER ${db} ENCODING 'UTF8' TEMPLATE template0;
SQL
done
