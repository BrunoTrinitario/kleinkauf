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
    #Postgres y Redis entran como dependencias; no se compilan. Caddy (la entrada HTTPS) va con el backend.
    #El backup (servicio backup) todavia no se levanta: para activarlo, sumarlo aca (ver deploy.md, "Backups")
    UP="$BUILD caddy"
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

#Si solo cambio el Caddyfile, up no recrea el contenedor: se recarga la config
if [ "$PART" = backend ]; then
  compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
fi

#Limpieza: sin esto el disco crece varios GB por semana solo con deploys.
#- Las imagenes viejas quedan sin tag despues de cada build.
#- La cache de build (capas y la cache de gradle y npm) que no se uso en CACHE_KEEP: lo que se uso hace poco queda,
#  asi el proximo build sigue siendo rapido.
#- Contenedores parados y redes sin uso. Los volumenes (base, imagenes, certificados) no se tocan nunca.
CACHE_KEEP=${DOCKER_CACHE_KEEP:-72h}
echo "== limpieza"
docker image prune -f >/dev/null
docker builder prune -f --filter "until=$CACHE_KEEP" >/dev/null
docker container prune -f >/dev/null
docker network prune -f >/dev/null
docker system df

compose ps
