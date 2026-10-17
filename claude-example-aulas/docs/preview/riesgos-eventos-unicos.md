# Riesgos del preview de eventos únicos y aulas simultáneas

Revisión del commit `2ea78f4f` (back) y de los cambios sin commitear del front en `develop`, hecha el 2026-10-14. Los tests del back (101) y del front (111) pasan; los riesgos de abajo no los cubre ningún test.

## 1. Re-optimizar eventos ya asignados (sin verificar)

`PreviewEngine.datesByKey` filtra `status == NEEDS_ROOM && !isPast()`. Antes tomaba todas las ocurrencias futuras. `OccurrenceStatus` solo tiene `NEEDS_ROOM` y `ROOM_RELEASED`, así que un evento con aula asignada queda sin fechas y `generate` lo saca del solver.

El front sigue manejando `unchanged` y `unmodifiedCount` en la pestaña `overlaps`. Si esa pestaña lanza preview sobre eventos ya asignados, ahora el back devuelve "sin eventos" o una respuesta vacía.

Pendiente: confirmar si `overlaps` lanza preview sobre eventos con aula. Si es así, ampliar el filtro o cubrirlo con un test de integración.

## 2. Nombre fijo del recurso "Cantidad de PC"

Se borró `COMPUTERS_RESOURCE_NAME` de `RoomRequestCandidateResolver` y ahora se usa `ClassroomResources.COMPUTERS`. Funciona mientras el recurso se llame igual en la base. Si se renombra, la validación de computadoras deja de encontrar cantidades y marca `COMPUTERS` como no cumplido en todas las aulas.

## 3. Clave de fila en el front

`previewRowId(eventId, roomSlot) = eventId * 100 + roomSlot` (`backend.mappers.ts`) asume menos de 100 aulas por evento. Con 100 o más, dos filas distintas comparten clave en `pendingMoves` y en el calendario.

## 4. Detección de eventos únicos en el front

`isUniqueOccurrence` (`utils/eventDay.ts`) devuelve `!occ.dayOfWeek`. Supone que el back nunca manda `dayOfWeek` en un evento único. Si el DTO cambia, la columna Día y el orden por Día se rompen sin error.

## 5. Significado de `skippedEventIds` en el confirm

Si un evento tiene un `roomSlot` con aula y otro sin ella, `PreviewServiceImpl.confirm` lo incluye en `skippedEventIds` y también en lo aplicado. El front lo maneja con un toast ("Algunas aulas de X quedaron sin asignar"). Cualquier otro cliente que lea `skippedEventIds` como "evento no aplicado" va a contar mal.
