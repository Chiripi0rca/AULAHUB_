#!/usr/bin/env bash
# ============================================================
#  Backup diario de la base de datos de AulaHub (version DOCKER).
#  Hace mysqldump DENTRO del contenedor 'db' y guarda un .sql.gz
#  comprimido en el host. Lee las credenciales del mismo .env que
#  usa docker-compose. PREPARADO pero NO activo: se programa con
#  cron (ver DOCKER.md, seccion Backups).
#  Pensado para un host Linux. En Windows se usa el Programador de
#  tareas en vez de cron (mismo script via WSL/Git-Bash).
# ============================================================
set -euo pipefail

# Carpeta donde estan docker-compose.yml + .env (ajusta si la mueves).
PROJECT_DIR="/opt/aulahub"
BACKUP_DIR="/var/backups/aulahub"
RETENTION_DAYS=14   # cuantos dias de respaldos conservar

cd "$PROJECT_DIR"

# ---- Credenciales y nombre de la base desde el .env ----
# No se hace 'source': el archivo tiene URLs con '&' que bash interpretaria.
ENV_FILE="$PROJECT_DIR/.env"
if [[ ! -r "$ENV_FILE" ]]; then
  echo "ERROR: no se puede leer $ENV_FILE" >&2
  exit 1
fi
leer_var() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- || true; }
DB_NAME="$(leer_var DB_NAME)"
DB_USER="$(leer_var DB_USER)"
DB_PASSWORD="$(leer_var DB_PASSWORD)"

if [[ -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "ERROR: faltan DB_NAME, DB_USER o DB_PASSWORD en $ENV_FILE" >&2
  exit 1
fi

# ---- Backup comprimido con fecha ----
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"   # los respaldos contienen TODA la base; solo root debe leerlos
FECHA=$(date +%F_%H%M)
ARCHIVO="$BACKUP_DIR/aulahub_${FECHA}.sql.gz"

# -T: sin pseudo-TTY (necesario al redirigir la salida).
# El password viaja como MYSQL_PWD dentro del contenedor (no en 'ps').
docker compose exec -T -e MYSQL_PWD="$DB_PASSWORD" db \
  mysqldump --single-transaction --routines --triggers -u "$DB_USER" "$DB_NAME" \
  | gzip > "$ARCHIVO"

# ---- Retencion: borra respaldos mas viejos que RETENTION_DAYS ----
find "$BACKUP_DIR" -name "aulahub_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "Backup OK: $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"
