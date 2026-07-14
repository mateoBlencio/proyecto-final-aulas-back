# ADR-003: DTOs como records

## Estado

Aceptado — 2026-07-10

## Contexto

Antes de esta decisión convivían cuatro estilos de DTO en el proyecto:

- Records planos (`ExcelRowDto`, `ImportResultDto`, `ClassroomFilter`,
  `OccurrenceConflictDto`, los `*RequestDto` de `allocation`).
- Lombok `@Value` + `@Builder` (`AllocationResponseDto`,
  `OccurrenceResponseDto`, `RecurringEventResponseDto`,
  `UniqueEventResponseDto`, `AcademicPeriodResponseDto`,
  `CommissionResponseDto`, `SpecialtyResponseDto`, `StudyPlanResponseDto`,
  `SubjectResponseDto`).
- Lombok `@Getter` + `@Builder` (`ClassroomResponseDTO`, `BuildingResponseDto`).
- Lombok `@Data` mutable (`AutoPreviewRequestDto`).

Además, dos archivos usaban el sufijo `DTO` en mayúsculas
(`ClassroomRequestDTO`, `ClassroomResponseDTO`) mientras el resto del
proyecto usa `Dto`.

Esta mezcla no aportaba nada: todos estos tipos son datos inmutables de
transporte entre capas (persistencia → HTTP), sin comportamiento propio más
allá de exponer sus campos. Java records son el tipo del lenguaje pensado
exactamente para eso, con menos código (sin anotaciones de Lombok) y
garantías del compilador (inmutabilidad, `equals`/`hashCode`/`toString`
generados, constructor canónico único).

## Decisión

Todos los DTOs pasan a ser records planos, con sufijo `Dto` (nunca `DTO`) sin
excepción:

- `ClassroomRequestDTO` → `ClassroomRequestDto`,
  `ClassroomResponseDTO` → `ClassroomResponseDto` (archivo, clase y todos los
  usos renombrados).
- El resto de los DTOs `@Value`/`@Builder`/`@Getter`/`@Data` listados en el
  contexto pasan a record, conservando exactamente los mismos componentes,
  tipos y anotaciones de validación (`@NotNull`, `@NotEmpty`, `@Min`, `@Max`,
  etc.) y de Swagger (`@Schema`) sobre los componentes del record en vez de
  sobre campos.
- Todos los puntos de construcción que usaban `.builder()...build()` pasan al
  constructor canónico del record (posicional). Los mappers MapStruct generan
  ese constructor canónico automáticamente (MapStruct soporta records desde
  la 1.5.2).
- `AcademicEventResponseDto` es una interfaz sellada (`sealed interface`)
  implementada por los records `RecurringEventResponseDto` y
  `UniqueEventResponseDto`. Sus métodos abstractos se renombran de
  `getId()`/`getType()`/`getEnrolled()`/`getStartTime()`/`getDurationMinutes()`
  a `id()`/`type()`/`enrolled()`/`startTime()`/`durationMinutes()`: un record
  no puede implementar un método abstracto `getX()` con su accessor generado
  (que siempre se llama igual que el componente), así que la interfaz se
  ajusta a la convención de accessors de record en vez de forzar accessors
  redundantes en cada implementación. Los pocos call sites que llamaban
  `.getId()` sobre este tipo (`AcademicEventController`,
  `ExcelImportServiceImpl`) pasan a `.id()`.
- El wire JSON no cambia: Jackson serializa un record por el nombre de sus
  componentes igual que serializaba los getters de Lombok (`getId()` →
  propiedad `id`; con record, el componente ya se llama `id` directamente),
  así que los nombres de propiedad expuestos por la API son idénticos a
  antes.

## Consecuencias

- Menos código por DTO (sin anotaciones de Lombok, sin clase builder
  generada) y menos superficie para bugs de mapeo manual de campos.
- Inmutabilidad real garantizada por el lenguaje, no por convención de
  Lombok (`@Value` ya daba inmutabilidad, pero record la hace explícita y
  sin depender de que nadie agregue un setter por error).
- Los DTOs que participan de jerarquías (`AcademicEventResponseDto`) deben
  declarar sus métodos abstractos con nombres de accessor de record
  (`id()`, no `getId()`), lo cual es una convención distinta a la que usan
  las entidades JPA del proyecto (que siguen con Lombok `@Getter` y por lo
  tanto `getId()`). Esta asimetría es intencional: las entidades JPA no son
  records (Hibernate necesita mutabilidad y constructor sin argumentos) y
  los DTOs sí, así que ambos siguen la convención de accessor que le
  corresponde a su propio mecanismo de generación.
- Los componentes de un record no pueden tener el mismo nombre que un
  método ya definido en el propio record (p. ej. no puede haber un
  componente llamado `class`); no se encontró ningún caso así en los DTOs
  migrados.

## Alternativas consideradas

- **Mantener `@Value` + `@Builder` de Lombok y solo unificar el sufijo
  `Dto`**: se descartó porque no elimina el código generado por Lombok ni
  aprovecha las garantías del lenguaje; además el plan explícitamente pide
  records para esta fase.
- **Records pero conservando un método estático `builder()` artesanal para
  no tocar los call sites**: se descartó por ser complejidad extra sin
  beneficio — los call sites de builder son pocos y localizados
  (mappers/servicios), y pasar al constructor canónico es más simple y más
  idiomático que mantener un builder falso sobre un record.
