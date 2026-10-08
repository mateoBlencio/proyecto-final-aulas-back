# Solape tolerado en asignaciones de aula

Rama `feature/superposiciones-de-asignaciones`. Hasta ahora, `POST` y `PUT
/v1/allocations` rechazaban con 409 cualquier asignación manual que
compartiera aula y franja horaria con otra, aunque fuera un minuto de
superposición. Ahora existe un margen configurable, en minutos, dentro del
cual ese solape se permite si la operación viene con un comentario.

## Qué expone

El módulo de asignaciones sigue creando, reasignando y liberando aulas por
ocurrencia o por evento. Lo nuevo es que la validación de solape de
aula/fecha en la asignación manual deja de ser todo o nada: por debajo de
un margen configurable el pedido se acepta con un comentario obligatorio,
y por encima sigue dando 409 igual que siempre. El módulo de configuración
expone ese margen como un setting más en `GET /v1/settings`.

## Endpoints

| Método | Path | Permiso requerido | Qué hace |
|---|---|---|---|
| `POST` | `/v1/allocations` | `PERM_ALLOCATION_WRITE` | Crea asignaciones en lote. 400 `Observation required` si el lote genera un solape tolerado sin `observation`; 409 si algún solape supera el margen o por las causas de siempre (aula inexistente, ocurrencia ya asignada). |
| `PUT` | `/v1/allocations` | `PERM_ALLOCATION_WRITE` | Reasigna en lote (upsert). Mismos códigos 400/409 que el `POST`. |
| `POST` | `/v1/allocations/impact` | `PERM_ALLOCATION_WRITE` | Simula el mismo body de `POST`/`PUT` sin escribir. Responde 200 con `toleratedOverlaps` cuando el pedido va a generar un solape dentro del margen. Sigue devolviendo 400 si el body está mal formado y 409 si pide un aula inexistente o una ocurrencia pasada. |
| `GET` | `/v1/allocations/conflicts` | `PERM_CONFLICT_READ` | Lista conflictos, `types=OVERLAP` incluido. Sin cambios de contrato: sigue listando cualquier solape existente, tolerado o no. |
| `GET` | `/v1/settings` | `PERM_SETTINGS_READ` | Devuelve los settings agrupados por categoría. Aparece el grupo nuevo `allocation` con la clave `allocation.maxOverlapMinutes`. |
| `GET` | `/v1/settings/{key}` | `PERM_SETTINGS_READ` | Devuelve un setting puntual, por ejemplo `allocation.maxOverlapMinutes`. |
| `PUT` | `/v1/settings/{key}` | `PERM_SETTINGS_WRITE` | Actualiza un setting. Solo el rol Subsecretaría Académica tiene este permiso; cualquier otro rol recibe 403. |

## Modelos relevantes

**`AllocationBatchRequestDto`** (body de `POST`/`PUT`/`POST /impact`):

| Campo | Tipo | Obligatorio |
|---|---|---|
| `items` | `List<AllocationItemRequestDto>` | Sí, no vacío |
| `observation` | `String` | Solo si el lote genera un solape tolerado; el resto de los casos lo aceptan vacío o ausente |

`AllocationItemRequestDto` no cambió: `occurrenceIds` o `eventId` (mutuamente
excluyentes), `from`/`to` opcionales junto a `eventId`, `classroomId`
obligatorio.

**`AllocationImpactResponseDto`** (respuesta de `POST /impact`) suma un
campo:

```json
{
  "totalClasses": 2,
  "movableClasses": 2,
  "blockedClasses": 0,
  "occurrences": [ ... ],
  "conflicts": [],
  "toleratedOverlaps": [
    {
      "occurrenceId": 501,
      "date": "2026-04-14",
      "startTime": "09:20:00",
      "endTime": "11:00:00",
      "classroomId": 12,
      "conflictingEventId": 88,
      "conflictingAllocationId": 340,
      "conflictingOccurrenceId": 499,
      "overlapMinutes": 40
    }
  ]
}
```

Una ocurrencia con solape tolerado cuenta en `movableClasses`, no en
`blockedClasses`: el backend la va a poder escribir si el front confirma.
`conflicts` (`List<ImpactConflictDto>`) sigue reservado para lo que bloquea
de verdad.

**`OccurrenceConflictDto`** (aparece en `toleratedOverlaps`, en la property
`overlaps` del 400 y en la property `conflicts` del 409) suma
`overlapMinutes`:

| Campo | Tipo |
|---|---|
| `occurrenceId` | `Long` |
| `date` | `LocalDate` |
| `startTime` / `endTime` | `LocalTime` |
| `classroomId` | `Long` |
| `conflictingEventId` | `Long` |
| `conflictingAllocationId` | `Long` |
| `conflictingOccurrenceId` | `Long` |
| `overlapMinutes` | `int` |

**Error 400, comentario faltante.** Cuando el lote genera un solape
tolerado y `observation` viene vacío o ausente, `POST`/`PUT` responden:

```json
{
  "status": 400,
  "title": "Observation required",
  "detail": "Esta asignación genera 1 superposición(es) dentro del margen permitido. Indicá una observación explicando el motivo.",
  "overlaps": [
    { "occurrenceId": 501, "overlapMinutes": 30, "...": "..." }
  ]
}
```

`title` es el valor exacto para distinguir este 400 de cualquier otro. La
property `overlaps` trae los mismos pares que traería `toleratedOverlaps`
en `/impact`, para pedirle el motivo al usuario sin perder de vista contra
qué chocó.

**Error 409, solape fuera de margen.** Sin cambios de forma: `title`
`Reallocation conflict`, property `conflicts` con la misma lista de
`OccurrenceConflictDto` (ahora con `overlapMinutes`).

**`SettingResponseDto`** suma `defaultValue`, para los once settings
existentes, no solo para el nuevo:

```json
{
  "key": "allocation.maxOverlapMinutes",
  "type": "INT",
  "value": "40",
  "defaultValue": "40",
  "riskLevel": "ADVANCED",
  "min": "0",
  "max": null,
  "warning": "Dos eventos distintos pueden compartir un aula durante este margen, solo en asignación manual y dejando un comentario. Subirlo esconde choques reales de horario; 0 vuelve al rechazo estricto."
}
```

`allocation.maxOverlapMinutes` es la primera clave con `riskLevel`
`ADVANCED` y la primera con `warning` no nulo; el resto de los settings hoy
tienen `riskLevel: "SAFE"` y `warning: null`. `max` no viene: no hay techo
de negocio para el margen, solo el límite técnico de que el valor entre en
un entero de Java.

`UpdateSettingRequestDto` (body de `PUT /v1/settings/{key}`) no cambió:
`{ "value": "60" }`.

## Reglas a tener en cuenta en el frontend

- El margen aplica solo a asignación manual, es decir a lo que pasa por
  `POST`/`PUT /v1/allocations`. La carga por Excel, el sync de SysAcad y
  aplicar una propuesta del optimizador nunca tienen margen: cualquier
  solape ahí sigue dando 409 sin excepción, aunque sea de un minuto.
- El flujo recomendado antes de escribir es llamar a
  `POST /v1/allocations/impact` con el mismo body que va a mandar al
  `POST`/`PUT`. Si `toleratedOverlaps` viene con elementos, mostrar el
  aviso de que la operación va a superponer, con `overlapMinutes` y el
  evento u ocurrencia contra el que choca, y habilitar el campo de
  comentario antes de dejar confirmar.
- El comentario es por lote entero, no por ítem: `observation` es un campo
  de `AllocationBatchRequestDto`, y si un solo par del lote cae en el
  margen, todo el lote lo exige y esa misma observación queda guardada en
  cada asignación del lote y en su historial de auditoría.
- El 400 `Observation required` hay que capturarlo por `title`, no tratarlo
  como un error de validación genérico: es la señal de "falta un dato",
  no de "el pedido está mal". La property `overlaps` trae todo lo
  necesario para pedirle el motivo al usuario sin repetir la llamada a
  `/impact`.
- El margen se evalúa por par, no acumulado: si un evento choca 30 minutos
  con uno y otros 30 minutos con otro evento distinto, los dos pares pasan
  el margen de 40 por separado, no se suman entre sí.
- `GET /v1/allocations/conflicts` con `types=OVERLAP` sigue devolviendo
  estos solapes, incluso los creados a propósito dentro del margen. No es
  un error del backend: el tablero muestra la ocupación real del aula, no
  la autorización con la que se creó cada asignación.
- Para decidir cuándo mostrar una advertencia propia en la UI de
  configuración, comparar `value` contra `defaultValue` en vez de
  hardcodear 40: el campo `warning` que manda el backend es un texto fijo
  del setting, no cambia según qué tan lejos esté `value` del default.
- Cambiar `allocation.maxOverlapMinutes` exige `PERM_SETTINGS_WRITE`, que
  hoy solo tiene el rol Subsecretaría Académica. Un Auxiliar Áulico puede
  seguir creando asignaciones dentro del margen vigente con
  `PERM_ALLOCATION_WRITE`, pero un intento de `PUT /v1/settings/{key}` con
  ese rol responde 403.

## Ejemplo de extremo a extremo

Evento A ocupa el aula 12 hasta las 10:00. Con el margen en 40 minutos
(default):

- Asignar el evento B en el aula 12 arrancando a las **9:20** entra
  justo en el margen: `overlapMinutes` da 40, `/impact` devuelve la
  ocurrencia en `toleratedOverlaps`, y el `POST`/`PUT` con `observation`
  responde 201/200.
- Asignar B arrancando a las **9:19** ya no entra: `overlapMinutes` da 41,
  y el `POST`/`PUT` responde 409 con `Reallocation conflict`, tenga o no
  `observation` el pedido.

## Punto abierto

`riskLevel: "ADVANCED"` es hoy metadata informativa: el backend no la usa
para exigir un paso extra ni una confirmación adicional a la de
`PERM_SETTINGS_WRITE`. Si el front quiere pedir una confirmación reforzada
antes de guardar un cambio en una clave `ADVANCED`, esa lógica va del lado
del cliente; el backend no la va a aplicar por su cuenta.
