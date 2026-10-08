# Mail de confirmación de aula al docente

Qué mandar cuando Subsecretaría toca **"Notificar al docente"**
(`POST /v1/room-requests/items/{itemId}/notify`). Es el único aviso que recibe el
docente y cierra el pedido: después ya no se cambia el aula.

Destinatario: `request.teacherEmail`.

## 1. Bloque común a todos los tipos

| Dato | De dónde sale | Nota |
|---|---|---|
| Nº de pedido | `item.id` | Es lo que cita el docente si consulta |
| Nombre del docente | `request.teacherName` | Para el saludo |
| Tipo de pedido | `request.type` | En texto legible (ver labels abajo) |
| Aula(s) | `item.assignedClassrooms[].roomNumber` | Puede ser **más de una** |
| Edificio | `item.assignedClassrooms[].buildingName` | Puede diferir por aula: mostrarlo junto a cada aula |

Y, si aplica:

- **Se resolvió con menos aulas de las pedidas**
  (`assignedClassrooms.length < item.classroomCount`) → párrafo explícito con
  `item.decisionReason`, para que no parezca un error.
- **Observaciones** del docente (`item.observations`).
- Cierre: "Ante cualquier inconveniente, respondé este mail" + contacto de
  Subsecretaría. No hay link de confirmación, el docente no responde por sistema.

**No mandar:** teléfono del docente, `item.decidedBy`, ni datos internos de
derivación (`derivedBuilding`, `returnedFromBuilding`, `returnedReason`).

## 2. Qué cambia según el tipo

Labels de tipo (usar estos textos, son los mismos que ve Subsecretaría en la app):

| `type` | Texto |
|---|---|
| `ONE_TIME_ROOM_CHANGE` | Cambio de aula — por única vez |
| `REGULAR_ROOM_CHANGE` | Cambio de aula — regular |
| `PARTIAL_EXAM_IN_CLASS` | Examen parcial — en horario de clases |
| `PARTIAL_EXAM_OFF_SCHEDULE` | Examen parcial — fuera de horario |
| `FINAL_EXAM` | Examen final |
| `CONFERENCE` | Congreso / Conferencia |
| `OTHER` | Otro |

### `REGULAR_ROOM_CHANGE` — cambio de aula regular

Es recurrente: no hay una fecha, hay un día de la semana y vale para todas las clases
de ahí en adelante. Una misma solicitud puede tener varios pedidos (varios días); si el
docente pidió lunes y miércoles, recibe un mail por cada día notificado.

- Materia + comisión
- Día de la semana (`dayOfWeek`) y horario
- Aula anterior (`item.currentClassroom`) → aula nueva (`assignedClassrooms`)
- Aclarar que aplica a todas las clases de ese día, no a una sola

> **Asunto:** Cambio de aula regular — Análisis Matemático II — 4K1
>
> Hola, Ana Gómez:
>
> Confirmamos el cambio de aula de tu pedido #158.
>
> - **Materia:** Análisis Matemático II (código 2345) — Comisión 4K1
> - **Día:** todos los lunes
> - **Horario:** 18:00 a 22:00
> - **Aula anterior:** Aula 210 (Edificio Central)
> - **Aula nueva:** Aula 305 (Edificio Central)
>
> Rige para todas las clases de ese día hasta fin del cuatrimestre.

### `ONE_TIME_ROOM_CHANGE` — cambio de aula por única vez

Igual que el regular, pero con una fecha puntual en vez de día de la semana. Aclarar que
es solo por ese día y que después vuelve al aula habitual.

- Materia + comisión, fecha, horario
- Aula anterior → aula nueva

> **Asunto:** Cambio de aula — Álgebra II — 14/10
>
> Hola, Marcos Ibáñez:
>
> Confirmamos el cambio de aula de tu pedido #172, solo para esa fecha.
>
> - **Materia:** Álgebra II (código 3310) — Comisión 2K4
> - **Fecha:** miércoles 14/10/2026
> - **Horario:** 18:00 a 22:00
> - **Aula anterior:** Aula 114 (Edificio Norte)
> - **Aula nueva:** Aula 220 (Edificio Norte)
>
> Después de esa clase volvés al aula habitual.

### `FINAL_EXAM` — examen final

Va por materia, no por comisión (`commissions` viene vacío, no se muestra esa línea).
Es siempre un solo pedido, con fecha y horario cargados por el docente.

- Materia (obligatoria) + fecha + horario + aula(s)

> **Asunto:** Aula confirmada — Final de Física I — 03/12
>
> Hola, Luis Torres:
>
> Confirmamos el aula para tu examen final, pedido #161.
>
> - **Materia:** Física I (código 1122)
> - **Fecha:** jueves 03/12/2026
> - **Horario:** 08:00 a 11:00
> - **Aula:** Aula 108 (Edificio Central)

### `PARTIAL_EXAM_IN_CLASS` — parcial en horario de clases

Fecha y horario salen del cursado de la comisión, los resuelve el back. Puede tener más
de una comisión.

- Materia + comisiones (puede ser más de una) + fecha + horario + aula(s)

> **Asunto:** Aula confirmada — Parcial de Programación I — 4K2 y 4K3
>
> Hola, Carla Núñez:
>
> Confirmamos el aula para el parcial de tu pedido #180.
>
> - **Materia:** Programación I (código 2201) — Comisiones 4K2 y 4K3
> - **Fecha:** lunes 20/10/2026
> - **Horario:** 18:00 a 20:00 (horario de cursado)
> - **Aula:** Aula 402 (Edificio Central)
>
> Pediste 2 aulas y asignamos 1: el horario de cursado coincide para las dos comisiones,
> entran juntas en esta aula.

Este ejemplo cubre la regla de "se resolvió con menos aulas de las pedidas"
(`assignedClassrooms.length < item.classroomCount`): el párrafo final usa
`item.decisionReason`.

### `PARTIAL_EXAM_OFF_SCHEDULE` — parcial fuera de horario

Fecha y horario los cargó el docente, fuera del cursado. Puede cubrir varias
comisiones en un mismo parcial: listarlas todas.

- Materia + comisiones + fecha + horario + aula(s)
- Remarcar que es fuera del horario habitual de cursado

> **Asunto:** Aula confirmada — Parcial de Base de Datos — fuera de horario
>
> Hola, Diego Farías:
>
> Confirmamos el aula para tu parcial, pedido #190. Es fuera del horario habitual de
> cursado.
>
> - **Materia:** Base de Datos (código 2450) — Comisiones 3K1, 3K2 y 3K5
> - **Fecha:** sábado 25/10/2026
> - **Horario:** 09:00 a 12:00
> - **Aula:** Aula 015 (Edificio Anexo)
>
> Observaciones: pediste proyector, ya está reservado en esa aula.

### `CONFERENCE` — congreso / conferencia

La materia puede venir en `null`, y no hay comisión. Si falta, el asunto se arma con el
tipo y lo que haya en `observations`.

- Fecha + horario + aula(s) + observaciones

> **Asunto:** Aula confirmada — Congreso / Conferencia
>
> Hola, Patricia Ríos:
>
> Confirmamos el aula para tu actividad, pedido #205.
>
> - **Fecha:** viernes 07/11/2026
> - **Horario:** 14:00 a 18:00
> - **Aula:** Aula 301 (Edificio Central)
>
> Observaciones: "Jornada de robótica, IEEE UTN FRC".

Sin materia ni comisión: el pedido no las pide para este tipo, así que esas líneas no
aparecen.

### `OTHER` — otro

Sin materia ni comisión. `observations` es obligatorio: ahí está la descripción de para
qué pidió el aula, y es el cuerpo del mensaje.

- Fecha + horario (pueden faltar) + aula(s) + observaciones destacadas

> **Asunto:** Aula confirmada — Otro
>
> Hola, Roberto Sáenz:
>
> Confirmamos el aula para tu pedido #211.
>
> - **Fecha:** martes 11/11/2026
> - **Aula:** Aula 118 (Edificio Central)
>
> Observaciones: "Grabación de material para el canal de YouTube de la carrera, necesito
> la 118 por el fondo con pizarrón".

Sin horario cargado para este pedido: se omite esa línea, no se imprime `null`.

## 3. Reglas de armado

1. **Varias aulas** → listarlas todas con su edificio, no solo la primera.
2. **Sin materia** (`subject: null`, en `CONFERENCE` y `OTHER`) → omitir la línea
   y armar el asunto con el tipo.
3. **Sin comisiones** (`commissions` vacío, en `FINAL_EXAM`, `CONFERENCE`, `OTHER`)
   → omitir la línea.
4. **Sin horario** → omitir la línea, nunca imprimir `null` ni "—".
5. Nunca dejar un campo vacío visible: si no hay dato, se cae la línea entera.

## 4. Pendiente / a confirmar

- El horario se muestra en los 7 tipos, en su propia fila, cuando el pedido tiene
  `startTime`/`endTime`: `ONE_TIME_ROOM_CHANGE` y `REGULAR_ROOM_CHANGE` sí lo capturan
  (`OneTimeRoomChangeHandler`/`RegularRoomChangeHandler` lo resuelven contra el
  cursado al dar de alta el ítem). Día/fecha y horario van siempre en filas separadas
  (`diaSemana`, `fecha`, `horario`), nunca concatenados en un mismo texto.
- ¿Mail de cancelación del pedido? Hoy el front solo dispara `notify`; la cancelación
  (`cancel`, con motivo) no manda nada.
- ¿Se avisa si el aula cambia después de notificar? Por regla de negocio no debería
  pasar: notificado = congelado.

Detalle completo de este análisis, y de por qué el diseño del mail depende de que se
modele el aula asignada en `roomrequest`: `.claude/plans/notificaciones-roomrequest-integracion.md`.
El diseño visual de la plantilla (HTML del mail) se resuelve aparte, fuera de esta
sesión: `.claude/plans/notificaciones-diseno-email.md`.