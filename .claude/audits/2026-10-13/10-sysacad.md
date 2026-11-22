# Anexo 10: módulo sysacad (2026-10-14)

Extiende la auditoría del 2026-10-13, que había dejado fuera este módulo. Mismo commit
(`22c693f2`), mismo método (skill `audit-standards`) y como insumo el documento del equipo
`.claude/docs/sysacad-sync-revision.md`. Cuatro agentes en paralelo y las herramientas en el
hilo principal. Detalle por dimensión:

| Anexo | Dimensión | Alta | Media | Baja |
|---|---|---|---|---|
| `10a-correctitud-diseno.md` | Correctitud, diseño, fronteras, mapa del flujo del sync | 2 | 6 | 5 |
| `10b-seguridad.md` | Seguridad (API10:2023, cliente HTTP, integridad) | 2 | 3 | 4 |
| `10c-datos-tests.md` | Datos, rendimiento, tests, tiempos por vista | 1 | 8 | 5 |
| `10d-comentarios.md` | Comentarios | | | 4 bloques a borrar, 3 MUST KILL |

Varios hallazgos se repiten entre anexos; la lista de abajo los deduplica.

## Números de base

| Medición | Resultado |
|---|---|
| Cobertura JaCoCo del módulo | 81.5% líneas, 72.1% ramas. `sync` 97.2/90.5; `SysacadClient` 36%; `SysacadSyncScheduler` 4%; `SysacadSyncStateServiceImpl` 39% |
| PIT, solo tests unitarios del módulo | 221 mutantes, 66% muertos, 58 sin cobertura unitaria, fuerza 89% |
| SpotBugs + FindSecBugs | 18 `CRLF_INJECTION_LOGS`, 1 `IMPROPER_UNICODE`; nada más relevante |
| Fronteras de Modulith | Usa solo tipos `@NamedInterface("api")` y comandos planos; ningún módulo lo importa |
| Duración del sync completo (13/10, base vacía) | 93 s; MATERIAS 52,6 s (casi todo espera HTTP), ASIGNACIONES 28,8 s |

## Hallazgos deduplicados, por prioridad

| # | IDs | Severidad | Esfuerzo | Problema | Arreglo mínimo |
|---|---|---|---|---|---|
| 1 | SYS-QA-01, SYS-DAT-01, SYS-SEC-04 | Alta | S | Los 8 cron (`application.yaml:190-197`) están en `0 0 3 * * SUN` y cada uno sincroniza su vista por separado: el orden de dependencias no está garantizado (en dev, ASIGNACIONES corrió antes que EVENTOS) y no respetan la guarda de corridas simultáneas | Un solo cron que llame a `resyncAll`, con la misma guarda que el POST manual |
| 2 | SYS-SEC-01, SYS-QA-07 | Alta | S | `ViewSyncRunner.requireRows` solo rechaza la lista vacía. Si todas las filas son inválidas, `incoming` queda vacío y `markAbsent` marca como no vigentes todos los edificios, aulas o comisiones | Validar después de filtrar; además, abortar si la vista trae menos de un porcentaje de lo que hay vigente |
| 3 | SYS-SEC-02, SYS-DAT-03, SYS-QA-05 | Alta | S | Si EVENTOS no resuelve ninguna fila, da de baja todos los eventos de Sysacad y termina en éxito; `resyncAll` sigue con EVENTOS y ASIGNACIONES aunque COMISIONES haya fallado. También marca ausentes eventos que Sysacad sí lista cuando la fila se saltea por causas locales | Cortar la cadena si falla una vista de la que dependen las siguientes; mismo umbral que el punto 2; marcar ausente solo lo que Sysacad no listó |
| 4 | SYS-QA-02, SYS-DAT-05 | Alta | S-M | Una fila de Horarios con la misma clave de evento y dos aulas distintas lanza `AllocationConflictException` y revierte toda la vista ASIGNACIONES (transacción única) | Detectar el conflicto en el adaptador, contarlo como incidencia (E1) y saltear solo esa fila |
| 5 | SYS-DAT-02, SYS-QA-03 | Media | S | El hash de comisión incluye la materia pero la clave es (curso, período): las 315 comisiones con más de una materia se reescriben en cada corrida | Hash sobre los campos de la comisión, sin la materia |
| 6 | SYS-PERF-01 | Media | S | EVENTOS reescribe los 3.824 eventos en cada corrida por `setSyncedAt`, y los contadores no miden escrituras reales | Tocar `syncedAt` solo si cambió el hash, o moverlo a `sysacad_sync_state` |
| 7 | SYS-DAT-04 | Media | S | Sin `UNIQUE` en las claves naturales de `comision` (curso, período) y del evento recurrente; un duplicado rompe `Collectors.toMap` en `AllocationSyncService:69` en cada corrida | Índices únicos en una migración nueva, después de verificar que no hay duplicados |
| 8 | SYS-SEC-03 | Media | S | `lastError` guarda `e.getMessage()` crudo (SQL, clases, cuerpo de la respuesta) y lo devuelve por API | Guardar un código de error y un mensaje propio; el detalle va al log |
| 9 | SYS-QA-04 | Media | S | `habilitada_sysacad` del aula mezcla "habilitada = N" con "ausente de la vista", las aulas `N` se reescriben en cada corrida y ninguno de los dos estados afecta la disponibilidad | Depende de la pregunta 2 |
| 10 | SYS-QA-06 | Media | M | Una asignación de origen SYSACAD no se libera nunca, ni cuando la clase pasa al aula centinela ni cuando la fila desaparece | Depende de la pregunta 1 |
| 11 | SYS-QA-08 | Media | S | El link comisión-materia ignora especialidad y plan, y elige el id más bajo (Plausible) | Incluir especialidad y plan en la búsqueda |
| 12 | SYS-SEC-05 | Media | S | Sin tope de tamaño de respuesta; falta probar si `readTimeout` acota la lectura del cuerpo (Plausible) | Límite de bytes en el cliente |
| 13 | SYS-TEST-01 a 05 | Media | S-M | Un test de EVENTOS mockea un resultado que producción nunca devuelve; no hay tests de idempotencia ni del loop HTTP (reintentos, timeout, cuerpo nulo) ni del código de edificio repetido; PIT: sobreviven la guarda de concurrencia y el aviso de solape | Tests con fixtures sintéticos (lista en `10c`) |
| 14 | SYS-QA-09 a 13, SYS-SEC-06 a 09, SYS-DAT-06, SYS-PERF-02/03 | Baja | | Limpieza (7 fetchers idénticos, tres capas Raw/Dto/Command, `ViewQuery` sin uso, mapper a mano), CRLF en logs, `base-url`/`api-key` sin validar, `sysacad_sync_state` sin historial, transacción única con locks de aula | Ver anexos |

## El documento del equipo contra el código

- Las causas de los casos 1 a 8 coinciden con el código.
- Caso 3: omite que una materia con variantes distintas se descarta entera.
- Caso 6: sobreestima el impacto; el evento se crea igual y se pierde solo la asignación.
- Caso 8: el log que muestra el id interno de comisión está en `SysacadCommissionResolver:63-65`.
- No menciona los tres problemas de mayor severidad de este anexo (puntos 1 a 3): el orden de
  los cron y las bajas masivas sin umbral pueden perder más datos que todos sus casos juntos.

## Impacto en los planes

- `integridad-baja-logica-auditoria.md`: la regla 4 ya tiene la corrección de premisa (comisiones
  y eventos se reescriben; las asignaciones no en cada corrida). Los puntos 1 a 4 y las preguntas
  quedaron registrados en su sección "Pendiente: hallazgos de la auditoría de sysacad", a
  decidir al implementar (decisión del usuario, 2026-10-14).
- `arquitectura-mejoras.md`: sin cambios; el módulo respeta las fronteras y no agrega conceptos
  de Sysacad al núcleo.

## Preguntas abiertas

1. Cuando Sysacad pasa una clase al aula centinela (999 o 0) o la fila desaparece, ¿la asignación
   anterior se libera o se conserva? (punto 10)
2. Un aula que Sysacad deja de listar o marca `habilitada = N`, ¿deja de ofrecerse como
   candidata? (punto 9)
3. ¿`HorarioCuatrimestre = 0` significa dictado anual? (SYS-QA-09)
4. ¿Qué umbral de filas válidas justifica abortar una vista (por ejemplo, menos del 50% de lo
   vigente)? (puntos 2 y 3)

