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

#De a uno: con varios gradle en paralelo el VPS se queda sin memoria.
#--no-cache: cada deploy compila desde cero y no reusa capas de otro deploy (al final se borra toda la cache).
#Dentro del mismo deploy los servicios del backend comparten la cache de gradle (mount de Dockerfile.service), asi
#que las dependencias se bajan una sola vez por deploy
for s in $BUILD; do
  echo "== build $s"
  compose build --no-cache "$s"
done

#--wait espera a que los servicios queden healthy: si alguno no arranca, el deploy falla
echo "== up $UP"
compose up -d $UP_FLAGS --wait --wait-timeout 600 $UP

#Si solo cambio el Caddyfile, up no recrea el contenedor: se recarga la config
if [ "$PART" = backend ]; then
  compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
fi

#Limpieza: no se guarda nada de un deploy para el siguiente (con cache quedaban mas de 13 GB en dos dias).
#- Las imagenes viejas quedan sin tag despues de cada build.
#- Toda la cache de build: capas y las caches de gradle y npm.
#- Contenedores parados y redes sin uso. Los volumenes (base, imagenes, certificados) no se tocan nunca.
echo "== limpieza"
docker image prune -f >/dev/null
docker builder prune -af >/dev/null
docker container prune -f >/dev/null
docker network prune -f >/dev/null
docker system df

compose ps
