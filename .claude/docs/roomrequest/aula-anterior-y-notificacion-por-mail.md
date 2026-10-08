# Aula anterior y notificación por mail al resolver un pedido

Rama `feature/roomrequest-notificacion-email` (PR #49, contra `develop`).
Trae dos cambios sobre el flujo de resolución de un pedido de aula: un
campo nuevo en la respuesta de los ítems y un cambio de comportamiento en
`POST /notify`, que ahora manda un mail real al docente en vez de solo
cambiar el estado del pedido.

## Qué expone

El módulo sigue resolviendo pedidos de aula por los mismos pasos: derivar
a un edificio, asignar aula(s) con `assign`, y confirmar con `notify`. Lo
nuevo es que un `RoomRequestItemResponseDto` puede traer el aula que el
docente tenía antes de un cambio de aula, y que `notify` deja de ser un
cambio de estado sin efecto visible para el docente.

## Endpoints

| Método | Path | Permiso requerido | Qué cambió |
|---|---|---|---|
| `POST` | `/v1/room-requests/items/{id}/assign` | `PERM_ROOM_REQUEST_WRITE` | Sin cambios de firma. En pedidos `ONE_TIME_ROOM_CHANGE` y `REGULAR_ROOM_CHANGE`, además de asignar el aula nueva, congela el aula anterior de la ocurrencia en `previousClassroom`. |
| `POST` | `/v1/room-requests/items/{id}/notify` | `PERM_ROOM_REQUEST_WRITE` | Sin cambios de firma, permiso ni códigos de estado. El pedido pasa a `RESOLVED` igual que antes, pero ahora dispara el mail de "pedido resuelto" al docente a través del módulo `notification`. |

Ningún otro endpoint del módulo cambió de contrato. `GET /v1/room-requests/items`
y `GET /v1/room-requests/items/{id}` siguen igual, pero como comparten el
mismo `RoomRequestItemResponseDto`, también devuelven `previousClassroom`
desde esta rama.

## Modelos relevantes

**`RoomRequestItemResponseDto`** suma un campo:

| Campo | Tipo | Obligatorio |
|---|---|---|
| `previousClassroom` | `AssignedClassroomDto` | No, nullable |

`AssignedClassroomDto` no cambió: `id` (`Long`), `roomNumber` (`Integer`),
`buildingName` (`String`), `capacity` (`Integer`).

```json
{
  "id": 421,
  "status": "IN_EVALUATION",
  "assignedClassrooms": [
    { "id": 18, "roomNumber": 205, "buildingName": "Edificio Central", "capacity": 40 }
  ],
  "previousClassroom": { "id": 12, "roomNumber": 108, "buildingName": "Edificio Central", "capacity": 35 }
}
```

Este fragmento sale de `RoomRequestItemAssignApiIntegrationTest.assign_oneTimeRoomChange_guardaAulaAnteriorYNoLaPisaEnReasignacion`,
que ejercita el caso completo: asignar A, después B, y comprobar que
`previousClassroom` sigue apuntando a la primera.

## Reglas a tener en cuenta en el frontend

- `previousClassroom` solo se completa en pedidos de tipo
  `ONE_TIME_ROOM_CHANGE` y `REGULAR_ROOM_CHANGE`. En los otros cinco tipos
  de `RoomRequestType` (`PARTIAL_EXAM_IN_CLASS`, `PARTIAL_EXAM_OFF_SCHEDULE`,
  `FINAL_EXAM`, `CONFERENCE`, `OTHER`) siempre viene `null`, porque esos
  pedidos no cambian una clase de aula, la asignan por primera vez.
- El campo se completa una sola vez, en el primer `assign` que encuentra
  una aula ya asignada a la ocurrencia. Si Subsecretaría reasigna el aula
  varias veces antes de notificar (A a B, después B a C), `previousClassroom`
  sigue mostrando A, la aula que el docente veía antes de cualquier cambio,
  no B.
- Si en el momento del primer `assign` la ocurrencia todavía no tenía
  ninguna aula asignada, `previousClassroom` queda `null` en esa respuesta.
  Un `assign` posterior sobre el mismo ítem sí encuentra una aula asignada,
  la que dejó el primer `assign`, y la graba como "anterior" recién ahí. En
  ese caso puntual, lo que el docente termina viendo como aula anterior es
  la primera que le llegó a asignar Subsecretaría, no la aula original de
  la ocurrencia.
- `POST /notify` sigue devolviendo 200 con el pedido en `RESOLVED`, 409 si
  el estado no es `IN_EVALUATION` y 400 si el pedido no tiene ninguna aula
  asignada. Estos códigos no cambiaron; lo que cambió es que un 200 ahora
  implica que se encoló un mail real al docente.
- El envío del mail queda encolado dentro de la misma transacción de
  `notify`, pero el despacho es asíncrono, vía el módulo `notification`.
  El endpoint responde sin esperar a que el mail salga, y una falla en la
  entrega no vuelve como error en la respuesta de `notify`: el frontend no
  tiene que sondear ni manejar ese caso.
- La idempotencia de `notify` no cambió: si el ítem ya tiene `notifiedAt`
  seteado, una segunda llamada devuelve 200 con el mismo estado, sin
  reenviar el mail ni volver a resellar la fecha.
