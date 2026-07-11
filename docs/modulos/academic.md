# Módulo `academic`

## Responsabilidad

Datos académicos de referencia: **especialidades**, **planes de estudio**, **materias**,
**períodos académicos**, **comisiones** y **materia-comisión** (con inscriptos). Es un
módulo de **soporte**: alimenta a `allocation` (materia/comisión de un evento) y a
`excelimport` (destino de la carga masiva).

## API pública (`::api`)

**No expone REST.** Todos sus servicios son consumidos internamente por otros módulos.
El patrón dominante es `findOrCreate` (idempotente, para la importación) + `findById` /
`findByIds` (para componer DTOs en `allocation`).

| Servicio | Firma clave |
|---|---|
| `SpecialtyService` | `findOrCreate(specialtyCode)` |
| `StudyPlanService` | `findOrCreate(planCode, specialtyCode)` — plan por clave natural compuesta |
| `SubjectService` | `findById`, `findByIds`, `findOrCreate(code, name, studyPlanCode, specialtyCode, term)` |
| `AcademicPeriodService` | `findOrCreate(year, termType)`, `findActive()` |
| `CommissionService` | `findById`, `findByIds`, `findOrCreate(courseCode, commissionNumber, yearLevel, periodYear, periodSemester)` |
| `SubjectCommissionService` | `findOrCreate(subjectId, commissionId, enrolledCount)` |

**Comunicación por clave natural, no por ID cruzando frontera**: p. ej. `StudyPlanService`
recibe `specialtyCode` (no un ID de especialidad), porque el consumidor no debería conocer
IDs internos de `academic`. Consistente con ADR-004.

**`AcademicPeriodService.findActive()`** (sprint 03): devuelve los períodos académicos
con `activo = true`. Consumido por `allocation` (`AllocationProblemServiceImpl`) para
resolver el `to` por defecto de los endpoints `GET /v1/allocations/{unassigned,overcrowded,overlaps}` — ver
[allocation.md](allocation.md). `AcademicPeriodResponseDto` expone `year`, `semester`,
`startDate` y `endDate` (antes solo `year`/`semester`); `endDate` puede ser `null`.

## Estructura interna

`dto/response` / `mapper` (MapStruct) / `model` / `repository` / `service` (+ `impl`).
**Sin `controller`, sin `dto/request`** (no hay entrada HTTP).

Entidades: `Specialty`, `StudyPlan`, `Subject`, `AcademicPeriod`, `Commission`,
`SubjectCommission`, enum `TermType`.

## Dependencias

Solo `common`. Módulo hoja.

## Gaps y oportunidades

- **Sin superficie de consulta REST.** El front no puede listar materias, comisiones ni
  planes. Si la UI necesita selects/autocompletes de estas entidades, hoy no hay endpoint.
  Evaluar exponer al menos GETs de lectura.
- **`findOrCreate` e idempotencia bajo concurrencia.** Toda la carga depende de que las
  claves naturales tengan unique constraints en BD y de que `findOrCreate` resuelva la
  colisión. Si el esquema del DBA no tiene esos únicos, dos filas iguales crean duplicados
  silenciosos. **Verificar constraints en `docs/ddl/` y fijarlos con test de integración.**
- **`term` como String vs `TermType` enum**: `SubjectService.findOrCreate` recibe `term`
  como texto mientras `AcademicPeriodService` usa el enum `TermType`. Inconsistencia de
  tipos entre servicios hermanos; riesgo de valores inválidos no validados.
- **Sin update/delete.** Las entidades solo se crean o se leen; no hay corrección de datos
  mal importados salvo tocar la BD directamente.

## Testing

**Estado actual: cero tests.**

### Unitarios recomendados
- Cada `findOrCreate`: rama "existe" (no crea, `reused=true`) vs "no existe" (crea,
  `created=true`), y correcta construcción de la clave natural.
- Mappers MapStruct de cada entidad → response DTO.
- Validación/normalización de `term` → `TermType` (si aplica).

### Integración (Testcontainers) recomendados
- **Idempotencia real**: llamar `findOrCreate` dos veces con el mismo input crea **una**
  fila (requiere unique constraint en el esquema de test).
- Resolución de clave natural compuesta (plan por `planCode`+`specialtyCode`, período por
  `year`+`termType`) contra datos sembrados.
</content>
