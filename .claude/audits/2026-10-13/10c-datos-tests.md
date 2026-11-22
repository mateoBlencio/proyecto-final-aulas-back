# Auditoría sysacad: datos, rendimiento y testing

Alcance: módulo `sysacad` y los `sync*` de `space`, `academic`, `events` y `allocation` que llama, commit 22c693f2.
No corrí `./mvnw`. Cobertura y PIT vienen del hilo principal (`herramientas.txt`).

Fuentes de datos:

- Docker estaba apagado, así que no consulté `dev-snapshot`. Las cifras de base salen del MCP `postgres-readonly`, que apunta a `database_dev`, la base de dev. Solo corrí SELECT. La última corrida del sync en esa base es la del cron del domingo 2026-10-11, 03:00 ART.
- Los tiempos salen de `~/Repositories/sysacad-dump/app.log`, la corrida manual del 13/10 sobre una base vacía.
- `.claude/docs/sysacad-evidencias-computos.md` no está en `.claude/docs/`: alguien lo mandó a la papelera (`~/.local/share/Trash/files/`). Lo leí de ahí. Si el hilo principal lo da por insumo vigente, hay que restaurarlo.

## Números de base

| Dato | Valor | Fuente |
|---|---|---|
| Filas de Horarios / cursos distintos / pares curso-materia / pares edificio-aula | 2.838 / 287 / 1.860 / 138 | volcado |
| Asignaciones SYSACAD en dev | 67.729 | base dev |
| `filas_afectadas` de ASIGNACIONES el 11/10 | 67.467 | `sysacad_sync_state` |
| UPDATE reales de asignaciones el 11/10 (filas en `asignacion_aula_aud`, rev 8146) | 15.170, todas por cambio de `observaciones`, 0 por cambio de aula | base dev |
| Eventos recurrentes con `actualizado_en` de la corrida del 11/10 | 3.830 de 3.824 con hash (todos) | base dev |
| Filas `_aud` de eventos en la revisión del 11/10 ("3813 cambios") | 0 | base dev |
| Comisiones reescritas el 11/10 | 321 de 397; 315 de ellas son todas las que tienen más de una materia | base dev |
| `hash_sysacad` nulo: especialidad / aula / evento / edificio / materia / comisión / plan | 9 / 2 / 10 / 0 / 0 / 0 / 0 | base dev |
| Especialidades con nombre `Especialidad N` | 9 (las mismas 9 con hash nulo) | base dev |
| No vigentes (`habilitado_sysacad = false`): aula / evento / comisión / edificio | 156 / 34 / 1 / 0 | base dev |
| Duplicados por clave natural: evento recurrente / comisión (curso, período) | 0 / 0 | base dev |

El edificio de código 1 en dev tiene el nombre de la segunda fila de la vista. Lo comparé por md5 del nombre recortado contra el volcado, sin imprimir el nombre. Coincide con el Caso 1.

## Tiempo de corrida (13/10, base vacía, `POST /v1/sysacad/sync`)

Cada vista termina en el log con `Sync de X finalizado`. El tiempo de una vista es la diferencia con la anterior, e incluye el fetch HTTP perezoso de las vistas que esa vista lee primero.

| Vista | Fin | Duración | Comentario |
|---|---|---|---|
| EDIFICIOS | 14:59:55.112 | 0,34 s | incluye el fetch de Edificios |
| AULAS | 14:59:55.559 | 0,45 s | |
| ESPECIALIDADES | 14:59:55.822 | 0,26 s | |
| MATERIAS | 15:00:48.472 | 52,6 s | unos 51,8 s de espera antes de la primera escritura; los archivos del volcado de Horarios, Materias y Comisiones tienen timestamp 15:00:46-48, así que la espera es HTTP (Plausible) |
| COMISIONES | 15:00:50.477 | 2,0 s | |
| EVENTOS | 15:00:58.477 | 8,0 s | resolver y upsert de 3.800 eventos, más la materialización de ocurrencias |
| ASIGNACIONES | 15:01:27.278 | 28,8 s | 2,6 s de resolución, 7,3 s cargando ocurrencias y asignaciones, 15,2 s de writer (61.946 `nextval`, ver PERF-03 del anexo 02) y 3,7 s de flush y commit |
| FERIADOS | 15:01:27.975 | 0,7 s | |
| Total | | 93 s | |

El cron del 11/10 en dev tardó unos 2,5 minutos (06:00:00 a 06:02:34 en `sysacad_sync_state.ultimo_sync_ok`), con un snapshot por vista. `read-timeout` es 120 s (`application.yaml:186`). Si la espera de 51,8 s de MATERIAS es la de Horarios, el margen frente al timeout es de 2,3 veces. `sysacad_sync_state` no guarda inicio ni duración (ver SYS-DAT-06), así que estos tiempos salen de inferir con el log.

## Casos 1-8 del documento del equipo (mirada de datos)

| Caso | Dónde ocurre hoy | ¿El documento lo describe bien? |
|---|---|---|
| 1 | `BuildingServiceImpl.syncBuildings` (`space/service/impl/BuildingServiceImpl.java:126-156`): sin deduplicar, la segunda fila falla `isUpToDate` (:146) y pisa el nombre (:150) | Sí. Le falta un dato: en régimen estable, cada corrida reescribe el código 1 con la fila 1 y después con la fila 2. EDIFICIOS cuenta 2 afectadas por corrida aunque nada cambió |
| 2 | `ClassroomServiceImpl.syncClassrooms` (`space/service/impl/ClassroomServiceImpl.java:303-306`) | Sí |
| 3 | `SubjectServiceImpl.syncSubjects` (`academic/service/impl/SubjectServiceImpl.java:112-125`) agrupa y loguea | Sí. MATERIAS dio 0 afectadas el 11/10: el hash funciona |
| 4 | `StudyPlanResolver.findOrCreate` (`academic/service/impl/StudyPlanResolver.java:34-44`) | Sí; dev tiene las 9 con hash nulo |
| 5 | `CommissionServiceImpl.linkSubjectCommission` (`academic/service/impl/CommissionServiceImpl.java:217-224`) | Sí. Hay además un problema de hash en la misma clase (SYS-DAT-02) |
| 6 | `AllocationSyncService.doSync` (`sysacad/internal/sync/AllocationSyncService.java:73-104`) | Sí |
| 7 | `AllocationSyncService.recordOverlap`/`warnOverlaps` (:131-147) | Sí (la clave no ve solapes con distinta hora de inicio; ya lo dice el plan) |
| 8 | `ViewSyncRunner.run` (`sysacad/internal/sync/ViewSyncRunner.java:22-32`) y `SysacadSyncStateServiceImpl.recordSuccess` | Sí, aunque se queda corto: los contadores sobrecuentan en EDIFICIOS y también en EVENTOS, COMISIONES y ASIGNACIONES (SYS-PERF-01) |

## Hallazgos de datos

### SYS-DAT-01 Los ocho crons corren al mismo instante, en orden arbitrario y sin la guarda del resync
- Severidad: Alta
- Esfuerzo: S
- Referencia: ISO/IEC 25010:2023, integridad funcional (correctitud); `data-persistence.md` (orden de dependencias FK)
- Evidencia: `application.yaml:190-197` (los 8 en `"0 0 3 * * SUN"`); `SysacadSyncScheduler.java:113-159` (un `orchestrator.sync(view)` por cron, cada uno con `newSnapshot()`); sin `spring.task.scheduling.pool` ni hilos virtuales, así que corre un solo hilo. En dev, `ultimo_sync_ok` del 11/10 muestra este orden de fin: FERIADOS, COMISIONES, MATERIAS, ASIGNACIONES, ESPECIALIDADES, EVENTOS, AULAS, EDIFICIOS
- Verificación: Confirmado (código, configuración y base dev)
- Problema: `resyncAll` respeta el orden de `SysacadView`, pero el cron no. El 11/10, ASIGNACIONES corrió antes que EVENTOS y COMISIONES antes que MATERIAS. Los eventos creados ese domingo no reciben aula hasta el domingo siguiente, y lo mismo pasa con los links de una materia nueva. Cada cron arma su propio snapshot, así que Horarios se descarga tres veces (COMISIONES, EVENTOS, ASIGNACIONES) en vez de una. La guarda `running` de `resyncAll` no cubre los crons: un `POST /sysacad/sync` durante el cron corre en paralelo (ver SYS-DAT-04).
- Arreglo: un único `@Scheduled` que llame a `orchestrator.resyncAll()` y una sola propiedad de cron. Hoy las ocho propiedades tienen el mismo valor, así que nadie pierde configuración.

### SYS-DAT-02 El hash de la comisión incluye la materia: toda comisión con más de una materia se reescribe en cada corrida
- Severidad: Media
- Esfuerzo: S
- Referencia: plan de integridad, regla transversal 4 y E3
- Evidencia: `CommissionServiceImpl.java:158-160` (hash de curso, período, especialidad, plan y **materia**) y :173. La vista trae una fila por par curso-materia, así que las filas del mismo curso alternan el hash. En dev, el 11/10 se reescribieron 315 de 315 comisiones con más de un link y 6 de 82 con uno solo. COMISIONES informó 2.161 afectadas
- Verificación: Confirmado
- Problema: la regla 4 del plan dice que comisiones "ya comparan contra `hash_sysacad`". Para el 80% de las comisiones la comparación nunca da igual, y E3 no lo toca. `Commission` no está auditada (no hay `@Audited`), así que hoy cuesta UPDATE y contador inflado, sin filas `_aud`. Cuando E4 la audite, cada corrida va a dejar cientos de revisiones vacías de sentido.
- Arreglo: el hash de la comisión con solo los campos de la comisión (curso, período; especialidad y plan si se quiere detectar cambio de plan). La materia ya la cubre el link. Sumarlo a E3.

### SYS-DAT-03 Las bajas por ausencia no tienen umbral: EVENTOS con cero filas resueltas da de baja todos los eventos
- Severidad: Media
- Esfuerzo: S
- Referencia: ISO/IEC 25010:2023, tolerancia a fallos; skill de memoria "Sysacad datos sucios"
- Evidencia: `AcademicEventSyncService.java:56` (`requireRows` mira las filas crudas, no los comandos resueltos); `AcademicEventServiceImpl.markRecurringEventsAbsent` (:422-438) marca no vigente todo evento con hash que no esté en `presentEventIds`. Mismo patrón sin umbral en `BuildingServiceImpl.markAbsent` (:161), `ClassroomServiceImpl.markAbsent` (:342) y `CommissionServiceImpl.markAbsent` (:260)
- Verificación: Confirmado el código; Plausible el disparador
- Problema: si COMISIONES falla o deja las comisiones no vigentes, el resolver no resuelve ninguna fila, `commands` queda vacío y EVENTOS da de baja los 3.824 eventos y termina en "éxito". Una vista parcial de Edificios (5 filas de 23, por ejemplo) hace lo mismo con edificios y, en cascada, con aulas y asignaciones. Sysacad ya entrega datos sucios. Esto se revierte en la corrida siguiente, pero el tablero y las asignaciones quedan mal una semana con el cron actual.
- Arreglo: en `ViewSyncRunner`, o antes de cada `markAbsent`, abortar la vista con `recordFailure` si las bajas superan un porcentaje de lo vigente (por ejemplo, 20%), y exigir al menos un comando resuelto en EVENTOS.

### SYS-DAT-04 Claves naturales del sync sin UNIQUE en la base
- Severidad: Media
- Esfuerzo: S
- Referencia: `data-persistence.md` ("unicidades de negocio que solo valida Java")
- Evidencia: `pg_indexes` en dev. `comision` solo tiene PK e índice por período: no hay `UNIQUE (codigo_curso, id_periodo_academico)` ni índice por `codigo_curso`. `evento_recurrente` no tiene unicidad sobre (materia, comisión, día, hora, fecha_inicio, fecha_fin), y `AcademicEventServiceImpl.partitionByKey` ya tolera duplicados (`(first, ignored) -> first`). Hoy hay 0 duplicados de cada uno
- Verificación: Plausible (requiere la carrera de SYS-DAT-01)
- Problema: un cron y un resync manual simultáneos crean la misma comisión o el mismo evento dos veces. Después, `findActiveByCourseCode` ve dos comisiones vigentes y saltea todas las filas de ese curso (:121-125).
- Arreglo: migración con `UNIQUE (codigo_curso, id_periodo_academico)` en `comision` (cubre también la búsqueda del resolver si se ordena `codigo_curso` primero) y, para eventos recurrentes, unicidad sobre la clave del sync. Con SYS-DAT-01 resuelto, alcanza con tomar `running` también en el cron.

### SYS-DAT-05 Una fila sucia tumba la vista ASIGNACIONES completa
- Severidad: Media
- Esfuerzo: S
- Referencia: ISO/IEC 25010:2023, tolerancia a fallos
- Evidencia: `AllocationTargetResolver.java:33-40`: si dos items del lote apuntan a la misma ocurrencia con aulas distintas, lanza `AllocationConflictException`. `AllocationSyncService` arma los items por fila de Horarios (:107) sin agrupar por evento. Dos filas con el mismo curso, materia, día y hora y distinta aula producen ese lote
- Verificación: Plausible. El volcado del 13/10 no lo tiene (la vista terminó bien), pero el Caso 7 ya muestra filas repetidas de curso-materia en la misma franja
- Problema: es distinto de `validateClassroomsAvailable`, que ya trata el plan. Una sola fila contradictoria hace rollback de las 61.946 asignaciones y deja `ultimo_error`. Es la política opuesta a la regla madre del documento.
- Arreglo: en `AllocationSyncService`, quedarse con la primera aula por `eventId` y contar el resto como incidencia (motivo nuevo para E1, por ejemplo `CONFLICTING_ROOM`).

### SYS-DAT-06 `sysacad_sync_state` no guarda inicio, duración ni historial
- Severidad: Baja
- Esfuerzo: S
- Referencia: `ops-ci.md` (observabilidad); plan E1
- Evidencia: `SysacadSyncState.java:29-52`: una fila por vista que se pisa en cada corrida; `ultimo_sync_ok` es el instante de fin
- Verificación: Confirmado
- Problema: E1 suma `incidencias` y alcanza para los contadores. Sin inicio ni duración, medir cuánto tarda cada vista exige leer logs, como hizo este anexo. Sin historial, no se ve si las incidencias crecen entre corridas.
- Arreglo: en la misma migración de E1, `ultimo_inicio` y `duracion_ms`. El historial puede esperar a que alguien lo pida.

## Hallazgos de rendimiento

### SYS-PERF-01 Los contadores no miden escrituras, y EVENTOS reescribe todos los eventos en cada corrida
- Severidad: Media
- Esfuerzo: S
- Referencia: plan de integridad E3 y su criterio de aceptación
- Evidencia: `AcademicEventServiceImpl.java:272` (`setSyncedAt(now)` en todo evento existente, que lo ensucia) y :286 (`updated = !wasCreated`). En dev, 3.830 eventos con `actualizado_en` de la corrida, la revisión 8147 dice "3813 cambios" y tiene 0 filas `_aud` (`syncedAt` es `@NotAudited`). En asignaciones, `AllocationWriter.java:101-104`: Hibernate no emite UPDATE si el aula y la observación no cambian. La rev 8146 tiene 15.170 UPDATE, todos por pasar de "Recuperado de SysAcad" a "Actualizado por sync de SysAcad", sobre las 15.170 que la rev 8108 había creado el 07/10
- Verificación: Confirmado
- Problema: el plan dice que el sync "reescribe las 61.946 asignaciones" en cada corrida. En SQL, cada asignación se reescribe una sola vez, en la corrida que sigue a su alta, y ahí deja una fila `_aud` sin cambio de aula. Lo que sí se reescribe en cada corrida son los 3.824 eventos (UPDATE de `sincronizado_en` y `actualizado_en`) y las comisiones de SYS-DAT-02. E3 corrige el valor de retorno de EVENTOS pero no el UPDATE. El criterio "segunda corrida sin filas nuevas en `revinfo`" se cumple igual: hoy EVENTOS ya no deja `_aud`, pero crea la revisión con descripción.
- Arreglo: sumar a E3 que `syncRecurringEvents` solo toque `syncedAt` si cambió `enrolled`, la duración o `sysacadEnabled`, y que `updated` refleje eso. El cambio de E3 en `AllocationWriter` ya evita el UPDATE único de observación.

### SYS-PERF-02 ASIGNACIONES: una transacción con todo el lote y los locks de aula tomados hasta el commit
- Severidad: Baja
- Esfuerzo: M
- Referencia: `data-persistence.md` (Hibernate, contexto de persistencia)
- Evidencia: `AllocationServiceImpl.syncFromSysacad` (`@Transactional`) carga unas 68.000 ocurrencias como DTO (`findSlotsByEvents`) y las asignaciones existentes como entidades en tramos de 10.000 (`AllocationRepository.java:23-25`). `ClassroomAllocationLock.lock` hace un `pg_advisory_xact_lock` por aula, uno por consulta (unas 300), y los mantiene hasta el commit. En el log del 13/10, el tramo de carga y escritura duró 26 s
- Verificación: Confirmado el código; los tiempos son de una corrida con base vacía
- Problema: con el cron de domingo a las 03:00 nadie lo nota. Un resync manual en horario hábil bloquea durante ese tramo cualquier asignación manual sobre esas aulas. El batching JDBC está activo (`batch_size: 50`, `order_inserts`, `order_updates`); el costo de alta viene de PERF-03 del anexo 02 (`allocationSize = 1`). Después de E3, las corridas sin cambios siguen cargando y revisando con dirty checking las 68.000 entidades.
- Arreglo: barato, comparar en `AllocationSyncService` contra un índice `occurrenceId → classroomId` leído como DTO y mandar al writer solo los items nuevos o cambiados. Con eso los locks quedan acotados a las aulas que cambian.

### SYS-PERF-03 El resolver hace una consulta por curso y una por par curso-materia, dos veces por corrida
- Severidad: Baja
- Esfuerzo: S
- Referencia: `data-persistence.md` (consultas dentro de loops)
- Evidencia: `SysacadCommissionResolver.java:47-69`, con cache por instancia; EVENTOS y ASIGNACIONES crean una cada uno. Con el volcado: 287 cursos y 1.860 pares, unas 4.300 consultas por corrida completa, cada una en su propia transacción de solo lectura. `comision` no tiene índice por `codigo_curso` (SYS-DAT-04)
- Verificación: Confirmado el código; el conteo es estimado desde el volcado
- Problema: no pesa con este volumen (EVENTOS completo tarda 8 s), pero es la única parte del sync que escala por fila contra la base.
- Arreglo: no urgente. Si se toca, un método `findActiveLinksByCourseCodes` que devuelva todo en una consulta, compartido por EVENTOS y ASIGNACIONES dentro del snapshot.

## Hallazgos de testing

Inventario: 18 archivos y 94 métodos de test en `src/test/.../sysacad`. Son 16 unitarios (Mockito o funciones puras, 90 métodos) y 2 de integración (`SysacadSyncApiIntegrationTest` 3, `SysacadSyncDisabledIntegrationTest` 1). Del lado de dominio, el sync se prueba en `AllocationSyncFromSysacadIntegrationTest` (1), `BuildingSysacadSyncIntegrationTest` (3), `EventSyncDeduplicationIntegrationTest` (1) y en los `*ServiceImplTest` de `space` y `academic`.

Casos borde de datos sucios:

| Caso | Cubierto | Dónde |
|---|---|---|
| Código de edificio repetido | No | ningún test de `syncBuildings` manda dos comandos con el mismo código |
| Espacios de relleno | Sí | `SysacadCatalogMapperTest` (recorta edificio, especialidad, curso, materia, feriado) |
| Centinela 999 y 0 | Sí | `AllocationSyncServiceTest.syncSkipsSentinelRoom` (CSV con 999 y 0) |
| Anual (cuatrimestre 0) en 1C y 2C | Sí | `syncMapsBothSemestersToTwoCommands`, `syncResolvesBothSemestersFromIndex` |
| Especialidad ausente (stub) | Sí, y fija el comportamiento que el Caso 4 discute | `StudyPlanResolverTest` |
| Materia repetida idéntica y con variantes | Sí | `SubjectServiceImplTest:253`, `:267`, `:317` |
| Comisión con varias materias (mismo curso, varias filas) | No | ver SYS-TEST-02 |
| Vista vacía | Parcial | EDIFICIOS, AULAS, COMISIONES y EVENTOS sí; MATERIAS, ESPECIALIDADES y ASIGNACIONES terminan en éxito silencioso con 0 y ningún test lo fija |
| Error HTTP | Parcial | `SysacadClientRetryTest` prueba `isRetryable` y `backoffFor`, que son estáticos; el loop de `fetchRows` no tiene test (`SysacadClient` 36%) |
| Timeout o conexión caída | No | `ResourceAccessException` → `SysacadUnavailableException` sin test |
| Orden de vistas | Solo en `resyncAll` | `resyncAll_respectsForeignKeyOrder`; el cron (SYS-DAT-01) no tiene test (scheduler 4%) |

### SYS-TEST-01 El test de la descripción de EVENTOS usa un resultado que producción nunca devuelve
- Severidad: Media
- Esfuerzo: S
- Referencia: `workflow.md` sección 6 (mocks que reemplazan lo que se quiere probar)
- Evidencia: `AcademicEventSyncServiceTest.java:267-268` (`unchanged` = `created=false, updated=false`) usado en :291 y :304. Producción construye siempre `updated = !wasCreated` (`AcademicEventServiceImpl.java:286`), así que `unchanged` no existe. Revisión 8147 en dev: "3813 cambios" con 0 filas `_aud`
- Verificación: Confirmado
- Problema: `syncDescribesIgnoresUnchangedEvents` pasa y certifica que la descripción ignora los eventos sin cambios. En producción la descripción cuenta como cambio a todo evento existente. El mock esconde el contrato roto entre `events` y `sysacad`.
- Arreglo: un test de `AcademicEventServiceImplTest` (o de integración) que sincronice dos veces el mismo comando y verifique `updated=false` en la segunda. Va junto con el arreglo de SYS-PERF-01; hoy ese test falla.

### SYS-TEST-02 Ningún test de idempotencia para comisiones, eventos ni asignaciones
- Severidad: Media
- Esfuerzo: S
- Referencia: skill `testing-pyramid` (integración angosta); plan E3
- Evidencia: `BuildingServiceImplTest` tiene `syncBuildingsIsIdempotentForAbsentBuilding` y `…ForReappearingBuilding`. `CommissionServiceImplTest` (:287-465) no tiene ningún caso con dos comandos del mismo curso ni una segunda corrida igual. E3 propone el de asignaciones y nada más
- Verificación: Confirmado
- Problema: un test "dos corridas iguales, la segunda devuelve 0" habría detectado SYS-DAT-02 y SYS-PERF-01, que hoy cuestan 315 UPDATE de comisiones y 3.824 de eventos por corrida.
- Arreglo: en `CommissionServiceImplTest`, dos comandos del mismo curso con materias distintas, corridos dos veces: la segunda devuelve 0 y no llama a `save`. Lo mismo en `AcademicEventServiceImplTest` con `syncRecurringEvents`.

### SYS-TEST-03 Borde HTTP y código de edificio repetido sin test
- Severidad: Media
- Esfuerzo: M
- Referencia: ISTQB, análisis de valores límite (`testing-pyramid`); `tooling.md`
- Evidencia: JaCoCo `client` 39% líneas y `SysacadClient` 36%; ningún test usa `MockRestServiceServer` ni levanta un servidor HTTP. No hay test de `syncBuildings` con código repetido (tabla de arriba)
- Verificación: Confirmado
- Problema: el loop de reintentos (`fetchRows`), el corte por `maxRetries`, la traducción de `ResourceAccessException` (que decide `CONNECTIVITY_FAILURE`) y el cuerpo nulo convertido en lista vacía no tienen ningún test. Con el cuerpo nulo, MATERIAS termina en éxito con 0 filas. El Caso 1, el único dato sucio que hoy corrompe datos sin avisar, tampoco tiene test.
- Arreglo: un `SysacadClientTest` con `MockRestServiceServer`: 503, 503, 200 → 3 pedidos y filas; 503 cuatro veces → excepción; 404 → sin reintento; timeout → `SysacadUnavailableException`; `{"rows": null}` → lista vacía. Inyectar el `sleep` (o `Duration` base) para no esperar 7 s. Para el Caso 1, el test de E2 en `BuildingSyncServiceTest` ya está planeado.

### SYS-TEST-04 Mutantes sobrevivientes en la guarda de concurrencia y en los bordes del aviso de solape
- Severidad: Baja
- Esfuerzo: S
- Referencia: `workflow.md` sección 6; PIT
- Evidencia: PIT (ver sección de cobertura). `resyncAll_ignoresConcurrentTrigger` prueba que el segundo disparo se ignora, pero no que un tercero, después de terminar el primero, corre: borrar `running.set(false)` sobrevive. `syncWarnsOverlapDetailWithoutDeduplicating` no verifica que una franja con una sola fila no avise (borde `> 1`)
- Verificación: Confirmado (PIT)
- Problema: si alguien rompe la liberación de `running`, el resync manual queda deshabilitado hasta reiniciar la app y ningún test falla.
- Arreglo: al final de `resyncAll_ignoresConcurrentTrigger`, un tercer `resyncAll()` que verifique `times(2)`. En el test de solape, sumar una franja de una fila y verificar que el log tenga un solo WARN.

### SYS-TEST-05 Las pruebas de API del sync no ejercitan el camino feliz del resync
- Severidad: Baja
- Esfuerzo: S
- Referencia: skill `testing-pyramid` (API)
- Evidencia: `SysacadSyncApiIntegrationTest.java:18-37`: el GET con rol válido verifica solo `isArray`; el POST tiene solo el caso 403. Cobertura del controller 25%, `SysacadSyncStateServiceImpl` 39%
- Verificación: Confirmado
- Problema: nadie prueba que el POST devuelva el estado correcto por `SysacadResyncOutcome` ni que el GET serialice lo persistido. E1 cambia ese DTO.
- Arreglo: con `SysacadCatalogSnapshotFactory` como `@MockitoBean` (sin HTTP real), POST como Subsecretaría → status y cuerpo esperados; después, GET → `filas_afectadas` de la vista. El test de E1 sobre el GET entra en el mismo archivo.

## Fixtures sintéticos para E1 y E2

Todos son records `Sysacad*Dto` armados a mano o un `SysacadCatalogReader` falso. No hace falta copiar filas reales.

- **Catálogo mínimo compartido** (un `FakeCatalogReader` en `src/test/.../sysacad/internal/sync`). Edificios: códigos 1 (dos filas con nombres distintos), 2 y 3. Aulas: (2,10), (2,11), (7,1) con edificio 7 no declarado y (2,803) que no existe en Aulas pero sí en Horarios. Especialidades: 5. Materias: (5, 2008, 101) dos veces idéntica y (5, 2008, 102). Comisiones: curso `1K1` con materias 101 y 102, curso `1I1` de especialidad 900 con materia 1 sin Materias. Horarios: una fila sana, una centinela 999, una 0, una en edificio 7, una en aula 803, una de `1I1`, dos en la misma franja (2,10) y una anual (cuatrimestre 0). Con eso cada motivo de la tabla de E1 da 1 (o 2 en la anual), y el mapa esperado se escribe a mano.
- **E1, contadores**: el mismo catálogo con una vista que lanza `RuntimeException` en `findAllocations()` para el test de "vista ajena no rompe la propia".
- **E1, idempotencia del contador**: dos corridas del mismo catálogo; la segunda reemplaza el mapa, no lo suma (`SysacadSyncStateServiceImplTest`).
- **E2, edificios referenciados**: Aulas con códigos 7 y 8 ausentes de Edificios; Horarios con nombre para el 7 y vacío para el 8 (espera `"Edificio 8"`); un código 9 solo en Horarios (no entra). Un edificio 3 presente en Edificios pero `habilitado_sysacad = false` de una corrida anterior, para el rechazo como `UNRESOLVED_CLASSROOM`.
- **SYS-DAT-02 y SYS-PERF-01**: `1K1` con tres materias, corrido dos veces; un evento con mismo `enrolled` y duración, corrido dos veces.
- **SYS-DAT-03**: catálogo con Horarios no vacío y ninguna comisión vigente; esperado: la vista falla y no da de baja eventos.
- **SYS-DAT-05**: dos filas con mismo curso, materia, día y hora y aulas (2,10) y (2,11).

Para los criterios de aceptación sobre la corrida del 13/10 (32 edificios, 354 aulas, `UNRESOLVED_CLASSROOM` 240 → 14) no alcanza un fixture: hace falta `dev-snapshot` con el volcado, como dice el plan.

## Cobertura y mutación

Datos del hilo principal (commit 22c693f2, `herramientas.txt`).

JaCoCo, módulo `sysacad`:

| Paquete o clase | Líneas | Ramas |
|---|---|---|
| Módulo | 81,5% | 72,1% |
| `internal.sync` | 97,2% | 90,5% |
| `internal.mapper` | 94,4% | 78,1% |
| `internal.service.impl` | 77,3% | 72,7% |
| `internal.client` | 39,2% | 45,8% |
| `SysacadClient` | 36% | |
| `SysacadSyncStateServiceImpl` | 39% | |
| `controller` | 25% | |
| `internal.scheduler` | 4% | |

PIT 1.30.0, solo con los tests unitarios del módulo: 221 mutantes, 66% muertos, 58 sin cobertura unitaria, fuerza 89% sobre los cubiertos.

Sobrevivientes con lectura:

- `AllocationSyncService.warnOverlaps:140` (borde `entries.size() > 1`) y `SysacadSyncOrchestratorImpl.resyncAll:76` (`running.set(false)`): SYS-TEST-04.
- `SysacadSyncOrchestratorImpl.resyncAll:69` y `:81` (contadores) y `:50` (retorno null): los tests de resultado comparan el enum final pero no cubren el caso de una vista sin syncer registrado, que es el que usa `attemptedViews`.
- `SysacadCatalogSnapshot.findAcademicEvents`, `findAllocations` y `findSubjectCommissions` devolviendo lista vacía: `SysacadCatalogSnapshotTest` no prueba esos tres métodos con filas. Es la misma brecha que deja pasar una vista vacía sin aviso en ASIGNACIONES.
- `SysacadCatalogMapper.parseDayOfWeek:143` (borde) y `warnIfDurationInconsistent:166/172`: el test de día fuera de rango usa un solo valor, sin 1 ni 7 (o 0 y 8, según la convención); el aviso de duración no se verifica.
- `SysacadClient.backoffFor:71`: `acotaElBackoff` no prueba el valor exacto en el tope de 30 s.
- `CommissionSyncService.higherEnrollment:53`: falta el caso de empate.

Los 58 mutantes sin cobertura unitaria están en cliente, scheduler, controller y estado del sync, los paquetes de SYS-TEST-03 y SYS-TEST-05.

## Conteo

| Severidad | Cantidad | IDs |
|---|---|---|
| Alta | 1 | SYS-DAT-01 |
| Media | 8 | SYS-DAT-02, SYS-DAT-03, SYS-DAT-04, SYS-DAT-05, SYS-PERF-01, SYS-TEST-01, SYS-TEST-02, SYS-TEST-03 |
| Baja | 5 | SYS-DAT-06, SYS-PERF-02, SYS-PERF-03, SYS-TEST-04, SYS-TEST-05 |
