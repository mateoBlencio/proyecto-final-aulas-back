# Revisión de arquitectura (opinión, 2026-10-14)

Complementa `01-arquitectura.md`. Ese anexo lista hallazgos verificables contra Spring
Modulith; este registra una opinión de conjunto sobre qué cambiar mientras el sistema no está
desplegado, cuando un cambio de esquema o de contrato todavía no tiene costo de migración en
producción. Cada punto cita los hallazgos de la auditoría que lo sostienen.

## Lo que se mantiene

- Spring Modulith como base: `verify()` pasa, ninguna entidad JPA cruza módulos, la
  integración entre módulos va por API o por eventos.
- La cantidad de módulos (15) y Timefold para el optimizador.
- `RoomRequestItem` como entidad rica: tiene muchas aristas, pero dentro de su módulo.
- Ocurrencias y asignaciones materializadas por fecha: simplifican la detección de conflictos.
  La condición es que el sync escriba solo lo que cambió (hoy reescribe todos los eventos y
  las comisiones con más de una materia en cada corrida, ver `10-sysacad.md`).
- `SoftDeletableEntity` para la baja lógica. Hibernate 7 trae `@SoftDelete` con estrategia por
  timestamp, pero oculta las filas en todas las consultas, y SIGA necesita listar lo dado de
  baja para reactivarlo.

## Lo que conviene cambiar antes del primer despliegue

| # | Tema | Hallazgos relacionados | Opinión |
|---|---|---|---|
| R1 | Sysacad fuera del núcleo | ARQ-07, MUST KILL #9 de `06a`, plan de integridad (E2, `inferido_sysacad`) | Las tablas núcleo cargan `hash_sysacad`, `habilitado_sysacad`, y `common` define `UnavailableReason.NOT_IN_SYSACAD`. `AcademicEventServiceImpl` mezcla CRUD con sync. Propuesta: una tabla de mapeo propiedad del módulo `sysacad` (tipo de entidad, id interno, código externo, hash, confirmado por Sysacad) y que el núcleo solo conozca un estado neutral de "vigencia externa". Rescata la parte válida de `sysacad_origen` del documento del equipo con el dueño correcto y sin FK desde el núcleo. Es la decisión que más condiciona portar SIGA a otro sistema académico. |
| R2 | APIs de módulo angostas | ARQ-03 | Cada módulo expone su servicio completo, con DTOs de request HTTP, como `@NamedInterface`. Las validaciones de esos DTOs no corren cuando llama otro módulo. Propuesta: un puerto chico por módulo (consultas y comandos que otros usan de verdad), con tipos propios, y los DTOs web solo en el controller. |
| R3 | Interfaz + `Impl` | ARQ (41 de 42 interfaces de servicio con una sola implementación) | Spring no las necesita para proxys ni tests. Borrarlas reduce archivos y saltos de navegación; con R2 quedan solo las interfaces que son API de módulo. |
| R4 | Lógica de sugerencias duplicada | ARQ-06, QA-A-07, MUST KILL #10 de `06a` | `toSolverRoom` aparece tres veces y hay dos `SuggestionStore` idénticos entre `preview` y `roomrequest`. Propuesta: un único componente de sugerencia en `optimizer` que corra el solver fuera de transacción y guarde la sugerencia. `roomrequest` (21 % del código, 9 dependencias) pierde peso. |
| R5 | Puertos en `common` y ciclos encubiertos | ARQ-01, ARQ-05 | `OccurrenceAllocationQuery` en `common` esconde el ciclo `events` ↔ `allocation`; `common` tiene 6 tipos de dominio. Propuesta: cada puerto vive en la API del módulo dueño; `common` queda para infraestructura (`SoftDeletableEntity`, seguridad, utilidades). |
| R6 | Dependencias hacia `audit` | `01-arquitectura.md` (diagrama) | Seis módulos dependen de `audit` sobre todo por la anotación `@AuditOperation`, `AuditLabelProvider` y `AuditedRecord`, y por `RevisionDto` en sus endpoints de historial. Es aceptable, pero el SPI de anotación se puede declarar como `@NamedInterface("spi")` o moverse a `common` para que marcar una operación no implique depender del módulo entero. Prioridad baja. |
| R7 | Lecturas por SQL de tablas ajenas | ARQ-04 | `dashboard` y `audit` leen tablas de otros módulos sin declararlo. Para un modelo de lectura es válido, pero tiene que ser explícito: vistas de base mantenidas por el módulo dueño, o declarar la dependencia en `@ApplicationModule`. |
| R8 | Fiabilidad de eventos | ARQ-02 | Activar `spring.modulith.events.republish-outstanding-events-on-restart` (o un reproceso programado con `IncompleteEventPublications`) y hacer idempotentes los listeners. |
| R9 | Tiempo y zona horaria | OPS-03, QA-B-06, QA-B-07, QA-A-12, DAT (columnas `timestamp` sin zona) | Instantes como `Instant` y `timestamptz` en UTC; horarios de cursada como `LocalDate`/`LocalTime` interpretados en `America/Argentina/Cordoba` por un `Clock` inyectado. Quitar la zona fija de la JVM en `SigaApplication`. |
| R10 | Ids compuestos | Plan de integridad (exclusión de Envers) | `SubjectCommission` tiene id compuesto y `AuditedEntityRegistry` falla con ids compuestos. Un id propio con unicidad sobre el par la vuelve auditable. |
| R11 | Versiones | DEP-01, OPS-06 | Boot al último 4.0.x ahora; Spring Modulith 2.1 antes del primer despliegue. |
| R12 | Tests por módulo | TEST (suite apoyada en integración amplia) | `@ApplicationModuleTest` y la Scenario API de Modulith para probar cada módulo con su contexto, en vez del contexto completo. Acelera la suite y verifica las fronteras en runtime. |

## Orden sugerido

R11 y R8 son de configuración (horas). R9 y R10 tocan esquema y conviene hacerlos antes de que
el plan de integridad agregue columnas y tablas `_aud`. R1 cambia el diseño de E2 del plan de
integridad (`inferido_sysacad` en tablas núcleo): decidirlo antes de implementar E2. R2 a R7
son refactors internos sin cambio de contrato HTTP, por módulo.
