# Módulo `solver`

## Responsabilidad

**Motor de optimización puro** (Timefold Solver). Recibe eventos, aulas y ocupación
existente; devuelve una asignación evento→aula óptima como **preview** (no persiste,
no lee la BD). El ejemplo limpio del paradigma: define sus propios records de entrada/
salida y no consume entidades ni DTOs de otros módulos.

## API pública (`::api`)

`SolverService`:

| Método | Descripción |
|---|---|
| `preview(events, classrooms, occupancy, timeLimitSeconds)` | Corre el solver, guarda la preview en el store y la devuelve |
| `getPreview(previewId)` | Recupera preview guardada; `ExpiredPreviewException` (410) si no existe/expiró |
| `invalidatePreview(previewId)` | Elimina la preview del store tras confirmarla: un re-confirm del mismo `previewId` da 410 (protección natural contra doble submit) |

Records `::api` (contrato con `allocation`): `SolverEvent`, `SolverRoom`,
`SolverOccupancy`, `SolverPreview`, `SolverAllocation`. Todos inmutables y sin dependencia
externa. `allocation` mapea sus entidades a estos records y consume el resultado vía su
propia fachada (`AutoAllocationService`); el solver nunca ve entidades JPA.

## Servicios: cómo funcionan

### `SolverServiceImpl` — orquestación del solve

`preview(events, classrooms, occupancy, timeLimitSeconds)` ejecuta este pipeline:

1. **Indexa aulas por id** (`Map<Integer, SolverRoom>`) para resolver la ocupación.
2. **`buildExistingOccupancy`** — convierte cada `SolverOccupancy` (aula ocupada en
   fecha/hora) en un par evento-sintético + aula:
   - Si el aula ocupada **no está entre las candidatas**, se descarta: no puede
     colisionar con ningún evento nuevo.
   - El evento sintético lleva `planningId = "occupied:aula:fecha:hora"` y **se
     deduplica por ese id** (`LinkedHashMap`, gana el primero, el duplicado loguea
     warn): dos allocations preexistentes conflictivas en BD generarían el mismo
     `@PlanningId` y Timefold explota.
3. **`computeConflicts`** — construye la adyacencia de solapamiento horario
   (`planningId → set de planningIds que solapan`) sobre **nuevos + existentes juntos**,
   así el `noOverlap` hard puede bloquear un aula ocupada para los eventos nuevos.
   Algoritmo: agrupa eventos por fecha de ocurrencia, dentro de cada fecha ordena por
   hora de inicio y barre con dos índices cortando (`break`) apenas el siguiente evento
   empieza después del fin del actual — evita el producto cartesiano. El `Set` por
   evento deduplica pares que se repiten en varias fechas compartidas; la adyacencia
   resultante es simétrica.
4. **`solve`** — arma el problema Timefold:
   - Cada evento nuevo → `ClassAllocation` planificable (value range = todas las aulas,
     variable `classroom` sin asignar).
   - Cada ocupación existente → `ClassAllocation.pinned(...)`: aula fija como única
     candidata, `@PlanningPin` activo; el solver no la puede mover, solo participa del
     no-solapamiento.
5. **`runSolver`** — dispara el job vía `SolverManager` (bean del starter de Timefold,
   paralelismo según `siga.solver.parallel-solver-count`) con un
   `SolverConfigOverride` de terminación por request: límite total
   `timeLimitSeconds` + corte temprano por `unimprovedSecondsLimit` (segundos sin
   mejora del mejor score; 0 lo deshabilita). Espera bloqueante el
   `getFinalBestSolution()`:
   - `InterruptedException` → re-interrumpe el thread, `terminateEarly()` y
     `SchedulingException`.
   - `ExecutionException` → loguea la causa y la envuelve en `SchedulingException`.
6. **`toPreview`** — proyecta la solución al contrato público: **filtra las pinned**
   (la ocupación existente no es parte de la propuesta), mapea cada allocation a
   `SolverAllocation(planningId, classroomId)` (`classroomId` puede ser `null` si el
   solver no encontró aula) y genera `previewId = "prev_" + 8 hex de UUID`.
7. **Persiste la preview en `PreviewStore`** y la devuelve.

`getPreview(previewId)` delega en el store; `Optional` vacío ⇒
`ExpiredPreviewException` (mapeada a 410 Gone). `invalidatePreview(previewId)` hace
`remove` en el store; `allocation` lo llama al final del confirm, tras persistir.

### `PreviewStore` → `CaffeinePreviewStore` — ciclo de vida de la preview

Cache in-memory (Caffeine) con `expireAfterWrite` según
`siga.solver.preview.ttl-minutes` (default 30). El TTL acota la **obsolescencia** de la
propuesta (la ocupación real puede cambiar entre preview y confirm), no es eviction por
memoria. Tres operaciones: `save`, `get` (`Optional`), `remove`. Flujo completo:
`preview` guarda → el usuario revisa/ajusta (via `allocation`) → confirm persiste y
llama `invalidatePreview` → cualquier reuso posterior del id da 410.

### `ClassroomConstraintProvider` — restricciones de score (`HardMediumSoftScore`)

No es un bean: Timefold lo instancia desde `SolverConfiguration`. Opera sobre pares o
individuos de `ClassAllocation`:

| Constraint | Tipo | Regla |
| --- | --- | --- |
| `noOverlap` | **HARD** | Par único con misma aula asignada cuyo `conflictsWith` (adyacencia precalculada) da true ⇒ 1 hard. Los pares **pinned-pinned se excluyen** (conflicto preexistente en BD, el solver no puede arreglarlo y penalizarlo solo ensucia el score). |
| `assignAllPossible` | **MEDIUM** | Por allocation no-pinned **sin aula** (`classroom == null`): 1 medium. Empuja al solver a asignar aula a todo evento que no rompa el HARD (aun sobrecupando, que es soft); el medium domina a todo el nivel soft. Usa `forEachIncludingUnassigned` (el `forEach` normal excluiría las entidades sin asignar). Con `@PlanningVariable(allowsUnassigned = true)`, el solver deja `classroom = null` solo en lo inubicable (solape en toda candidata) → esa fila viaja en `unresolved`, ya no oculta como false-feasible. |
| `minimizeOvercrowding` | SOFT | Por allocation no-pinned con `inscriptos > capacidad`: penaliza `excedente × 100.000`. La rama "sin aula ⇒ excedente = total de inscriptos" de `getOvercrowding` es **código muerto en el scoring** (el `forEach` excluye entidades sin aula); ese caso lo cubre ahora `assignAllPossible` en el nivel medium. |
| `minimizeUnusedCapacity` | SOFT | Por allocation no-pinned: penaliza `capacidad − inscriptos` (peso 1) — desempata hacia el aula más ajustada. |
| `preferSameRoomSameCommission` | SOFT | Par no-pinned de la misma comisión (`commissionKey`) en aulas distintas: 2.000. |
| `preferSameBuildingSameCommission` | SOFT | Par no-pinned de la misma comisión en edificios distintos: 4.000 (se suma a la anterior: distinta aula y distinto edificio castiga 6.000). |

Jerarquía de niveles: **HARD** (sin solape) ≫ **MEDIUM** (asignar todo lo posible) ≫
**SOFT**. Dentro del soft, el sobrecupo (100.000/alumno) domina sobre el agrupamiento por
comisión (2.000/4.000) y este sobre el ajuste de capacidad (1/asiento).

### Modelos de planificación (soporte de los servicios)

- **`ClassAllocation`** (`@PlanningEntity`): `@PlanningId` = `planningId` del evento;
  variable de decisión `classroom` con `@ValueRangeProvider` **por entidad** (todas las
  aulas, o solo la fijada si es pinned) y `allowsUnassigned = true` (puede quedar `null`
  si no hay aula sin solape); `@PlanningPin pinned` para ocupación inmovible; helpers de
  score (`getOvercrowding`, `getUnusedCapacity`, `conflictsWith`, `getCommissionKey`,
  `getBuildingId`).
- **`ScheduleSolution`** (`@PlanningSolution`): aulas (facts) + allocations (entities) +
  `HardMediumSoftScore`.

### Configuración

- **`SolverConfiguration`** registra `SolverProperties` y ajusta el `SolverConfig` del
  starter.
- **`SolverProperties`** (`siga.solver.*`): `parallelSolverCount` (default `AUTO`),
  `unimprovedSecondsLimit` (default 10), `environmentMode` (default `PHASE_ASSERT`;
  `FULL_ASSERT`/`TRACKED_FULL_ASSERT` para debuggear score corruption),
  `preview.ttlMinutes` (default 30).

## Dependencias

Solo `common`. Módulo hoja, sin acceso a BD.

## Gaps y oportunidades

- **`CLAUDE.md` referencia `SolverServiceImplTest` que NO existe.** El comando de ejemplo
  (`./mvnw test -Dtest=SolverServiceImplTest`) apunta al vacío. El único test del módulo
  es `CaffeinePreviewStoreTest`; la lógica de `SolverServiceImpl` y las constraints
  siguen sin cobertura.
- **`PreviewStore` in-memory ⇒ single-instance.** Con más de una réplica, un `previewId`
  generado en la instancia A no se recupera desde la B. El TTL acota obsolescencia, no
  resuelve escalado horizontal. Si se despliega multi-instancia, migrar a store compartido
  (Redis/BD).
- **Pesos de restricciones hardcodeados** (`OVERCROWDING_WEIGHT`, etc. como constantes).
  No son configurables por `application.yaml`; ajustar la política de asignación requiere
  recompilar. Candidatos a `SolverProperties`.
- **`getFinalBestSolution()` bloquea el thread del request** durante todo el solve
  (hasta `timeLimitSeconds`). Aceptable con límites cortos; si crecen, considerar solve
  asíncrono con polling.

## Testing

**Estado actual: solo `CaffeinePreviewStoreTest`** (save/get/remove/expiración TTL).

### Unitarios recomendados (alto ROI — módulo puro, sin BD)

- `computeConflicts`: eventos que solapan mismo día ⇒ adyacencia simétrica; distinto día
  ⇒ sin conflicto; mismo evento en varias fechas compartidas no se duplica; determinismo.
- `buildExistingOccupancy`: ocupación cuya aula no es candidata se descarta; duplicados por
  `planningId` se colapsan a uno (y loguean warn).
- `SolverRoom.overcrowding` / `undercrowding` (math de capacidad).
- `ClassAllocation.getOvercrowding` / `getUnusedCapacity` / `conflictsWith` con classroom null.
- **Restricciones vía `ConstraintVerifier`** (API de test de Timefold): cada constraint
  premia/penaliza el escenario esperado de forma aislada.

### Integración recomendados

- `preview` end-to-end con dataset pequeño y `environmentMode = FULL_ASSERT`: score
  reproducible, no hay corruption, respeta `noOverlap` duro y ocupación pinned.
