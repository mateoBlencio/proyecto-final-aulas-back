# Documentación por módulo

Ficha técnica de cada módulo del monolito modular SIGA. Objetivo: mapear
responsabilidad, API pública, dependencias y — sobre todo — **gaps, oportunidades
de mejora y estado de testing**.

Un archivo por módulo. Cada ficha sigue la misma estructura:

1. **Responsabilidad** — qué hace y qué no.
2. **API pública (`::api`)** — servicios expuestos + endpoints REST.
3. **Estructura interna** — modelo, capas, piezas clave.
4. **Dependencias** — hacia qué módulos y por qué.
5. **Gaps y oportunidades** — deuda, faltantes, riesgos.
6. **Testing** — estado actual + tests unitarios/integración recomendados.

| Módulo | Ficha | REST | Rol |
|---|---|---|---|
| `space` | [space.md](space.md) | Sí (parcial) | Aulas, edificios, tipos |
| `academic` | [academic.md](academic.md) | **No** | Materias/comisiones/planes (solo `::api`) |
| `allocation` | [allocation.md](allocation.md) | Sí | Eventos, ocurrencias, asignaciones |
| `solver` | [solver.md](solver.md) | No (vía allocation) | Optimización Timefold |
| `excelimport` | [excelimport.md](excelimport.md) | Sí | Carga masiva Excel |
| `common` | [common.md](common.md) | No (OPEN) | Config, excepciones, converters, auditoría |

## Hallazgo transversal: testing concentrado en `allocation`, casi ausente en el resto

> Sprint 03 agregó cobertura unitaria real: `AllocationServiceImplTest`,
> `AllocationProblemServiceImplTest` y `AutoAllocationServiceImplTest` (más
> `ModularityTests.java`, fronteras de módulo, y `CaffeinePreviewStoreTest` en `solver`,
> preexistente). **`academic`, `space`, `common` y `excelimport` siguen en cero tests**, y
> no hay ningún test de integración (Testcontainers) todavía.

Consecuencias concretas:

- **`CLAUDE.md` documenta un comando `./mvnw test -Dtest=SolverServiceImplTest`
  que apunta a una clase que no existe.** El ejemplo de la doc es aspiracional, no real
  (sigue sin existir tras sprint 03; el módulo `solver` solo tiene `CaffeinePreviewStoreTest`).
- La infraestructura de Testcontainers (`jdbc:tc:postgresql:16-alpine`) descrita en
  `CLAUDE.md` no tiene ningún test de integración que la use.
- El refactor de desacople entre módulos (relaciones JPA → IDs planos) sigue **sin
  red de integración automatizada**: la cobertura nueva es unitaria (mocks de
  repositorios/fachadas), no valida composición real contra BD (N+1, DTO mal armado,
  borrado histórico).

Prioridad de cobertura sugerida (mayor ROI primero, ajustada post-sprint-03):

1. **`allocation`** — cubierto en lo unitario (asignación manual + conflictos, problemas de
   asignación, flujo automático completo); falta integración end-to-end y
   `RecurringEvent.toOccurrences()` / composers.
2. **`solver`** — determinismo de `computeConflicts`, dedup de ocupación, restricciones
   (`ConstraintVerifier`). Sigue sin cobertura más allá de `CaffeinePreviewStoreTest`.
3. **`excelimport`** — parsing + idempotencia de `findOrCreate`. Cero tests.
4. **`space` / `academic`** — specifications, natural keys, `findOrCreate`. Cero tests.

## Convenciones de referencia

- Modelo de dominio: [`../modelo-dominio.md`](../modelo-dominio.md).
- Decisiones arquitectónicas: [`../adr/`](../adr/).
- Reglas de fronteras y comunicación por ID+DTO: `CLAUDE.md` (raíz) y ADR-004.
</content>
</invoke>
