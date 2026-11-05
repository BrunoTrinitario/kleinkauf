#!/usr/bin/env bash
# Creates a platform superadmin directly in Postgres (users database, public schema), the same way users does:
# bcrypt password (cost 10, like BCryptPasswordEncoder) and the SUPERADMIN role. Asks for the email, name and password.
# Run it on the server, next to the running containers:
#   bash ~/kleinkauf/backend/backend/deploy/create-superadmin.sh
# Optional variables: PG_CONTAINER (default: the running container with a postgres image), PG_USER (postgres),
# USERS_DB (users).
# It does not go through the app, so the creation does not show up in the audit log.
set -euo pipefail

PG_USER="${PG_USER:-postgres}"
USERS_DB="${USERS_DB:-users}"
PG_CONTAINER="${PG_CONTAINER:-$(docker ps --format '{{.Names}} {{.Image}}' | awk '$2 ~ /^postgres(:|$)/ {print $1; exit}')}"

fail() { echo "Error: $*" >&2; exit 1; }

[ -n "$PG_CONTAINER" ] || fail "no encontré el contenedor de Postgres. Indicalo con PG_CONTAINER=<nombre>"

psql_users() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$USERS_DB" -v ON_ERROR_STOP=1 -qAt; }
sql_quote() { printf "'%s'" "${1//\'/\'\'}"; }

echo "Crear superadmin (Postgres: $PG_CONTAINER, base $USERS_DB)"

read -r -p "Email: " EMAIL
EMAIL="$(printf '%s' "$EMAIL" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
[ ${#EMAIL} -le 254 ] && [[ "$EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || fail "email inválido"

read -r -p "Nombre [Superadmin]: " NAME
NAME="$(printf '%s' "${NAME:-Superadmin}" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
[ -n "$NAME" ] && [ ${#NAME} -le 100 ] || fail "el nombre tiene que tener entre 1 y 100 caracteres"

read -r -s -p "Contraseña: " PASSWORD; echo
read -r -s -p "Repetí la contraseña: " PASSWORD2; echo
[ "$PASSWORD" = "$PASSWORD2" ] || fail "las contraseñas no coinciden"
[ ${#PASSWORD} -ge 8 ] || fail "la contraseña tiene que tener al menos 8 caracteres"
[ "$(printf '%s' "$PASSWORD" | wc -c)" -le 72 ] || fail "la contraseña no puede pasar de 72 bytes (límite de bcrypt)"

# The email is unique in public, also against deactivated users
EXISTING="$(printf 'SELECT count(*) FROM public.usuarios WHERE lower(email) = %s;\n' "$(sql_quote "$EMAIL")" | psql_users)"
[ "$EXISTING" = "0" ] || fail "ya hay un usuario de la plataforma con ese email (si es un superadmin desactivado, reactivalo desde el panel)"

# The hash is computed in the "postgres" maintenance database so pgcrypto is not added to users. The password goes
# through stdin, so it does not show up in the process list
HASH="$(printf 'CREATE EXTENSION IF NOT EXISTS pgcrypto;\nSELECT crypt(%s, gen_salt(%s, 10));\n' \
    "$(sql_quote "$PASSWORD")" "'bf'" \
    | docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 -qAt | tail -n 1)"
unset PASSWORD PASSWORD2
[[ "$HASH" == \$2a\$10\$* ]] || fail "no se pudo calcular el hash de la contraseña"

# Dates in UTC, like the services (server time in Docker)
ID="$(psql_users <<SQL
SET TIME ZONE 'UTC';
BEGIN;
WITH u AS (
  INSERT INTO public.usuarios (name, email, password, created_at)
  VALUES ($(sql_quote "$NAME"), $(sql_quote "$EMAIL"), $(sql_quote "$HASH"), LOCALTIMESTAMP)
  RETURNING id
)
INSERT INTO public.roles (type, userid) SELECT 'SUPERADMIN', id FROM u RETURNING userid;
COMMIT;
SQL
)"
ID="$(printf '%s\n' "$ID" | grep -E '^[0-9]+$' | tail -n 1)"
[ -n "$ID" ] || fail "no se pudo crear el superadmin"

echo "Listo: superadmin $EMAIL (id $ID). Ya puede entrar al panel de la plataforma."
