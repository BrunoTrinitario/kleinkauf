#!/usr/bin/env bash
#Despliega una parte de KleinKauf en el server: backend o frontend.
#Lo corre GitHub Actions por SSH (.github/workflows/deploy.yml) despues de dejar el checkout en el commit pusheado.
#  bash backend/deploy/deploy.sh backend|frontend
#El .env es uno solo para los dos checkouts: ~/kleinkauf/.env (o KLEINKAUF_ENV). El compose tiene name: kleinkauf,
#asi que desde cualquiera de los dos checkouts se maneja el mismo proyecto, con los mismos volumenes.
set -euo pipefail

PART=${1:?uso: deploy.sh backend|frontend}
ENV_FILE=${KLEINKAUF_ENV:-$HOME/kleinkauf/.env}
[ -f "$ENV_FILE" ] || { echo "No existe $ENV_FILE (copiar backend/deploy/.env.example y completarlo)" >&2; exit 1; }

cd "$(dirname "$0")/../.."
compose() { docker compose --env-file "$ENV_FILE" -f backend/deploy/docker-compose.prod.yml "$@"; }

case "$PART" in
  backend)
    BUILD="eureka user-service product-service sales-service observability-service gateway-service"
    #Postgres y Redis entran como dependencias; no se compilan
    UP="$BUILD"
    UP_FLAGS=""
    ;;
  frontend)
    BUILD="frontend"
    UP="frontend"
    #El frontend no depende de nada del compose: no tocar el backend
    UP_FLAGS="--no-deps"
    ;;
  *)
    echo "Parte desconocida: $PART (backend o frontend)" >&2
    exit 1
    ;;
esac

echo "== Desplegando $PART ($(git rev-parse --short HEAD))"

#De a uno: con varios gradle en paralelo el VPS se queda sin memoria
for s in $BUILD; do
  echo "== build $s"
  compose build "$s"
done

#--wait espera a que los servicios queden healthy: si alguno no arranca, el deploy falla
echo "== up $UP"
compose up -d $UP_FLAGS --wait --wait-timeout 600 $UP

#Las imagenes viejas quedan sin tag despues de cada build
docker image prune -f >/dev/null

compose ps
