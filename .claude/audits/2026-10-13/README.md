# Auditoría del backend SIGA, 2026-10-13

Commit auditado: `22c693f2` (mismo árbol que `origin/develop`). Plan y
criterios: `.claude/plans/auditoria-completa.md`. Fuera de alcance: el
módulo `sysacad` (auditado después, el 2026-10-14, en `10-sysacad.md`) y el frontend.

Nadie modificó código de producción durante la auditoría. Cada hallazgo
tiene severidad, esfuerzo (S menos de 1 h, M menos de 1 día, L más), la
referencia al estándar y la evidencia `archivo:línea` en su anexo.
"Confirmado" significa que alguien leyó el código que lo prueba o lo
reprodujo; "Plausible" queda sujeto a la pregunta que lo acompaña.

## Números de base

| Medición | Resultado |
|---|---|
| Suite completa (`./mvnw verify`, Docker) | 2263 tests, 0 fallas, 0 skipped, 3m30s |
| Cobertura JaCoCo sin sysacad | 94.7% líneas, 85.4% ramas; ramas más bajas: optimizer 75.6%, settings 76.3% |
| PIT (allocation, roomrequest, auth; solo tests unitarios) | 884 mutantes, 72% muertos, fuerza 90% sobre lo cubierto |
| `ModularityTests` | pasa (3/3) |
| SpotBugs + FindSecBugs | 878 avisos; casi todos ruido o falsos positivos (triage en `03-herramientas.md`) |
| OSV-Scanner sobre SBOM (167 deps) | 17 paquetes con CVE, varios CVSS 9.1-9.8 en Tomcat y spring-webmvc |
| gitleaks, historial completo | JWT de acceso vencidos y 2 refresh tokens en `bruno/` (no figuran en la base) |

## Hallazgos por dimensión

| Dimensión | Anexo | Alta | Media | Baja |
|---|---|---|---|---|
| Arquitectura | `01-arquitectura.md` | 0 | 4 | 9 |
| Datos, DER y rendimiento | `02-datos-rendimiento.md` | 1 | 3 | 16 |
| Herramientas | `03-herramientas.md` | 1 (DEP-01) | | |
| Seguridad (ASVS 5.0 L2) | `04-seguridad.md` | 1 | 5 | 8 |
| Testing | `05-testing.md` | 1 | 4 | 3 |
| Comentarios | `06a-comentarios.md` | | | 546 líneas a borrar, 221 a traducir, 24 MUST KILL |
| Calidad, grupo A | `06b-calidad-grupoA.md` | 0 | 3 | 11 |
| Calidad, grupo B | `06c-calidad-grupoB.md` | 1 | 2 | 7 |
| API y UX | `07-api-ux.md` | 0 | 5 | 7 |
| Operación y CI | `08-operacion.md` | 1 | 3 | 8 |
| Revisión de arquitectura (opinión, 2026-10-14) | `09-revision-arquitectura.md` | | | 12 propuestas (R1-R12) |
| Módulo sysacad (2026-10-14) | `10-sysacad.md` (+ `10a` a `10d`) | 4 | 9 | resto, deduplicado |

Ningún hallazgo llegó a Crítica. Hay duplicados entre anexos: SEC-14 es
QA-B-01, SEC-05 es API-05, y QA-A-02 se superpone con PERF del anexo 02.

## Arreglar ahora

Criterio: severidad Alta, o Media con esfuerzo S. Orden sugerido por valor
sobre costo.

| # | ID | Qué | Esfuerzo |
|---|---|---|---|
| 1 | DEP-01 | Subir Spring Boot 4.0.6 a 4.0.8: trae Tomcat 11.0.24, Spring Framework 7.0.9, Security 7.0.7, Jackson 2.21.5/3.1.5 y postgresql 42.7.13, y cierra la mayoría de los CVE. Re-escanear después; si siguen abiertos los de Tomcat 11.0.25 y Jackson 2.21.7/3.1.7, fijar esas versiones | S |
| 2 | QA-B-01 / SEC-14 | `RefreshTokenServiceImpl.refresh`: la revocación por reuso de token se pierde en el rollback de la excepción. `noRollbackFor` o revocar en `REQUIRES_NEW`, más un test | S |
| 3 | DAT-02 | `ClassroomServiceImpl.java:80` busca el número de aula en todos los edificios: 500 con números repetidos (7 en dev) y rechazos falsos. Usar `findByRoomNumberAndBuildingAndDeletedAtIsNull` y un índice único parcial | S |
| 4 | QA-A-01 | Timefold en `PHASE_ASSERT` (modo de depuración) en todos los perfiles, `application.yaml:165` | S |
| 5 | OPS-01 | CI de PRs en el runner self-hosted que también despliega, con repo público. Depende de la pregunta 1 | S |
| 6 | TEST-02 | Sin test de 403 en endpoints de escritura: un `@ParameterizedTest` por controller con rol `CONSULTA` | M |
| 7 | PERF-01 | Falta `hibernate.default_batch_fetch_size`: N+1 en listados paginados. Una línea | S |
| 8 | API-02 | El 500 del solver devuelve `cause.getMessage()` al cliente (`OptimizerServiceImpl.java:141`) | S |
| 9 | API-05 / SEC-05 | `POST /v1/room-requests` (público) sin `@Size` en `items` y `preferredClassroomIds`. Depende de la pregunta 6 | S |
| 10 | DAT-01 | V23 huérfana en dev, oculta por `ignore-migration-patterns: "*:missing,*:future"`. V26 con `DROP TABLE IF EXISTS` de sus 4 tablas, `flyway repair` y quitar los patterns | S |
| 11 | SEC-01 | Rate limit de login por email: cualquiera bloquea una cuenta ajena 15 min con 5 intentos | S |
| 12 | API-01, API-03 | 404 en inglés con nombres de clase; `sort` inválido termina en 500 | S |
| 13 | ARQ-02 | Sin reproceso de publicaciones de eventos incompletas: si falla `OccurrenceVacatedListener`, el aula queda asignada | S |
| 14 | OPS-02, OPS-04 | Swagger y health con detalle abiertos en dev/test; acciones de GitHub fijadas por tag y no por SHA | S |
| 15 | DAT-03 | Borrar en una migración nueva las funciones `reset_asignaciones` y `reset_eventos` (SECURITY DEFINER) | S |
| 16 | TEST-01, TEST-03, TEST-05 | `ManualEmailSendTest` sin assert corre en cada build; alcance por edificio solo probado con mocks; borde `endDate == hoy` | S |
| 17 | PIT | `DefaultBuildingScopeResolverTest`: falta verificar que `requireAccess` no lance para el edificio permitido | S |
| 18 | ARQ-01, QA-A-02, QA-B-02, OPS-03, SEC-02, SEC-06 | Ciclo events/allocation vía `common`; triple lectura en `findConflicts`; código muerto; zona horaria UTC vs Buenos Aires; los dos SEC dependen de preguntas | S |

Limpieza de comentarios: aplicar el anexo `06a` como un único commit
mecánico (borrar 546 líneas, traducir 221). Antes conviene revisar su
criterio sobre Javadoc: borra el de los servicios que solo usa su propio
módulo.

## Deuda anotada

Media con esfuerzo M o L, sin propuesta de rediseño ahora:

- ARQ-03: la API de cada módulo expone el servicio completo con DTO HTTP;
  las validaciones no corren entre módulos.
- ARQ-04: `dashboard` y `audit` leen por SQL tablas de otros módulos sin
  declararlo.
- QA-A-03: `loadOccupancy` trae todas las ocurrencias del rango (74.698 en
  dev) en cada validación.
- QA-B-03: el ingest no es atómico.
- API-04: duplicados devuelven 400 y la carrera devuelve 500.
- TEST-04: restricciones del optimizer sin `ConstraintVerifier`.
- SEC-03: sin política de contraseñas ni endpoint de cambio.

Los MUST KILL de `06a` que piden rediseño (solver fuera de transacción,
regla de `requiresSpecialAssignment` duplicada) también van acá.

## Preguntas abiertas

1. ¿Está activa la aprobación obligatoria para PRs de colaboradores
   externos en GitHub? Si sí, OPS-01 baja a Media.
2. ¿dev, test y MailHog son accesibles solo por Tailscale? Define
   OPS-02, OPS-08, SEC-10 y SEC-13.
3. ¿El secreto JWT de `application-dev-local.yaml` se usó alguna vez como
   `JWT_SECRET_DEV/TEST`? Si sí, SEC-09 sube a Alta y hay que rotarlo.
4. ¿El backend corre detrás de un reverse proxy? Define SEC-02.
5. ¿Un auxiliar puede mover a su edificio ocurrencias de otro con
   `PUT /v1/allocations`? Define SEC-06.
6. ¿Qué tope de ítems y aulas preferidas acepta una solicitud? Define el
   arreglo de API-05.
7. ¿Zona horaria oficial: UTC o America/Argentina/Buenos_Aires? Define
   OPS-03, QA-B-06 y QA-B-07.
8. ¿Es válido el mismo número de aula en dos edificios? Los datos de dev
   dicen que sí; confirma el arreglo de DAT-02.
9. ¿La base de `test` tiene la V23 aplicada? ¿Qué grants reales tienen las
   funciones `reset_*` en dev y prod? (DAT-01, DAT-03)
10. ¿Un import que falla a mitad de archivo debe dejar lo ya guardado?
    (QA-B-03)
11. `updateUniqueEvent` mueve fecha y hora sin revalidar solapamiento ni
    permisos, y permite fechas pasadas; DDR-001 permite anular una
    solicitud resuelta y el código lo rechaza; `DirectEventDraftValidator`
    decide por rol y no por `PERM_*`. ¿Cuáles son reglas de negocio?
12. ¿El frontend puede absorber cambios de contrato este cuatrimestre
    (API-10, API-11)?
13. ¿`event_publication` tiene filas con `completion_date` nula en dev o
    prod? Si sí, ARQ-02 sube a Alta.

## Cómo se hizo

Nueve agentes en paralelo, uno por dimensión (calidad dividida en dos
grupos de módulos), con un brief común de solo lectura. El hilo principal
corrió las herramientas y verificó en el código los hallazgos Alta antes
de consolidarlos: QA-B-01, DAT-02, QA-A-01 y el mutante de
`requireAccess`. El DER en Mermaid está al inicio de `02-datos-rendimiento.md`
y el diagrama de módulos en `01-arquitectura.md`.

## Hallazgos cruzados desde la auditoría del frontend (2026-10-14)

La auditoría del frontend (`proyecto-final-aulas-front/.claude/audits/2026-10-14/README.md`)
reportó tres puntos que tocan al backend:

| ID | Severidad | Origen en el front | Qué toca al backend |
|---|---|---|---|
| X-01 | Alta en el front; en el back, Media (Plausible) | ARQ-01 y ARQ-02 del front: manda ids de asignación como `occurrenceIds` en `PUT /v1/allocations` | `AllocationTargetResolver.java:73-77` solo valida que la ocurrencia exista y no sea pasada, así que con un id equivocado mueve otra clase sin error. El arreglo principal va en el front. El back podría exigir una asignación previa cuando la operación es una reasignación; antes hay que confirmar con negocio si `PUT` también sirve para asignar ocurrencias sin aula |
| X-02 | Media | UX-01 del front | API-01 llega a pantalla: "Classroom not found with id" en `/solicitar-aula` cuando un borrador guardado tiene un aula dada de baja. Confirma la prioridad de API-01 |
| X-03 | Media | QA-B-01 del front: pide `size=500` contra `max-page-size: 200` | En dev-snapshot hoy no se trunca ninguna lista que el front pida así (aulas 108-123, edificios 32, especialidades 61 activas). Los eventos (3.825) sí superarían el tope si se listaran con ese tamaño |
