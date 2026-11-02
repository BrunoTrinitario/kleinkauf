#!/bin/sh
#Seed de desarrollo: da de alta la tienda demo por la API (mismo flujo que el autoservicio)
#Se puede correr muchas veces: si la tienda ya existe la API responde 409 y se toma como ok
#Reintenta porque el gateway tarda unos segundos en ver a users en eureka

body=$(cat <<EOF
{"identifier":"$DEMO_IDENTIFIER","ownerName":"$DEMO_OWNER_NAME","ownerEmail":"$DEMO_OWNER_EMAIL","ownerPassword":"$DEMO_OWNER_PASSWORD"}
EOF
)

for i in $(seq 1 40); do
  status=$(curl -s -o /tmp/response -w '%{http_code}' \
    -X POST "$GATEWAY_URL/api/v1/platform/tenants" \
    -H "Origin: $PLATFORM_URL" \
    -H 'Content-Type: application/json' \
    -d "$body")

  case "$status" in
    201) echo "Tienda $DEMO_IDENTIFIER creada: $(cat /tmp/response)"; exit 0 ;;
    409) echo "La tienda $DEMO_IDENTIFIER ya existe"; exit 0 ;;
    400) echo "Datos invalidos: $(cat /tmp/response)"; exit 1 ;;
  esac

  echo "Intento $i: el gateway respondio $status, reintento en 5s"
  sleep 5
done

echo "No se pudo crear la tienda $DEMO_IDENTIFIER"
exit 1
