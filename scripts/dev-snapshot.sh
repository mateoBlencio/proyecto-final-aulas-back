#!/usr/bin/env bash
# Uso: scripts/dev-snapshot.sh {refresh|run}.
# refresh necesita DEV_SNAPSHOT_ADMIN_USER en el .env de la raiz del repo.

set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] && { set -a; source .env; set +a; }

ADMIN_HOST="100.90.179.1"
ADMIN_PORT="5432"
ADMIN_DB="${DEV_SNAPSHOT_ADMIN_DB:-database_dev}"
CONTAINER=dev-snapshot-db-container

find_pg16_bin() {
    if command -v pg_dump >/dev/null 2>&1 && pg_dump --version | grep -q ' 16\.'; then
        dirname "$(command -v pg_dump)"
        return
    fi
    for prefix in /opt/homebrew/opt/postgresql@16 /usr/local/opt/postgresql@16; do
        if [ -x "$prefix/bin/pg_dump" ]; then
            echo "$prefix/bin"
            return
        fi
    done
    echo "No encontre pg_dump/pg_restore v16 (el server de dev corre 16.x). Instalar con: brew install postgresql@16" >&2
    exit 1
}

cmd_refresh() {
    if [ -z "${DEV_SNAPSHOT_ADMIN_USER:-}" ]; then
        echo "Falta DEV_SNAPSHOT_ADMIN_USER (tu usuario admin de dev) en el .env de la raíz del repo." >&2
        exit 1
    fi
    local admin_user="$DEV_SNAPSHOT_ADMIN_USER"

    local pg16_bin
    pg16_bin="$(find_pg16_bin)"
    dump_file="$(mktemp -t dev-snapshot-dump)"
    trap 'rm -f "${dump_file:-}"' EXIT

    echo "Dump de dev ($admin_user@$ADMIN_HOST:$ADMIN_PORT/$ADMIN_DB) -> temp"
    "$pg16_bin/pg_dump" -h "$ADMIN_HOST" -p "$ADMIN_PORT" -U "$admin_user" -d "$ADMIN_DB" -Fc -f "$dump_file"

    echo "Levantando postgres local (si no estaba arriba)"
    docker compose -f compose-snapshot.yaml up -d

    for _ in $(seq 1 30); do
        docker exec "$CONTAINER" pg_isready -U myuser >/dev/null 2>&1 && break
        sleep 1
    done

    echo "Limpiando schema del postgres local"
    docker exec -e PGPASSWORD=secret "$CONTAINER" psql -U myuser -d mydatabase \
        -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

    echo "Restaurando dump"
    PGPASSWORD=secret "$pg16_bin/pg_restore" -h localhost -p 5432 -U myuser -d mydatabase \
        --no-owner --no-privileges "$dump_file"

    echo "Limpiando target/ (evita migraciones viejas compiladas si hubo un rename)"
    ./mvnw clean -q

    echo "Listo (dump temporal borrado). Correr la app con: scripts/dev-snapshot.sh run"
}

cmd_run() {
    SPRING_PROFILES_ACTIVE=dev-snapshot ./mvnw spring-boot:run
}

case "${1:-}" in
    refresh) cmd_refresh ;;
    run) cmd_run ;;
    *)
        echo "Uso: $0 {refresh|run}" >&2
        exit 1
        ;;
esac
