# ADR-007: Auditoría con Hibernate Envers, alcance `allocation`

## Estado

Aceptado — 2026-07-10

## Contexto

SIGA no tenía ningún mecanismo de auditoría: no había forma de responder
"quién asignó esta aula", "cuándo se reasignó" o "qué decía esta ocurrencia
antes de cancelarse". Para un sistema que gestiona asignación de aulas —
donde una reasignación o cancelación puede ser motivo de reclamo— esa
trazabilidad es un requisito de negocio, no solo una conveniencia técnica
(decisión B9 del plan de refactor).

El desacople de módulos hecho en la Fase 3 (`Allocation.classroomId`,
`RecurringEvent.subjectId`/`commissionId` como columnas de ID plano en vez de
relaciones `@ManyToOne` cross-módulo) deja a `allocation` en una posición
particularmente favorable para auditar: **todas** sus referencias a datos de
otros módulos ya son valores escalares (`Integer`/`Long`), no relaciones JPA.
Auditar una entidad con relaciones `@ManyToOne` hacia otro módulo hubiera
requerido decidir, entidad por entidad, si esa relación se audita también
(arrastrando su propia tabla `_aud`) o se excluye con
`@Audited(targetAuditMode = NOT_AUDITED)` — ese problema no existe acá.

El alcance se limita a `allocation` porque es el único módulo donde hay una
necesidad de negocio concreta hoy (historial de asignaciones). `academic` y
`space` son catálogos de datos maestros con su propio ciclo de vida (altas,
soft-delete) pero sin el mismo requisito de "quién cambió qué y cuándo".

## Decisión

Se agrega la dependencia `org.hibernate.orm:hibernate-envers` (sin versión
explícita: la fija el BOM de `spring-boot-starter-parent`) y se marca
`@Audited` sobre las 5 entidades de `allocation/model`:

- `Allocation`
- `Occurrence`
- `AcademicEvent` (raíz de la herencia `JOINED`)
- `RecurringEvent` y `UniqueEvent` (subtipos; quedarían audit­adas por
  herencia de `AcademicEvent` de todos modos, pero se marcan explícitas para
  que el alcance de auditoría sea legible entidad por entidad, sin depender
  de que quien lee el código sepa que Envers propaga `@Audited` a
  subclases).

**Entidad de revisión propia** (`common/audit/SigaRevision.java`, `common`
es el único módulo `OPEN`): reemplaza la `DefaultRevisionEntity` de Envers
—cuyo timestamp es un `long` epoch-millis— por un campo `LocalDateTime`
(`@RevisionTimestamp`, columna `fecha_revision`), legible directamente sin
conversión al inspeccionar la tabla `revinfo`. Hibernate ORM 7 soporta
`LocalDateTime` como tipo de `@RevisionTimestamp` de forma nativa (se
verificó generando el esquema, no se asumió de la documentación).

`RecurringEvent.excludedDates` (`@ElementCollection`, campo obsoleto que
ningún flujo actual escribe — ver `plan-refactor.md`, punto D5) se marca
`@NotAudited`: auditar una colección que nunca cambia solo agrega una tabla
`evento_recurrente_fecha_excluida_aud` vacía sin ningún valor.

DDL manual en `docs/ddl/001_envers_tablas_auditoria.sql` (sin
Flyway/Liquibase, como el resto del esquema de SIGA — ver
`docs/ddl/README.md`): `revinfo` + `asignacion_aula_aud` +
`ocurrencia_aud` + `evento_academico_aud` + `evento_recurrente_aud` +
`evento_unico_academico_aud`. El script se generó levantando la app con
perfil `dev-local` (`ddl-auto: create-drop`) y volcando el esquema real con
`pg_dump`, no a mano — así se confirmó, en vez de asumirse, el detalle no
obvio de que Envers no repite la columna `revtype` en las tablas `_aud` de
las subclases de la herencia `JOINED` (`evento_recurrente_aud` /
`evento_unico_academico_aud`): el tipo de revisión de esa fila ya lo da la
fila correspondiente de `evento_academico_aud`, con la que comparte
`(rev, id_evento_academico)`.

Como `ddl-auto: validate` exige que el esquema ya tenga estas tablas para
que la app arranque, el script queda documentado en `docs/ddl/README.md` en
estado `pendiente` hasta que se aplique en la base de datos compartida.

## Consecuencias

- Cada `INSERT`/`UPDATE`/`DELETE` sobre `Allocation`, `Occurrence`,
  `AcademicEvent` (o sus subtipos) dentro de una transacción que Hibernate ya
  gestiona genera automáticamente una fila en `revinfo` (si es la primera
  entidad auditada tocada en esa transacción) y una fila en la tabla `_aud`
  correspondiente — no requiere código explícito en los servicios de
  `allocation`, es transparente al `EntityManager`.
- **`allocation` no necesita ningún `RevisionListener` para registrar quién
  hizo el cambio** (no hay todavía un campo "usuario" en `SigaRevision`):
  fuera de alcance de esta fase porque SIGA no tiene autenticación
  implementada; cuando la haya, agregar `usuario` a `SigaRevision` vía un
  `RevisionListener` es una extensión aislada a esa entidad, sin tocar las 5
  entidades auditadas.

  > **Actualización (2026-07-13)**: la extensión prevista ya se implementó.
  > `common/audit/SigaRevisionListener.java` completa `SigaRevision.usuario`
  > con el email del `Authentication` del `SecurityContextHolder` (null si la
  > transacción no vino de un request autenticado). DDL en
  > `scripts/sql/ddl.sql`. Las 5 entidades auditadas no se
  > tocaron, como estaba previsto.
- El DDL de las tablas `_aud` queda acoplado al mapeo de las entidades: si
  una columna de `Allocation`/`Occurrence`/`AcademicEvent` cambia (nueva
  columna, tipo distinto), su tabla `_aud` correspondiente necesita el mismo
  cambio a mano en `scripts/sql/ddl.sql` (DDL pendiente para el DBA) — exactamente la
  misma disciplina que ya aplica al resto del esquema de SIGA.
- **No se habilita `modifiedFlags`** (la propiedad global
  `org.hibernate.envers.global_with_modified_flag`, que le agrega a cada
  tabla `_aud` una columna `<campo>_mod` booleana por cada campo auditado,
  indicando si ese campo puntual cambió en esa revisión). Envers ya permite
  reconstruir qué cambió comparando dos revisiones consecutivas de la fila
  vía `AuditReader`, que es el único acceso a auditoría que este alcance
  necesita hoy — el consumidor previsto del `AuditReader` ya existe:

  > **Actualización (2026-07-17)**: `allocation/service/AuditHistoryService`
  > expone el historial por API (`GET /v1/events/{id}/history`,
  > `GET /v1/allocations/occurrences/{id}/history`,
  > `GET /v1/allocations/occurrences/{id}/allocation-history`) leyendo las
  > tablas `_aud` vía `AuditReader`, sin cambio de esquema. El diff campo a
  > campo sigue del lado del consumidor (comparar snapshots consecutivos).

  las columnas `_mod` solo aportan cuando se consulta el
  historial con una librería/UI que las explota directo por SQL, y acá no
  hay ese consumidor. Si aparece, es una propiedad de configuración
  aditiva —no requiere revisar esta decisión— pero sí una migración de
  esquema nueva (agrega columnas a las 5 tablas `_aud`).
- Costo de almacenamiento: cada modificación de una asignación/ocurrencia/
  evento genera una fila nueva en su tabla `_aud`, indefinidamente (no hay
  purga de revisiones antiguas implementada). Aceptable para el volumen de
  SIGA (asignación de aulas de una facultad, no un sistema de alto volumen
  transaccional); si en el futuro se vuelve un problema, es un cambio
  acotado (política de purga por antigüedad) sin impacto en el modelo.

## Alternativas consideradas

- **Auditar también `academic` y/o `space`**: se descartó porque no hay hoy
  un requisito de negocio que lo pida — son catálogos de datos maestros con
  su propio ciclo de vida (altas, soft-delete), y agregar auditoría sin un
  consumidor real es complejidad y tablas `_aud` sin uso. Si aparece la
  necesidad, extender `@Audited` a esos módulos es aditivo: no requiere
  revisar esta decisión, solo repetir el patrón.
- **Tabla de auditoría propia (`log_cambios` con `INSERT`s manuales en cada
  servicio) en vez de Envers**: se descartó porque duplica a mano lo que
  Envers ya resuelve de forma declarativa y consistente (una fila por
  revisión, con `revtype` para distinguir alta/baja/modificación), y porque
  quedaría acoplado a que cada desarrollador se acuerde de escribir el log
  en cada punto de mutación — el mismo problema de fragilidad que
  `@SQLRestriction` resolvió para soft-delete (ver ADR-006).
- **Usar la `DefaultRevisionEntity` de Envers (timestamp `long`) en vez de
  una entidad propia**: se descartó porque un epoch-millis crudo en
  `revinfo` obliga a convertir manualmente para cualquier consulta o
  inspección directa de la tabla — un costo recurrente para ahorrar una
  entidad de 15 líneas.
- **`RelationTargetAuditMode.NOT_AUDITED` en `Occurrence.event` /
  `Allocation.occurrence`**: no aplicó — ambas relaciones apuntan a
  entidades que también están `@Audited` (`Occurrence`→`AcademicEvent`,
  `Allocation`→`Occurrence`), que es el caso por defecto de Envers y no
  requiere ninguna anotación adicional.
