# 06a Comentarios (solo reporte, sin cambios en el repo)

Alcance: `src/main/java` sin `sysacad` (147 archivos con comentarios, 308 bloques, 821 líneas), `src/main/resources/application.yaml` (0 comentarios) y migraciones Flyway (solo listadas).

Criterio: se conservan licencias, restricciones de vendor/plataforma que no controlamos (Postgres, Hibernate/Envers, Spring, Excel, RFC 4180, Sysacad), Javadoc de contrato de API pública (interfaces de servicio de cada módulo, tipos `@NamedInterface("api")`, DTOs de respuesta REST y `common`). Ante la duda, borrar. Javadoc de impl, repositorios, modelos internos, métodos privados, notas de historia ("antes reventaba") y referencias a `.claude/docs` o "plan §4" se borran. Lo conservado en español se traduce al inglés.

Columna "Borrar": rangos de líneas. Columnas de conservar/traducir: rango (excepción).

| Archivo (bajo src/main/java/ar/edu/utn/frc/siga/) | Borrar | Conservar (excepción) | Traducir a inglés y conservar (excepción) |
|---|---|---|---|
| academic/repository/AcademicPeriodRepository.java | 17 | - | - |
| academic/repository/SubjectCommissionRepository.java | - | - | 27-30 (vendor: datos sucios de Sysacad (comisión ligada a dos materias), quitar "TEMPORAL" y la narración) |
| academic/service/AcademicPeriodService.java | - | - | 25 (contrato API pública); 28 (contrato API pública); 31-35 (contrato API pública); 38-42 (contrato API pública) |
| academic/service/CommissionService.java | - | - | 21-24 (contrato API pública); 31-37 (contrato API pública (quitar ref a supuestos-sync-horarios.md)); 40-44 (contrato API pública) |
| academic/service/HolidayService.java | - | - | 12 (contrato API pública); 15-19 (contrato API pública) |
| academic/service/impl/AcademicPeriodServiceImpl.java | 94-99 | - | - |
| academic/service/impl/CommissionServiceImpl.java | 242-243 | - | - |
| academic/service/impl/SpecialtyServiceImpl.java | 54-55 | - | - |
| academic/service/impl/SubjectServiceImpl.java | 153, 165-167 | - | - |
| academic/service/SpecialtyService.java | - | - | 22-25 (contrato API pública) |
| academic/service/SubjectCommissionService.java | - | - | 17-23 (contrato API pública (404 si falta), quitar narración del mock) |
| academic/service/SubjectService.java | - | - | 23-26 (contrato API pública); 31-34 (contrato API pública) |
| academic/util/WeekAlignedDates.java | - | - | 11-15 (contrato de método público (caso 5.ª semana)) |
| allocation/dto/request/AllocationItemRequestDto.java | 25 | - | - |
| allocation/mapper/AllocationCommandMapper.java | 39-40 | - | - |
| allocation/repository/AllocationRepository.java | 17-20 | - | - |
| allocation/service/AllocationService.java | - | - | 37-50 (contrato API pública (quitar refs a .claude/docs)) |
| allocation/service/command/AllocationTarget.java | 17-18 | - | - |
| allocation/service/impl/AllocationAuditLabelProvider.java | 22-27 | - | - |
| allocation/service/impl/AllocationImpactServiceImpl.java | 122, 199-200 | - | - |
| allocation/service/impl/AllocationServiceImpl.java | 147, 154, 156-157 | - | - |
| allocation/service/impl/AllocationTargetResolver.java | 85-86 | - | - |
| allocation/service/impl/AllocationWriter.java | 73-79 | - | - |
| allocation/service/impl/ClassroomAllocationLock.java | 22 | - | 18-19 (plataforma: pg_advisory_xact_lock vive hasta el fin de la transacción) |
| audit/AuditCause.java | - | - | 5-12 (contrato API (@NamedInterface api), Modulith reintenta en otro hilo); 16 (contrato API (@NamedInterface api)) |
| audit/AuditedRecord.java | - | 7-10 (contrato API (@NamedInterface api)); 14 (contrato API (@NamedInterface api)); 23 (contrato API (@NamedInterface api)); 32 (contrato API (@NamedInterface api)) | - |
| audit/AuditLabelProvider.java | - | 8-15 (contrato API (@NamedInterface api)); 19 (contrato API (@NamedInterface api)); 22 (contrato API (@NamedInterface api)) | - |
| audit/AuditOperation.java | - | - | 10-17 (contrato API (anotación pública)) |
| audit/AuditOperations.java | - | 18-21 (contrato API (@NamedInterface api)) | 6 (contrato API (@NamedInterface api)); 13 (contrato API (@NamedInterface api)) |
| audit/controller/AuditRegistryController.java | 126 | - | - |
| audit/dto/response/AuditLogEntryDto.java | - | - | 11-35 (contrato REST, líneas 12-18 en español) |
| audit/dto/response/AuditLogEntryType.java | - | 11-14 (contrato REST) | 5 (contrato REST); 8 (contrato REST) |
| audit/dto/response/FieldChangeDto.java | - | 3-7 (contrato REST) | - |
| audit/internal/AuditOperationAspect.java | 14-20, 38-39, 46-47 | - | - |
| audit/internal/AuditOperationContext.java | 5-14, 33-36, 54-58, 70 | 78 (plataforma: pares sustitutos UTF-16) | - |
| audit/internal/RevisionDescriptionUpdater.java | 10-13, 24, 28 | - | - |
| audit/internal/SigaRevisionListener.java | 17-22, 37-38, 58 | - | - |
| audit/mapper/AuditLogEntryMapper.java | 41 | - | - |
| audit/model/ActorType.java | - | 3 (contrato REST (enum expuesto en actorType)) | - |
| audit/model/AuditArchiveRun.java | 20-23 | - | - |
| audit/model/RevisionSummaryKey.java | 6 | - | - |
| audit/model/SigaRevision.java | 57-61 | - | - |
| audit/repository/ArchivedTable.java | 3-6 | - | - |
| audit/repository/AuditArchiveRepository.java | 19-24, 31-35, 49, 104, 110-113, 120, 126-130, 148, 151, 154 | 142 (vendor: Postgres no acepta parámetros en CREATE TABLE AS) | - |
| audit/repository/AuditChainRows.java | 5 | - | - |
| audit/repository/AuditGroupRow.java | 9-13 | - | - |
| audit/repository/AuditLogCriteria.java | 10 | - | - |
| audit/repository/AuditLogQueryRepository.java | 26-30, 35, 52, 76-85, 126-129, 196-199, 257-261, 269, 298 | - | - |
| audit/repository/AuditRecordStateRepository.java | 21-26, 33, 103-106 | - | - |
| audit/repository/ChangeScope.java | 6-10, 31 | - | - |
| audit/service/AuditArchiveService.java | 9-12 | - | - |
| audit/service/AuditedEntity.java | 6-10, 20, 37 | - | - |
| audit/service/AuditedEntityRegistry.java | 44, 82, 110 | 131 (vendor: naming de Envers para to-one (<prop>_id)) | - |
| audit/service/AuditRegistryService.java | 15-19, 22-26, 29, 32, 35-39, 42-45, 48 | - | - |
| audit/service/impl/ArchiveCutoffResolver.java | 15-20, 25, 33, 45-48, 56 | - | - |
| audit/service/impl/AuditArchiveServiceImpl.java | 27-30, 72, 83, 115 | - | - |
| audit/service/impl/AuditCsvWriter.java | 36, 74 | 13-16 (RFC 4180 + Excel necesita BOM para UTF-8) | - |
| audit/service/impl/AuditLabelResolver.java | 45, 50-53 | 24-29 (vendor: Spring marca rollback-only al cruzar un proxy transaccional); 84 (vendor: Envers store_data_at_delete) | - |
| audit/service/impl/AuditRegistryServiceImpl.java | 67-69, 78-86, 119, 164, 185 | - | - |
| audit/service/impl/FieldDiffCalculator.java | 14, 20-24 | 43 (plataforma: BigDecimal.equals compara escala) | - |
| audit/service/RevisionReader.java | - | - | 10-13 (contrato API (@NamedInterface api)) |
| auth/repository/UserRepository.java | 25-28, 77 | - | - |
| auth/security/SecurityUser.java | 65-66 | - | - |
| auth/service/impl/RoleAssignmentAuditLabelProvider.java | 18 | - | - |
| auth/service/impl/UserAuditLabelProvider.java | 12 | - | - |
| auth/service/UserService.java | - | - | 26 (contrato API pública); 29 (contrato API pública); 32 (contrato API pública); 35 (contrato API pública) |
| common/config/ClockConfig.java | 7 | - | - |
| common/config/SchedulingConfig.java | 6 | - | - |
| common/security/BuildingScope.java | - | - | 40 (contrato API pública (common)); 49 (contrato API pública (common)) |
| common/security/SystemScope.java | - | - | 5-10 (contrato API pública (common), quitar "a propósito") |
| common/service/OccurrenceAllocationQuery.java | - | - | 6-11 (contrato de puerto cross-módulo) |
| common/util/Chunks.java | - | 14-20 (vendor: límite de 65.535 parámetros de PostgreSQL) | - |
| common/util/Plurals.java | 6, 10 | - | - |
| dashboard/dto/response/RoomRequestCountsResponseDto.java | - | 3 (contrato REST (pending null)) | - |
| dashboard/model/AllocationCategory.java | 3 | - | - |
| dashboard/model/AllocationUnitRow.java | 7 | - | - |
| dashboard/model/Availability.java | 7 | - | - |
| dashboard/model/DashboardParameters.java | 17, 22 | - | - |
| dashboard/model/DashboardPeriodType.java | 3 | - | - |
| dashboard/model/DashboardRules.java | 9, 12, 15, 21 | - | - |
| dashboard/model/DashboardSnapshot.java | 18-21 | - | - |
| dashboard/model/DashboardSnapshotRegeneration.java | 15-18 | - | - |
| dashboard/model/DateWindow.java | 5 | - | - |
| dashboard/model/PendingItemRow.java | 7 | - | - |
| dashboard/model/PeriodMetrics.java | 6-9 | - | - |
| dashboard/model/RequestCountRow.java | 3-6 | - | - |
| dashboard/model/ResolvedPeriod.java | 6-12 | - | - |
| dashboard/model/ScopeMetrics.java | 6-9, 20 | - | - |
| dashboard/model/SnapshotPayload.java | 8-11, 35 | - | - |
| dashboard/model/WindowRoomRequests.java | 6 | - | - |
| dashboard/repository/DashboardReadRepository.java | 25-28, 71, 99-103, 123, 137-141, 184 | - | - |
| dashboard/repository/DashboardSnapshotRepository.java | 23 | - | - |
| dashboard/scheduler/DashboardSnapshotScheduler.java | 12 | - | - |
| dashboard/service/DashboardQueryService.java | 15, 18, 21, 24, 27, 30-33 | - | - |
| dashboard/service/DashboardSnapshotService.java | 8-11, 14, 17-20 | - | - |
| dashboard/service/impl/AvailabilityCalendar.java | 21 | - | - |
| dashboard/service/impl/DashboardCalculator.java | 23, 29, 63, 123, 145 | - | - |
| dashboard/service/impl/DashboardDateLimits.java | 8 | - | - |
| dashboard/service/impl/DashboardDtoMapper.java | 21, 81 | - | - |
| dashboard/service/impl/DashboardMetricsService.java | 19 | - | - |
| dashboard/service/impl/DashboardPeriodResolver.java | 19-22, 38, 67, 121 | - | - |
| dashboard/service/impl/DashboardQueryServiceImpl.java | 128-131, 157, 203-207, 221, 239, 266, 283 | - | - |
| dashboard/service/impl/DashboardRoomRequestCounter.java | 17, 49-52, 76 | - | - |
| dashboard/service/impl/DashboardRoomRequestStats.java | 23, 100 | - | - |
| dashboard/service/impl/DashboardSnapshotReader.java | 32, 42, 46, 132 | - | - |
| dashboard/service/impl/DashboardSnapshotServiceImpl.java | 98 | - | - |
| events/model/OccurrenceWindow.java | 5-10 | - | - |
| events/repository/OccurrenceRepository.java | 23 | - | - |
| events/repository/RecurringEventRepository.java | 23-28, 32-37, 59-63 | - | - |
| events/service/AcademicEventService.java | - | - | 45-52 (contrato API pública); 55-63 (contrato API pública (quitar ref a .claude/docs)); 66-69 (contrato API pública); 72-76 (contrato API pública); 79-86 (contrato API pública (quitar ref a .claude/docs)) |
| events/service/impl/AcademicEventAuditLabelProvider.java | 13 | - | - |
| events/service/impl/AcademicEventServiceImpl.java | 227-232, 310-315, 391-398, 524, 542-543 | - | - |
| events/service/impl/AcademicPeriodChangedListener.java | 21-25 | - | - |
| events/service/impl/EventLabels.java | 16, 24, 28-31 | - | - |
| events/service/impl/OccurrenceAuditLabelProvider.java | 21 | - | - |
| events/service/impl/RecurringEventReconciler.java | 19-23 | - | - |
| events/service/OccurrenceService.java | - | - | 20 (contrato API pública); 25-29 (contrato API pública) |
| optimizer/constraint/PermissionNotSatisfied.java | 29-33 | - | - |
| preview/dto/request/ReallocationSuggestionRequestDto.java | 27-28 | - | - |
| preview/mapper/PreviewComposer.java | 117-119 | - | - |
| preview/service/impl/CaffeinePreviewStore.java | 57 | - | - |
| preview/service/impl/PreviewEngine.java | 106 | - | - |
| roomrequest/dto/response/CommissionScheduleDto.java | - | - | 6-9 (contrato REST) |
| roomrequest/dto/RoomRequestItemFilter.java | 38 | - | - |
| roomrequest/mapper/RoomRequestComposer.java | 35, 77, 235 | - | - |
| roomrequest/mapper/RoomRequestNotificationModel.java | 21-23 | - | - |
| roomrequest/model/RoomRequestItemAllocation.java | 50 | - | - |
| roomrequest/model/RoomRequestItem.java | 85, 89, 104, 153-154, 158, 173-174, 193, 203, 224 | - | 185 (vendor: Postgres guarda timestamp con microsegundos); 225 (vendor: orden de flush de Hibernate viola UNIQUE al borrar y recrear) |
| roomrequest/repository/RoomRequestItemAllocationRepository.java | 17 | - | - |
| roomrequest/service/impl/DirectEventServiceImpl.java | 76, 189, 197-198 | - | - |
| roomrequest/service/impl/DirectEventSuggestionStore.java | 49 | - | - |
| roomrequest/service/impl/RoomRequestAuditLabelProvider.java | 12 | - | - |
| roomrequest/service/impl/RoomRequestSuggestionInputLoader.java | 116 | - | - |
| roomrequest/service/impl/RoomRequestSuggestionServiceImpl.java | 46, 112 | - | - |
| roomrequest/service/impl/RoomRequestSuggestionStore.java | 49 | - | - |
| roomrequest/service/RoomRequestCatalogService.java | 13, 22 | - | - |
| roomrequest/specification/RoomRequestItemSort.java | - | - | 46-47 (vendor: Postgres ordena NULLS al final en ASC) |
| roomrequest/specification/RoomRequestItemSpecification.java | 31-32, 42-43 | - | 44-45 (vendor: lógica trivalente de NULL en SQL (coalesce)) |
| roomrequest/validator/ItemConsistency.java | 97 | - | - |
| roomrequest/validator/RoomRequestAccessControl.java | 67 | - | - |
| roomrequest/validator/RoomRequestDeriveEligibilityValidator.java | 7-11 | - | - |
| space/service/BuildingService.java | - | - | 25 (contrato API pública); 32-35 (contrato API pública) |
| space/service/ClassroomService.java | - | - | 34-37 (contrato API pública); 40-44 (contrato API pública); 47-52 (contrato API pública); 67-71 (contrato API pública) |
| space/service/impl/BuildingServiceImpl.java | 149 | - | - |
| space/service/impl/ClassroomServiceImpl.java | 234 | - | - |
| space/service/impl/ClassroomTypeServiceImpl.java | 91-93 | - | - |
| space/service/impl/ScopedClassroomFinder.java | 18-23 | - | - |

Nota: `RoomRequestItem.java:224-225` son dos Javadoc apilados sobre `assignClassrooms`; el primero (224) queda colgado y se borra. `RoomRequestItemSpecification.java:42-45`: 42-43 (espejo de la regla) se borra, 44-45 (NULL en SQL) se conserva.

## Fuera de código Java

- `src/main/resources/application.yaml`: sin comentarios.
- `src/main/resources/application-dev-snapshot.yaml:12-15` (fuera del alcance pedido, informativo): español, explica una fila huérfana de `flyway_schema_history` en dev. Restricción de entorno externo; si se conserva, traducir y quitar "ver CLAUDE.md".
- Migraciones: no tocar, migración aplicada. Todas siguen en español; no se traducen.

| Migración | Líneas de comentario |
|---|---|
| V1__baseline_schema.sql | 1-11, 15-17 y ~230 banners de pg_dump de 3 líneas (47-49 ... 2380-2382), 710 líneas |
| V2__ajusta_increment_secuencia_evento_academico.sql | 1-6 |
| V3__rbac_alcance_por_edificio.sql | 1-6, 8, 12-13, 23, 41-42, 46, 55-56 + 3 comentarios al final de línea |
| V4__auditoria_revinfo_operacion.sql | 1-5 |
| V5__periodo_academico_receso.sql | 1-5 |
| V7__resolucion_solicitudes.sql | 1-2 |
| V8__renombra_estados_solicitud_pendiente_preaprobado.sql | 1-7 |
| V9__version_optimista_solicitud_item.sql | 1-5 |
| V10__aula_anterior_solicitud_item.sql | 1-3 |
| V11__notificacion.sql | sin comentarios |
| V12__indices_lectura_auditoria.sql | 1-4 |
| V13__solicitud_item_fechas_con_zona.sql | 1-2 |
| V14__tipo_actor_revinfo.sql | 1-5 |
| V15__indices_id_rev_auditoria.sql | 1-4 |
| V16__operacion_padre_revinfo.sql | 1-2 |
| V17__resumen_revision_auditoria.sql | 1-4 |
| V18__funciones_correccion_auditada.sql | 1-25, 29-33, 55, 75-79, 122, 148, 155, 173, 194 + 2 al final de línea |
| V19__archivo_auditoria.sql | 1-9, 17, 46-50, 66 |
| V20__feriado.sql | 1 |
| V21__tablero_snapshot.sql | 1-3 |
| V22__regeneracion_tablero.sql | 1 |
| V24__evento_unico_requisitos.sql | 1, 6, 10, 17 |
| V25__edificio_habilitado_sysacad.sql | 1 |

## MUST KILL

Rutas bajo `src/main/java/ar/edu/utn/frc/siga/`.

1. `audit/service/impl/AuditArchiveServiceImpl#archive` `long[] totals` (l. 83): índices mágicos 0/1/2 explicados por comentario al final de línea; type: record `ArchiveTotals(occurrenceRows, allocationRows, deletedRevisions)`.
2. `allocation/repository/AllocationRepository#findChunkByOccurrenceIdIn`: método público que "no hay que llamar"; type/visibility: sacar el chunking del contrato público (un solo método `findByOccurrenceIdIn` y la query cruda fuera de la interfaz que ven los demás).
3. `allocation/service/impl/AllocationServiceImpl#resolveAndValidate` condición `source == IMPORTED || source == SYSACAD`: extract `AllocationSource#allowsPastOccurrences()`.
4. `allocation/service/impl/AllocationServiceImpl#resolveAndValidate(…, String action)`: `action` nulo cambia el comportamiento; extract: separar la descripción de auditoría de la validación.
5. `roomrequest/model/RoomRequestItem#requiresSpecialAssignment` duplicada en `roomrequest/specification/RoomRequestItemSpecification` (l. 42-43): rearchitecture: una sola fuente de la regla (columna generada o predicado compartido).
6. `roomrequest/model/RoomRequestItem#assignClassrooms(List<Long>, List<List<Long>>)`: listas paralelas por índice; type: lista de record `(classroomId, occurrenceIds)`.
7. `roomrequest/model/RoomRequestItem` campos `date` / `dayOfWeek` (l. 85-90): mutuamente excluyentes y nulos según tipo; type: valor que modele "fecha puntual o día de dictado".
8. `auth/security/SecurityUser` rama `scopeId == null` (l. 64-67): alcance BUILDING sin edificio tolerado en código; type: CHECK en la tabla de asignación de roles (nueva migración) que lo impida.
9. `space/service/impl/ClassroomTypeServiceImpl#rejectDefaultTypeChange(…, null)`: `null` significa "desactivar"; extract: dos métodos (`rejectDeactivatingDefault`, `rejectRenamingDefault`).
10. `space/service/impl/ClassroomTypeServiceImpl` `DEFAULT_CLASSROOM_TYPE` buscado por descripción: rearchitecture: marcar el tipo por defecto con un flag o id fijo en vez del texto.
11. `events/service/impl/AcademicEventServiceImpl` `requirementsAbsent` (l. 544): cinco nulos chequeados a mano; extract: objeto `requirements` anidado en el DTO o `dto.hasRequirements()`.
12. `events/model/AcademicEvent#sysacadHash`: guarda el hash de lo que el sync escribió, no de la fila entrante (doc l. 391-398 de `AcademicEventServiceImpl`); rename: `lastSyncWrittenHash` o similar.
13. `events/repository/RecurringEventRepository#findBySysacadHashIsNotNull`: necesita Javadoc para decir "sync-owned"; rename: método default `findSyncOwned()`.
14. `audit/model/RevisionSummaryKey` tipo de revisión como entero 0/1/2: type: `org.hibernate.envers.RevisionType`.
15. `audit/internal/SigaRevisionListener#rootAuditTable`: exige `@Table(name)` explícito en cada entidad auditada; rearchitecture: obtener la tabla `_aud` del metamodelo de Hibernate/Envers.
16. `audit/internal/SigaRevisionListener` booleano `system` (l. 37-38): extract `resolveActor()` que devuelva `ActorType`.
17. `audit/internal/AuditOperationAspect`: un método anotado que se une a una transacción externa pierde `operacion_id` (Javadoc l. 14-20); rearchitecture del momento en que se sella la operación.
18. `audit/internal/AuditOperationContext#end`: devuelve `null` como "nada pendiente"; type: `Optional<PendingDescription>`.
19. `preview/service/impl/PreviewEngine#suggest`, `roomrequest/service/impl/DirectEventServiceImpl` (l. 76) y `RoomRequestSuggestionServiceImpl` (l. 46): "sin @Transactional: el solver no debe correr con conexión tomada" repetido; rearchitecture: un componente único que corra el solver fuera de transacción.
20. `roomrequest/service/impl/DirectEventServiceImpl` (l. 189) y `RoomRequestSuggestionServiceImpl` (l. 112): re-guardan la sugerencia porque el cache no hace rollback; rearchitecture: consumir la sugerencia recién en `afterCommit`.
21. `preview/service/impl/CaffeinePreviewStore`, `roomrequest/service/impl/DirectEventSuggestionStore`, `RoomRequestSuggestionStore` `take`: mismo comentario y mismo `asMap().remove` en tres clases (Plausible, no comparé el resto del cuerpo); extract: store genérico de un solo uso.
22. `allocation/service/impl/AllocationImpactServiceImpl#freeRoomsAt`: el comentario aclara "después de aplicar el pedido"; rename `freeRoomsAfterApplying`.
23. `dashboard/service/impl/DashboardSnapshotServiceImpl` `lockMonth(year * 100L + month)` (l. 98): type: `lockMonth(YearMonth)` que arme la clave adentro.
24. `audit/service/impl/AuditCsvWriter#text`: el comentario explica que neutraliza fórmulas; rename `neutralizeFormula`.

## Totales (código Java, sin sysacad)

| | Bloques | Líneas |
|---|---|---|
| Borrar | 236 | 546 |
| Conservar tal cual (inglés) | 20 | 54 |
| Conservar y traducir al inglés | 52 | 221 |
| Total | 308 | 821 |
| MUST KILL | 24 | - |

Migraciones: 23 archivos, ~830 líneas de comentario (710 en V1); todas "no tocar, migración aplicada".

## Resumen

Dos tercios de las líneas de comentario de `src/main/java` (546 de 821) se pueden borrar: son Javadoc de clases impl, repositorios y modelos internos, notas de historia y referencias a planes en `.claude/docs`. Lo que se conserva es, en su mayoría, Javadoc de interfaces de servicio cruzadas entre módulos, que en 52 bloques sigue en español y hay que traducir. Los módulos `audit` y `dashboard` concentran el borrado; `roomrequest` y `allocation` concentran los MUST KILL, que en su mayoría son arreglos chicos (records, renames, extraer un método).
