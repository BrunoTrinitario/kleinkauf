#!/usr/bin/env bash
#Backup diario del server: corre adentro del servicio backup de docker-compose.prod.yml (imagen de Postgres).
#Una vez por dia, a partir de BACKUP_HOUR (hora UTC), deja en /backups:
#  kleinkauf-<fecha>.sql.gz       pg_dumpall de todas las bases (users, products, sales, observability)
#  kleinkauf-<fecha>-uploads.tgz  imagenes de productos, comprobantes de pago y fotos de perfil (volumenes de products, sales y users)
#Borra los de mas de BACKUP_KEEP_DAYS dias. Si el contenedor se reinicia, no repite el backup del dia.
#Como restaurarlo: deploy.md, "Backups"
set -euo pipefail

BACKUP_HOUR=${BACKUP_HOUR:-6}
BACKUP_KEEP_DAYS=${BACKUP_KEEP_DAYS:-14}
DIR=/backups

backup() {
  local day=$1
  echo "$(date -u '+%F %T') backup $day"
  #Primero a un .tmp: si falla a mitad no queda un archivo cortado con el nombre final
  pg_dumpall --clean --if-exists | gzip > "$DIR/kleinkauf-$day.sql.gz.tmp"
  mv "$DIR/kleinkauf-$day.sql.gz.tmp" "$DIR/kleinkauf-$day.sql.gz"
  tar -czf "$DIR/kleinkauf-$day-uploads.tgz.tmp" -C /uploads .
  mv "$DIR/kleinkauf-$day-uploads.tgz.tmp" "$DIR/kleinkauf-$day-uploads.tgz"
  find "$DIR" -maxdepth 1 -name 'kleinkauf-*' -mtime "+$BACKUP_KEEP_DAYS" -delete
  echo "$(date -u '+%F %T') listo: $(du -sh "$DIR" | cut -f1) en total"
}

while true; do
  day=$(date -u +%F)
  hour=$((10#$(date -u +%H)))
  if [ "$hour" -ge "$BACKUP_HOUR" ] && [ ! -f "$DIR/kleinkauf-$day.sql.gz" ]; then
    rm -f "$DIR"/*.tmp
    backup "$day" || echo "$(date -u '+%F %T') ERROR: fallo el backup de $day, se reintenta en una hora" >&2
  fi
  sleep 3600
done
