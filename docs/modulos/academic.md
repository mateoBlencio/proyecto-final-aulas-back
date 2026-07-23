# Módulo `academic`

## Responsabilidad

Datos académicos de referencia: **especialidades**, **planes de estudio**, **materias**,
**períodos académicos**, **comisiones** y **materia-comisión** (con inscriptos). Es un
módulo de **soporte**: alimenta a `allocation` (materia/comisión de un evento) y a
`excelimport` (destino de la carga masiva).

## API pública (`::api`)

Exponen algunos GETs de solo lectura (`SubjectController`, etc.) además de ser consumidos
internamente por `allocation` y `excelimport`. **Catálogo cargado por fuera de esta app**:
salvo `AcademicPeriod`, ningún servicio crea — busca por clave natural y lanza
`ResourceNotFoundException` si no existe. Esto convierte a cada fachada en el **seam de
proveedor de datos**: hoy la impl lee de Postgres, el día que el catálogo se sirva desde
una API externa, una impl cliente-HTTP cumple el mismo contrato (ID+DTO) sin tocar
`allocation`/`excelimport`.

| Servicio | Firma clave |
|---|---|
| `SpecialtyService` | `findBySpecialtyCode(specialtyCode)` |
| `StudyPlanService` | `findByPlanCodeAndSpecialtyCode(planCode, specialtyCode)` — plan por clave natural compuesta |
| `SubjectService` | `findAll`, `findById`, `findByIds`, `findByCodeAndStudyPlan(code, studyPlanCode, specialtyCode)` |
| `AcademicPeriodService` | `findOrCreate(year, termType)` (único creador: período es dato **derivado**, no catálogo externo), `findActive()`, `findAll`, `findById` |
| `CommissionService` | `findById`, `findByIds`, `findAll`, `findByCourseAndNumberAndPeriod(courseCode, commissionNumber, periodYear, periodSemester)` |
| `SubjectCommissionService` | `findAll`, `findById`, `findBySubjectAndCommission(subjectId, commissionId)` |

**Comunicación por clave natural, no por ID cruzando frontera**: p. ej. `StudyPlanService`
recibe `specialtyCode` (no un ID de especialidad), porque el consumidor no debería conocer
IDs internos de `academic`.

**`AcademicPeriodService.findActive()`**: devuelve los períodos académicos
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

- **`AcademicPeriod.findOrCreate` e idempotencia bajo concurrencia.** Es el único creador
  que queda en el módulo (período = año+semestre, dato derivado). Depende de un unique
  constraint en BD para resolver la colisión bajo carga concurrente.
- **Sin update/delete.** Las entidades solo se leen (o, para `AcademicPeriod`, se crean);
  no hay corrección de datos mal cargados salvo tocar la BD directamente.

## Testing

**Estado actual: cero tests.**

### Unitarios recomendados
- Cada `findByX`: rama "existe" (devuelve DTO) vs "no existe" (`ResourceNotFoundException`),
  y correcta construcción de la clave natural.
- `AcademicPeriodService.findOrCreate`: rama "existe" (no crea) vs "no existe" (crea).
- Mappers MapStruct de cada entidad → response DTO.

### Integración (Testcontainers) recomendados
- **Idempotencia real de `AcademicPeriod.findOrCreate`**: llamarlo dos veces con el mismo
  input crea **una** fila (requiere unique constraint en el esquema de test).
- Resolución de clave natural compuesta (plan por `planCode`+`specialtyCode`, comisión por
  `courseCode`+`commissionNumber`+período) contra datos sembrados.
</content>
