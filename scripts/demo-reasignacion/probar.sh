#!/usr/bin/env bash
# Prueba de punta a punta de la reasignación por rango y por lotes contra la API, sobre una clase demo
# (A) y las clases que ocupan el aula a la que se la quiere mover (B). Deja todo como estaba.
# Los argumentos salen de los candidatos de verificar.sql.
#
# Uso: SIGA_BASE_URL=http://localhost:8080/api SIGA_EMAIL=<correo> SIGA_PASSWORD=<clave> \
#        scripts/demo-reasignacion/probar.sh <evento> <aula actual> <aula libre> <aula ocupada>

set -euo pipefail

[ $# -eq 4 ] || { echo "Uso: $0 <evento> <aula actual> <aula libre> <aula ocupada>"; exit 2; }
: "${SIGA_BASE_URL:?Falta SIGA_BASE_URL (con /api al final)}"
: "${SIGA_EMAIL:?Falta SIGA_EMAIL}"
: "${SIGA_PASSWORD:?Falta SIGA_PASSWORD}"

EVENT=$1 ORIGINAL=$2 FREE=$3 BUSY=$4
FROM=2026-12-01
BODY=$(mktemp)
BLOCKERS='[]'
DONE=false

TOKEN=$(curl -sS -X POST "$SIGA_BASE_URL/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg e "$SIGA_EMAIL" --arg p "$SIGA_PASSWORD" '{email: $e, password: $p}')" | jq -er .accessToken)

# Si la prueba corta a mitad de camino, devuelve A (y B, si llegó a moverse) a su aula original.
restore() {
    if [ "$DONE" != true ]; then
        local items
        items=$(range_item "$ORIGINAL")
        [ "$BLOCKERS" != '[]' ] && items="$items $(blockers_item "$BUSY")"
        local status
        status=$(call PUT /v1/allocations "$(echo "$items" | batch)") || true
        echo "Restauración de las aulas originales: status $status"
    fi
    rm -f "$BODY"
}
trap restore EXIT

# Item de rango de la clase A: <aula> [hasta]
range_item() {
    jq -n --argjson ev "$EVENT" --argjson room "$1" --arg from "$FROM" --arg to "${2:-}" \
        '{eventId: $ev, from: $from, classroomId: $room} + (if $to == "" then {} else {to: $to} end)'
}

# Item de las clases B por id de ocurrencia: <aula>
blockers_item() {
    jq -n --argjson ids "$BLOCKERS" --argjson room "$1" '{occurrenceIds: $ids, classroomId: $room}'
}

batch() {
    jq -s '{items: ., observation: "Prueba de reasignación por rango (demo)"}'
}

call() {
    curl -sS -o "$BODY" -w '%{http_code}' -X "$1" "$SIGA_BASE_URL$2" \
        -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$3"
}

check() {
    local step=$1 expected=$2 status=$3 condition=$4
    if [ "$status" != "$expected" ] || ! jq -e "$condition" "$BODY" >/dev/null; then
        echo "FALLA $step: status $status (esperaba $expected), condición: $condition"
        jq . "$BODY" 2>/dev/null || cat "$BODY"
        exit 1
    fi
    echo "OK    $step"
}

echo "-- Reasignación por rango de una clase"

status=$(call POST /v1/allocations/impact "$(range_item "$FREE" | batch)")
check "1. impacto de 'Clase completa' al aula libre" 200 "$status" '.totalClasses >= 8 and .blockedClasses == 0'
echo "      clases alcanzadas: $(jq .totalClasses "$BODY")"

status=$(call PUT /v1/allocations "$(range_item "$FREE" 2026-12-28 | batch)")
check "2. rango temporal 01/12 a 28/12 al aula libre" 200 "$status" 'length == 4'

echo "-- Conflictos"

status=$(call POST /v1/allocations/impact "$(range_item "$BUSY" | batch)")
check "3. impacto al aula ocupada muestra choques con alternativas" 200 "$status" \
    '.blockedClasses > 0 and (.conflicts | length) > 0 and all(.conflicts[]; (.alternativeClassrooms | length) > 0)'
echo "      bloqueadas: $(jq .blockedClasses "$BODY") de $(jq .totalClasses "$BODY")"
BLOCKERS=$(jq -c '[.conflicts[].blockedBy | select(.kind == "EXISTING_ALLOCATION") | .occurrenceId] | unique' "$BODY")
[ "$(jq length <<<"$BLOCKERS")" -gt 0 ] || { echo "FALLA 3: ningún choque es contra una asignación existente"; exit 1; }

status=$(call PUT /v1/allocations "$(range_item "$BUSY" | batch)")
check "4. cambio al aula ocupada responde 409 y no aplica nada" 409 "$status" '(.conflicts | length) > 0'

echo "-- Reasignación por lotes"

status=$(call POST /v1/allocations/impact "$({ range_item "$FREE"; blockers_item "$FREE"; } | batch)")
check "5. un lote que manda A y B a la misma aula marca el choque como SAME_BATCH" 200 "$status" \
    'any(.conflicts[]; .blockedBy.kind == "SAME_BATCH")'

status=$(call PUT /v1/allocations "$({ range_item "$FREE"; blockers_item "$FREE"; } | batch)")
check "6. ese mismo lote responde 409" 409 "$status" '(.conflicts | length) > 0'

# Destrabar: A va al aula de B y B se corre al aula de A. Si B no entra ahí (por ejemplo, porque dura
# más que A), se corre a una de las alternativas que el impacto propone para todas sus fechas.
B_TARGET=$ORIGINAL
status=$(call POST /v1/allocations/impact "$({ range_item "$BUSY"; blockers_item "$B_TARGET"; } | batch)")
if [ "$status" = 200 ] && jq -e '.blockedClasses > 0' "$BODY" >/dev/null; then
    ALT=$(jq --argjson b "$BLOCKERS" '
        [.conflicts[] | select(.occurrenceId as $o | $b | index($o)) | [.alternativeClassrooms[].id]]
        | if length == 0 then null
          else reduce .[1:][] as $x (.[0]; [.[] | select(. as $i | $x | index($i))]) | .[0] end' "$BODY")
    [ "$ALT" != null ] || { echo "FALLA 7: B no entra en el aula de A y no hay una alternativa común a todas sus fechas"; jq . "$BODY"; exit 1; }
    echo "      B no entra en el aula $ORIGINAL; el impacto propone el aula $ALT para todas sus fechas"
    B_TARGET=$ALT
    status=$(call POST /v1/allocations/impact "$({ range_item "$BUSY"; blockers_item "$B_TARGET"; } | batch)")
fi
check "7. impacto del lote que destraba (A al aula de B, B al aula $B_TARGET) sin choques" 200 "$status" '.blockedClasses == 0'

status=$(call PUT /v1/allocations "$({ range_item "$BUSY"; blockers_item "$B_TARGET"; } | batch)")
check "8. lote aplicado en una sola operación" 200 "$status" 'length > 0'

status=$(call PUT /v1/allocations "$({ range_item "$ORIGINAL"; blockers_item "$BUSY"; } | batch)")
check "9. lote revertido en una sola operación" 200 "$status" 'length > 0'

echo "-- Estado final"

status=$(call POST /v1/allocations/impact "$(range_item "$ORIGINAL" | batch)")
check "10. A quedó en su aula original en todas las fechas" 200 "$status" \
    "all(.occurrences[]; .currentClassroomId == $ORIGINAL)"

status=$(call POST /v1/allocations/impact "$(blockers_item "$BUSY" | batch)")
check "11. B quedó en su aula original" 200 "$status" "all(.occurrences[]; .currentClassroomId == $BUSY)"

DONE=true
echo "Prueba completa."
