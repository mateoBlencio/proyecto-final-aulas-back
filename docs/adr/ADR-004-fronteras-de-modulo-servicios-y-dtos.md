# ADR-004: Fronteras de módulo solo con servicios y DTOs; referencias por ID

## Estado

Aceptado — 2026-07-10

## Contexto

Hasta esta decisión, las fachadas `api` (interfaces de servicio anotadas
`@NamedInterface("api")`) de `academic` y `space` eran mixtas: algunos
métodos devolvían DTOs (`findDtoById`, `findDtosByIds`, los `create`/`update`
de `Classroom`) y otros devolvían o recibían directamente entidades JPA
(`Specialty save(Specialty)`, `Optional<Subject> findById(Long)`,
`FindOrCreateResult<Building> findOrCreate(String)`, etc.). Además, tres
entidades (`Specialty`, `StudyPlan`, `Subject`, `Commission`,
`AcademicPeriod`, `SubjectCommission` en `academic`; `Building`, `Classroom`
en `space`) y `ClassroomMapper` estaban anotadas `@NamedInterface("api")`
directamente, es decir, el modelo de persistencia de un módulo era en sí
mismo su contrato público.

Esto generaba dos problemas concretos, ambos con un único consumidor real:
`excelimport`, que necesita resolver o crear de a una las entidades
`Specialty → StudyPlan → Subject`, `AcademicPeriod → Commission →
SubjectCommission` y `Building → Classroom` mientras procesa cada fila del
Excel (el patrón `findOrCreate`, repetido idéntico 8 veces):

- **Acoplamiento estructural**: nada impedía que un módulo consumiera
  campos internos de la entidad ajena (relaciones `@ManyToOne`, setters),
  reintroduciendo exactamente el acoplamiento que las Fases 2 y 3 ya habían
  eliminado del resto del código (mappers MapStruct, `Allocation.classroomId`
  / `RecurringEvent.subjectId`/`commissionId` como IDs planos en vez de
  relaciones JPA).
- **`ModularityTests.verify()` no lo detectaba de forma estructural**: como
  Spring Modulith verifica referencias reales entre módulos y no el "buen
  gusto" de una firma de método, una entidad marcada `@NamedInterface("api")`
  quedaba habilitada para que **cualquier** cambio futuro en `excelimport` (u
  otro consumidor) empezara a navegar sus relaciones JPA sin que ningún test
  lo frenara — el guardián solo actúa cuando el acoplamiento ya existe en
  bytecode, no cuando la puerta está simplemente abierta.

Se combinan en un único ADR dos decisiones que en el plan original figuraban
por separado ("fronteras de módulo: servicios + DTOs, referencias por ID" y
"fachada api por módulo") porque en la práctica son la misma decisión vista
desde dos ángulos: una fachada `api` que solo expone DTOs *es* la forma en
que un módulo consumidor referencia datos ajenos por ID/DTO en vez de por
entidad. No hay una decisión sin la otra.

## Decisión

Las fachadas `api` de todos los módulos exponen **exclusivamente** DTOs
(records) y tipos que no son entidades JPA (enums como `TermType`); nunca
entidades ni mappers que produzcan/consuman entidades.

- **Los 8 `findOrCreate`** (`SpecialtyService`, `StudyPlanService`,
  `SubjectService`, `CommissionService`, `AcademicPeriodService`,
  `SubjectCommissionService` en `academic`; `BuildingService`,
  `ClassroomService` en `space`) devuelven `FindOrCreateResult<XxxDto>` en
  vez de `FindOrCreateResult<Entidad>`. Cada implementación colapsa a
  `FindOrCreateResult.resolve(repo.findBy...(...), () ->
  repo.save(...)).map(mapper::toDto)`.
- **Los parámetros de `findOrCreate` que antes recibían la entidad "padre"
  ya resuelta** (p. ej. `StudyPlanService.findOrCreate(Integer,
  Specialty)`) pasan a recibir su **clave natural** en vez de la entidad o
  un ID subrogado: `StudyPlanService.findOrCreate(Integer planCode, Integer
  specialtyCode)`. La razón es que `SpecialtyResponseDto` no expone un
  campo `id` (nunca lo necesitó: es un DTO usado hoy solo anidado dentro de
  otros DTOs de respuesta ya existentes, `StudyPlanResponseDto.specialty` /
  `SubjectResponseDto.studyPlan.specialty`) y agregarlo únicamente para
  este encadenamito interno de `excelimport` habría cambiado el wire JSON de
  esos DTOs anidados — algo explícitamente fuera de alcance en esta fase.
  Como la clave natural (`specialtyCode`; `planCode` + `specialtyCode`;
  `year` + `semester`) ya identifica la fila de forma única (son las mismas
  columnas de unicidad del esquema), resolverla de nuevo dentro de la
  implementación (`SpecialtyRepository.findBySpecialtyCodeAndDeletedFalse`,
  etc.) es una consulta extra y liviana, no una vuelta a acoplar entidades.
  Donde el DTO ya traía un `id` utilizable (`SubjectResponseDto`,
  `CommissionResponseDto`, `BuildingResponseDto`), se usa directamente
  (`SubjectCommissionService.findOrCreate(Long subjectId, Long commissionId,
  ...)`, `ClassroomService.findOrCreate(String, Integer buildingId, ...)`).
- **Nuevo `SubjectCommissionResponseDto`** (`id`, `subjectId`, `commissionId`,
  `enrolledCount`) y su `SubjectCommissionMapper`, porque el agregado no
  tenía DTO de respuesta propio hasta ahora.
- **Se eliminan de las interfaces `api`** los métodos que devolvían o
  recibían entidades y no tenían consumidores fuera de su propio módulo:
  `save(Entidad)` en los 6 servicios de `academic` (ninguno se llamaba desde
  ningún lado, ni siquiera intra-módulo) y `Optional<Subject>
  findById(Long)` / `Optional<Commission> findById(Long)` (ídem, muertos).
  `findDtoById` → `findById` y `findDtosByIds` → `findByIds` en
  `SubjectService`/`CommissionService`, liberado el nombre corto al borrar
  antes el método-entidad homónimo.
- **`BuildingService.findById(Integer)` (entidad)**, el único caso con un
  consumidor real pero *intra*-módulo (`ClassroomServiceImpl`, para
  materializar la relación JPA `Classroom.building`), se elimina de la
  fachada `api` y `ClassroomServiceImpl` pasa a resolver `Building` con
  `BuildingRepository` directamente — ambas clases viven en `space`, así que
  no cruza ninguna frontera; solo se saca del contrato público lo que nunca
  debió estar ahí.
- **`ImportCache`** (`excelimport`) cachea los DTOs de respuesta en vez de
  entidades; las claves de deduplicación pasan de `entidad.getId()` a la
  clave natural de la fila de Excel ya disponible en el `ExcelRowDto`
  (p. ej. `dto.studyPlanCode() + "-" + dto.specialtyCode()`) para los
  agregados sin `id` en su DTO, o al `.id()` del DTO para los que sí lo
  tienen.
- **Se quita `@NamedInterface("api")`** de las 8 entidades JPA que la
  tenían (`Specialty`, `StudyPlan`, `Subject`, `Commission`,
  `AcademicPeriod`, `SubjectCommission`, `Building`, `Classroom`) y de
  `ClassroomMapper`. `TermType` (enum, no entidad) la conserva: no es
  persistencia, es un tipo de valor sin problema en cruzar la frontera.
  Desde ahora, si algún cambio futuro reintroduce una entidad como tipo de
  retorno/parámetro de una fachada `api`, dejará de compilar contra el
  contrato marcado (el tipo ya no está habilitado como named interface) en
  vez de depender de que nadie lo use.

## Consecuencias

- Los únicos tipos que cruzan una frontera de módulo son DTOs (records) e
  IDs planos — coherente con la regla ya vigente para las relaciones JPA
  desde la Fase 3 (`Allocation.classroomId`,
  `RecurringEvent.subjectId`/`commissionId`) y con el ejemplo limpio que ya
  era `solver` (`SolverEvent`/`SolverRoom`/`SolverOccupancy`/`SolverPreview`).
- Quitar `@NamedInterface` de las entidades hace **estructuralmente
  imposible** (no solo "no usado hoy") que un módulo futuro navegue una
  relación `@ManyToOne` ajena o llame a un setter de una entidad de otro
  módulo: `ModularityTests.verifyBoundaries()` rompe el build en el momento
  en que alguien lo intente, no cuando el equipo lo note en code review.
- Costo: cada `findOrCreate` que antes recibía la entidad "padre" ya
  resuelta ahora hace una consulta adicional para resolverla de nuevo por
  clave natural dentro de su propia implementación (p. ej.
  `SubjectServiceImpl` resuelve `Specialty` y luego `StudyPlan` antes del
  `findOrCreate` de `Subject`). Es aceptable: son lookups por índice único
  ya existente, dentro de la misma transacción, y el volumen de filas de un
  import de Excel no es sensible a esta cantidad de queries extra (la
  Fase 6, fuera de alcance acá, es donde se aborda performance de
  `excelimport` en general).
- `ClassroomServiceImpl` depende de `BuildingRepository` en vez de
  `BuildingService` para la única necesidad que tenía (la entidad
  `Building`), lo que es correcto porque ambas clases son internas al mismo
  módulo — evita el rodeo de crear una interfaz "interna" extra sin
  necesidad real de desacoplar dos implementaciones del mismo módulo entre
  sí.

## Alternativas consideradas

- **Agregar `id` a `SpecialtyResponseDto` y `AcademicPeriodResponseDto`
  para poder encadenar por ID como el resto de los agregados**: se
  descartó porque esta fase excluye explícitamente tocar el wire JSON de
  endpoints existentes, y estos dos DTOs ya viajan anidados en respuestas
  HTTP reales (`RecurringEventResponseDto.subject.studyPlan.specialty`,
  `RecurringEventResponseDto.commission.academicPeriod`) devueltas por
  `allocation`. Encadenar por clave natural logra lo mismo sin ese efecto
  colateral.
- **Crear un DTO "interno" distinto del DTO de respuesta público solo para
  el resultado de `findOrCreate`**: se descartó por duplicar tipos sin
  necesidad — el DTO de respuesta ya existente es información pública
  válida para cualquier consumidor, incluido `excelimport`; solo
  `SubjectCommission` carecía de uno.
- **Mantener las entidades como API pero agregar un test de arquitectura
  ad hoc (ArchUnit) que prohíba navegarlas desde otros módulos**: se
  descartó porque Spring Modulith ya ofrece ese mecanismo de forma nativa
  (`@NamedInterface`) — agregar una segunda herramienta para reforzar la
  misma regla es redundante y es más fácil que quede desactualizada.
- **Interfaz interna separada (`BuildingLookupService` no anotada) para que
  `ClassroomServiceImpl` resuelva `Building`**: se descartó a favor de
  inyectar `BuildingRepository` directamente — ambas clases ya conviven en
  el mismo módulo y capa (`space.service.impl`), y la interfaz extra no
  aportaba ninguna sustitución/mock real que no diera ya el repositorio.
