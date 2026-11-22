# 06c Calidad de código, grupo B (academic, audit, auth, common, dashboard, ingest, notification, settings, space)

Base: commit 22c693f2. Solo lectura. Comentarios `ponytail:` encontrados en el grupo: 0 (nada que inventariar con ponytail-debt).
Fronteras de Modulith: los imports cruzados del grupo apuntan a tipos `@NamedInterface("api")` o a `common`; no vi violaciones por lectura (la confirmación formal es ModularityTests del hilo principal).

### QA-B-01 La revocación en cascada de refresh tokens se revierte con la excepción que la sigue
- Severidad: Alta
- Esfuerzo: S
- Referencia: ISO 25010 Reliability/Fault tolerance (correctitud transaccional)
- Evidencia: auth/service/impl/RefreshTokenServiceImpl.java:74 y :111 (revokeAllByUserId seguido de throw InvalidRefreshTokenException en :75 y :115); AuthServiceImpl.java refresh() @Transactional; SigaAppException extends RuntimeException (common/exception/SigaAppException.java:11); no hay noRollbackFor en src/main
- Verificación: Confirmado
- Problema: `refresh()` y `handleReuse()` ejecutan el UPDATE de revocación en cascada (reuso fuera de la ventana de gracia, usuario deshabilitado) y después lanzan una RuntimeException dentro de la misma transacción `@Transactional`. Spring hace rollback y el UPDATE no persiste: la detección de reuso responde 401 pero no revoca la familia de tokens.
- Arreglo: mover la revocación a una transacción propia (`REQUIRES_NEW` en un bean aparte) o lanzar con `noRollbackFor = InvalidRefreshTokenException.class` en `refresh()` de RefreshTokenService y AuthService. Agregar un IntegrationTest del reuso (no hay cobertura: el grep de tests no muestra ninguno sobre CASCADE de refresh). El impacto de seguridad lo evalúa security-reviewer.

### QA-B-02 `materializeCurrentYear` es código muerto y su catch no funciona
- Severidad: Media
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Modifiability + ponytail: delete
- Evidencia: academic/service/impl/AcademicPeriodServiceImpl.java:100-112; academic/service/AcademicPeriodService.java:43; `grep -rn materializeCurrentYear src` solo devuelve interfaz e implementación
- Verificación: Confirmado (sin llamadores); Plausible (catch)
- Problema: nadie invoca el método. Además, si algún día se usa, el `catch (DataIntegrityViolationException)` está dentro del mismo `@Transactional`: el save() que falló marca la transacción rollback-only y el commit lanza UnexpectedRollbackException; el comentario "tolera la carrera" no se cumple.
- Arreglo: borrar el método y su doc en la interfaz (y `rolloverFrom` si queda sin uso). Si se quiere conservar, ejecutar cada inserción en `REQUIRES_NEW`.

### QA-B-03 Ingest no es atómico y el cache puede quedar con entidades de una transacción revertida
- Severidad: Media
- Esfuerzo: M
- Referencia: ISO 25010 Reliability/Maturity
- Evidencia: ingest/service/impl/IngestServiceImpl.java:37-47 (sin @Transactional); IngestRowResolver.java:~62 (`@Transactional resolveRefs` por fila); IngestRowBatchProcessor.java:41-60; IngestCache.java
- Verificación: Plausible
- Problema: `resolveRefs` abre una transacción por fila. Si la fila crea un período (`findOrCreate`) y luego lanza ResourceNotFoundException (comisión inexistente), el período se revierte pero ya quedó en `IngestCache` y en `periodsCreated`: el contador informa períodos que no existen y las filas siguientes no lo recrean. Además, una InvalidRowException en una fila posterior (el controller documenta "aborta el import completo") deja períodos de filas anteriores ya confirmados, y los eventos se crean (`findOrCreateRecurringEvents`) en una transacción distinta de `reallocate`: un fallo del segundo deja eventos sin aula.
- Arreglo: pre-validar todas las filas (TermType, catálogo) antes de escribir, o envolver `ingestFile` en una transacción única. Mínimo: no incrementar `periodsCreated` ni cachear dentro de la transacción de fila hasta el commit. Confirmar con business-rules si el import parcial es deliberado.

### QA-B-04 SettingsStore actualiza el cache después de publicar el evento de cambio
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Reliability/Maturity
- Evidencia: settings/service/SettingsStore.java:54-71; preview/service/impl/CaffeinePreviewStore.java:62 y roomrequest/service/impl/DirectEventSuggestionStore.java:35 (`@ApplicationModuleListener`, asíncronos tras commit)
- Verificación: Plausible
- Problema: `write()` publica `SettingChangedEvent` y recién después registra la sincronización `afterCommit` que hace `cache.put`. Los listeners asíncronos corren tras el commit y pueden leer `settingsReader.getLong(PREVIEW_TTL_MINUTES)` antes del `put`, quedando con el TTL viejo hasta la próxima escritura. La ventana es de milisegundos.
- Arreglo: registrar el `cache.put` antes de `publishEvent` (la sincronización se ejecuta en orden de registro) o pasar el valor nuevo en el evento.

### QA-B-05 Envío SMTP dentro de una transacción REQUIRES_NEW
- Severidad: Baja
- Esfuerzo: M
- Referencia: ISO 25010 Performance efficiency/Resource utilization
- Evidencia: notification/internal/service/NotificationDispatcher.java:31-57
- Verificación: Confirmado
- Problema: `dispatch` abre una transacción nueva y envía el mail dentro. Una conexión de pool queda retenida durante el timeout SMTP por notificación. El diseño (claim con lock de 5 min, backoff, idempotencia, envío fuera de la transacción del negocio vía AFTER_COMMIT + @Async) es correcto; solo falta acotar la retención.
- Arreglo: aceptar como deuda; si el pool se agota, separar en claim -> send sin transacción -> markSent/registerFailure en transacción corta. Verificar que el executor de @Async tenga tamaño acotado (hoy se usa el default de `@EnableAsync` en NotificationConfiguration.java).

### QA-B-06 `LocalDate.now()` sin Clock en lógica de fechas
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Testability
- Evidencia: academic/service/impl/AcademicPeriodServiceImpl.java:87,103; academic/validator/AcademicPeriodUpdateValidator.java:18; common/util/DateRanges.java:13; audit/internal/AuditArchiveScheduler.java:23; audit/service/impl/AuditArchiveServiceImpl.java:81; audit/controller/AuditRegistryController.java:118
- Verificación: Confirmado
- Problema: el bean `Clock` (common/config/ClockConfig.java) existe y dashboard lo usa, pero estos sitios llaman a `LocalDate.now()`/`LocalDateTime.now()` directo. No se pueden fijar en tests y dependen del `TimeZone.setDefault` de SigaApplication.main. Nota: ese default es America/Argentina/Buenos_Aires, no UTC como dice la guía de estilo del repo; confirmar cuál es la regla vigente (las columnas `fecha_revision` son timestamp sin zona).
- Arreglo: inyectar `Clock` en los 4 sitios con lógica (AcademicPeriodServiceImpl, AcademicPeriodUpdateValidator, AuditArchiveScheduler, AuditArchiveServiceImpl). Es acotado.

### QA-B-07 Zona horaria duplicada como literal en el scheduler del tablero
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Modifiability + ponytail: shrink
- Evidencia: dashboard/scheduler/DashboardSnapshotScheduler.java:21; SigaApplication.java:14
- Verificación: Confirmado
- Problema: el cron fija `zone = "America/Argentina/Buenos_Aires"` y el main fija la misma zona por default de JVM; si se cambia una, la otra queda desalineada (el cron de AuditArchiveScheduler no fija zona y usa el default).
- Arreglo: quitar `zone` del `@Scheduled` (o parametrizar ambos con una propiedad).

### QA-B-08 Campos de entidad en español en SigaRevision
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Analysability (convención código en inglés)
- Evidencia: audit/model/SigaRevision.java:39,42,45,48 (`fechaRevision`, `usuario`, `descripcion`, `operacionId`); usos: audit/service/impl/RevisionReaderImpl.java:74-75
- Verificación: Confirmado
- Problema: los campos Java están en español y conviven con `parentOperationId` y `actorType` en inglés. Las columnas (`fecha_revision`, etc.) y los nombres de `ArchivedTable`/SQL nativo son esquema, no se tocan.
- Arreglo: renombrar los 4 campos (`revisionDate`, `username`, `description`, `operationId`), mantener `@Column(name=...)`. Revisar HQL/Envers que referencie los nombres (grep no encontró otros usos en main).

### QA-B-09 Nombre completo inline y comportamiento de extensión en ingest
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Analysability
- Evidencia: ingest/service/impl/IngestServiceImpl.java:43 (`ar.edu.utn.frc.siga.common.security.SystemScope.run` con FQN); ingest/source/impl/ExcelIngestSource.java (`supports` con `endsWith(".xls")` sensible a mayúsculas)
- Verificación: Confirmado
- Problema: un `Datos.XLSX` se rechaza como formato no soportado; el FQN inline rompe la convención de imports.
- Arreglo: `filename.toLowerCase(Locale.ROOT)` en `supports` e import de SystemScope.

### QA-B-10 IngestCache indexa por nombre simple de clase y `resolveRefs` cachea llamadas solo por efecto lateral
- Severidad: Baja
- Esfuerzo: S
- Referencia: ISO 25010 Maintainability/Modularity + ponytail: shrink
- Evidencia: ingest/service/impl/IngestCache.java:14; IngestRowResolver.java (resolveAcademicRefs: Specialty, StudyPlan y SubjectCommission se cachean sin usar el resultado)
- Verificación: Confirmado
- Problema: tres lookups cuyo valor se descarta existen solo para lanzar ResourceNotFoundException; la clave `getSimpleName()+":"+key` es frágil y el cache de `Map<String,Object>` obliga a un cast sin chequeo.
- Arreglo: aceptar como deuda; si se toca, usar un `Map` tipado por clave compuesta o `record` de claves.

## Resumen

- Hallazgo principal: QA-B-01 (Alta). El rollback de la transacción de refresh deshace la revocación en cascada por reuso de token; confirmado en código, sin test que lo cubra.
- Código muerto: `materializeCurrentYear` sin llamadores, con un catch que además no funcionaría (QA-B-02).
- Ingest no es atómico y el cache puede contener datos de una transacción revertida (QA-B-03, Plausible); definir con negocio si el import parcial es intencional.
- Notification, audit archive y dashboard snapshot están bien resueltos transaccionalmente (claim + AFTER_COMMIT, TransactionTemplate por paso, advisory locks); solo observaciones menores (QA-B-05).
- Fechas: Clock disponible pero usado solo en dashboard (QA-B-06); la zona por defecto es Buenos Aires, no UTC (pregunta abajo).
- Sin `hasRole(...)` en el grupo (solo `UserService.hasRole(email, role)`, método de consulta propio); sin "assignation"; identificadores en español solo en SigaRevision (QA-B-08).
- Sin comentarios `ponytail:`; no encontré sobre-ingeniería relevante en common/util (todas las utilidades tienen usos).
- No audité a fondo academic (sync de comisiones/materias) ni las capas de mapper/specification de space; lectura parcial por tiempo.

Conteo por severidad: Crítica 0, Alta 1, Media 2, Baja 7.

## Preguntas para el usuario

1. Zona horaria: SigaApplication fija America/Argentina/Buenos_Aires como default de JVM, pero la guía de estilo dice UTC. Si la regla vigente es UTC, QA-B-06/QA-B-07 suben a Media y hay que revisar las columnas timestamp sin zona; si es Buenos Aires, hay que corregir la guía.
2. Importación (QA-B-03): ¿es deliberado que un import con error a mitad de archivo deje períodos y eventos ya confirmados? Si es deliberado, el hallazgo baja a Baja y solo queda el contador `periodsCreated`.
