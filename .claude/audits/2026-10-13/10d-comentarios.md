# 10d Comentarios del módulo sysacad (solo reporte, sin cambios en el repo)

Alcance: `src/main/java/ar/edu/utn/frc/siga/sysacad` (56 archivos `.java`, 2137 líneas, commit 22c693f2). Tres archivos tienen comentarios: 4 bloques, 14 líneas contando delimitadores. Los 53 archivos restantes, incluidos `package-info.java`, `api/*` y los DTOs `Raw*`, no tienen ningún comentario.

Criterio: el mismo de `.claude/audits/2026-10-13/06a-comentarios.md`. Se conservan licencias, restricciones de Sysacad o de plataforma que no controlamos, Javadoc de contrato de la API pública del módulo (`sysacad.api`, `@NamedInterface("api")`) y links a issues/RFC. Ante la duda, se borra.

Ningún comentario del módulo cae en una excepción. Los cuatro bloques están en `internal/` (fuera del contrato `api`) y describen decisiones nuestras: devolver `null`, loguear dentro del resolver, la historia de un refactor y un guard para un caso que hoy no ocurre. Las rarezas de Sysacad (relleno con espacios en `trim`, `"S"` en `YES`, semestre `0` en `termTypes`, fechas `uuuuMMdd`) no tienen comentario. Las constantes y los WARN ya las explican.

## Tabla por archivo

Rutas bajo `src/main/java/ar/edu/utn/frc/siga/sysacad/`.

| Archivo | Borrar | Conservar (excepción) | Traducir a inglés y conservar (excepción) |
|---|---|---|---|
| internal/mapper/SysacadCatalogMapper.java | 60 | - | - |
| internal/service/impl/SysacadSyncOrchestratorImpl.java | 58-61 | - | - |
| internal/sync/SysacadCommissionResolver.java | 15-19, 34-37 | - | - |

Motivo de cada borrado:

- `SysacadCatalogMapper.java:60`: Javadoc de un método público de un `@Component` interno. No es contrato de `sysacad.api`. Explica en prosa que `toHoliday` usa `null` como señal de fila descartada.
- `SysacadSyncOrchestratorImpl.java:58-61`: sermón de workaround con guion largo. Las 8 constantes de `SysacadView` (`EDIFICIOS`, `AULAS`, `ESPECIALIDADES`, `MATERIAS`, `COMISIONES`, `EVENTOS`, `ASIGNACIONES`, `FERIADOS`) tienen hoy un `SysacadViewSyncer` registrado en `internal/sync/*SyncService.view()`, así que el caso de vista sin syncer que justifica el comentario no ocurre en ningún camino vivo.
- `SysacadCommissionResolver.java:15-19`: Javadoc de una clase package-private, con historia ("hacían de forma idéntica", "antes cada syncer declaraba a mano"). El ciclo de vida (una instancia por corrida) ya se lee en `AllocationSyncService.java:64` y `AcademicEventSyncService.java:53`, donde cada corrida hace `new SysacadCommissionResolver(...)`.
- `SysacadCommissionResolver.java:34-37`: Javadoc de un método package-private que aclara un efecto colateral oculto (el WARN ya salió) y le dicta al caller cómo usarlo.

## MUST KILL

- `SysacadSyncOrchestratorImpl.resyncAll`: recorre `SysacadView.values()` y necesita un guard `syncersByView.containsKey(view)` más 4 líneas de prosa. Si recorre `syncersByView.entrySet()`, sobran el guard y el comentario, y el WARN de `sync(view, snapshot)` deja de ser camino de error del resync.
- `SysacadCatalogMapper.toHoliday`: devuelve `null` como señal de fila descartada y por eso necesitó un Javadoc. Debería devolver `Optional<SysacadHolidayDto>`. `toAcademicEvent` y `toAllocation` hacen lo mismo sin comentario y comparten el arreglo.
- `SysacadCommissionResolver.resolve`: oculta que loguea un WARN cuando el resultado es vacío. El nombre debería decirlo (`resolveOrWarnSkip`) o el método debería devolver el motivo del salteo y dejar el log al syncer.

## Totales

- Archivos con comentarios: 3 de 56.
- Bloques: 4. Borrar 4 (14 líneas). Conservar 0. Traducir 0.
- MUST KILL: 3.
- Saltados: ninguno. No hay comentarios de licencia, supresiones (`@SuppressWarnings`, `NOSONAR`) ni links a issues en el módulo.
