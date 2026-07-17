# Restricciones de la asignación de aulas — mapa completo

Referencia autocontenida de TODAS las reglas que gobiernan qué aula puede
recibir qué evento: las que evalúa el solver como score, las implícitas que se
aplican antes/después del solve, y las validaciones de negocio de los flujos de
escritura. Última actualización: 2026-07-16.

## 1. Restricciones de score del solver (`ClassroomConstraintProvider`)

Score `HardMediumSoftScore`; jerarquía **HARD ≫ MEDIUM ≫ SOFT** (un punto de un
nivel superior domina a cualquier suma del inferior). Pesos SOFT configurables
vía `siga.solver.weights.*` en `application.yaml`.

| Restricción | Nivel | Peso (default) | Config | Regla |
|---|---|---|---|---|
| Sin solapamiento (`noOverlap`) | **HARD** | 1/par | — | Dos asignaciones no comparten aula si sus horarios se solapan (adyacencia precalculada por fecha). Pares pinned-pinned excluidos: conflicto preexistente en BD no es corregible por el solver. |
| Asignar todo lo posible (`allocateAllPossible`) | **MEDIUM** | 1/evento | — | Penaliza cada evento no-pinned que quede sin aula. Empuja a asignar siempre que no rompa el HARD (aun sobrecupando); solo queda sin aula lo inubicable → viaja en `unresolved`. |
| Minimizar sobreocupación (`minimizeOvercrowding`) | SOFT | 100.000 × alumno excedente | `siga.solver.weights.overcrowding` | Penaliza asignar aula con menos capacidad que inscriptos, proporcional al excedente. Casi-hard en la práctica por el peso. |
| Preferir mismo edificio por comisión (`preferSameBuildingSameCommission`) | SOFT | 4.000/par | `siga.solver.weights.same-commission-diff-building` | Dos eventos de la misma comisión en edificios distintos penalizan. |
| Preferir misma aula por comisión (`preferSameRoomSameCommission`) | SOFT | 2.000/par | `siga.solver.weights.same-commission-diff-room` | Dos eventos de la misma comisión en aulas distintas penalizan (se suma a la anterior: distinta aula y distinto edificio = 6.000). |
| Minimizar subocupación (`minimizeUnusedCapacity`) | SOFT | 1 × asiento libre | — | Desempate final hacia el aula más ajustada al curso. |

## 2. Reglas implícitas del pipeline (no son score, pero restringen igual)

| Regla | Dónde se aplica | Efecto |
|---|---|---|
| Solo aulas disponibles | `AutoAllocationDataLoader` (pre-solve): `classroomService.findAllAvailable()` | Un aula con `available = false` nunca es candidata; el solver ni la ve. |
| Aula única por evento | Modelo de planificación (`ClassAllocation`: una variable aula por evento) | **Requisito de negocio confirmado (2026-07-16)**: la asignación se maneja por EVENTO; las ocurrencias son su manifestación. Todas las ocurrencias futuras de un evento reciben la MISMA aula; un solape en una sola fecha descarta esa aula para el evento entero (no se admite split por fecha). |
| Ocupación existente inmovible | `SolverServiceImpl.buildExistingOccupancy` (pinned) | Las asignaciones firmes de BD bloquean sus franjas; el solver no puede moverlas. Si su aula no es candidata, se descartan (no pueden colisionar). |
| Ocupación duplicada colapsada | `buildExistingOccupancy` (dedup por `planningId`) | Dos allocations preexistentes conflictivas en BD (misma aula/fecha/hora) entran como una sola; la descartada loguea warn. |
| Solo ocurrencias pendientes y futuras | `AutoAllocationServiceImpl.autoPreview` + loader | Evento sin ocurrencias pendientes de asignación no entra al solve (todas asignadas/pasadas ⇒ 409 si el lote entero queda vacío). |
| Piso de no-regresión (aula previa) | `AutoAllocationServiceImpl.compose` (post-solve) | Evento que el solver dejó sin aula pero que YA tenía aula asignada conserva la previa y va a resueltos; solo lo sin aula previa cae en `unresolved`. |
| Comisión de la ocupación pinned invisible | `buildExistingOccupancy` (`commissionKey = null`) | ⚠️ Limitación conocida: las preferencias por comisión NO atraen eventos nuevos hacia aulas donde la comisión ya cursa según BD; solo agrupan dentro del mismo preview. |

## 3. Validaciones de negocio en los flujos de escritura (`AllocationValidator`)

Compartidas entre flujo manual y confirm automático; todas cortan con 409 antes
de la primera escritura.

| Validación | Regla |
|---|---|
| `validateNoOverlap` | Ningún candidato solapa con asignaciones ASSIGNED firmes de BD ni con otros candidatos del mismo lote (misma aula, misma fecha, franjas que se pisan; fin == inicio no es solape). |
| `validateClassroomsAvailable` | Toda aula referenciada existe y está `available = true`. |
| `validateNotPast` / `isApplicable` | Ocurrencia ya sucedida no se modifica; en lotes se saltea. |
| `validateAssignable` | Ocurrencia CANCELLED o SUSPENDED no recibe aula. |
| `validateEventNotFinished` | Evento con todas sus ocurrencias pasadas no se reasigna. |
| `validateNoDuplicateEventIds` | La propuesta final del confirm no repite eventos. |
| `validateAllocationsBelongToPreview` / `validateBelongsToPreview` | Confirm y validate-move solo aceptan eventos del preview vigente (preview expirado ⇒ 410). |
| Source estampado adentro | `source` (MANUAL/AUTOMATIC/IMPORTED) lo decide el método de servicio, nunca el cliente. |

## 4. Restricciones propuestas (no implementadas)

| Nivel | Propuesta | Valor | Requiere |
|---|---|---|---|
| SOFT | Estabilidad: preferir aula previa del evento | Re-corridas del solver no barajan todo de nuevo; hoy el piso post-solve solo evita regresión, no atrae | Pasar aula previa en `SolverEvent` + constraint nueva |
| SOFT | Atracción a ocupación firme de la comisión | Corrige la limitación ⚠️ de la sección 2: eventos nuevos se agrupan con las clases que la comisión ya tiene en BD | `commissionKey` en `SolverOccupancy` (hoy va null en pinned) |
| MEDIUM | Ponderar "sin aula" por inscriptos | Si algo queda afuera, que sea el curso chico, no el de 200 | Peso proporcional a `enrolled` en `allocateAllPossible` |
| MEDIUM | Regulares > únicos | Preferencia de negocio confirmada: ante escasez, el cursado regular gana | Modelar eventos únicos en el flujo del solver (hoy no entran); peso por tipo de evento |
| HARD | Tipo de aula requerido | Materia que necesita laboratorio no puede caer en aula común (inválido, no penalizable) | Modelar "tipo requerido" en el evento + DDL al DBA + dato en `SolverEvent`/`SolverRoom` |
| — | Capacidad se mantiene SOFT | Sobrecupo nominal chico es viable (ausentismo); el peso 100.000 ya lo hace casi-hard | Nada — decisión explícita de no endurecer |

Descartadas por ahora: balance de uso entre aulas (poco valor), distancia entre
edificios en horas consecutivas (mismo-edificio lo aproxima), accesibilidad
(sin datos modelados).
