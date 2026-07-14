# ADR-002: MapStruct + patrón composer para mappers

## Estado

Aceptado — 2026-07-10

## Contexto

Antes de esta decisión el proyecto tenía mappers manuales escritos a mano,
uno por agregado (`SpecialtyMapper`, `StudyPlanMapper`, `AcademicPeriodMapper`,
`SubjectMapper`, `CommissionMapper`, `ClassroomMapper`, `AcademicEventMapper`),
más mapeo inline duplicado directamente en servicios y composers (el edificio
en `BuildingServiceImpl.findAll()`, la ocurrencia en `AllocationMapper` y en
`AcademicEventServiceImpl.findOccurrencesByEventId()`). Esto generaba:

- Boilerplate repetitivo (cada mapper manual repite el mismo patrón:
  `if (x == null) return null;` + `builder()...build()`).
- Cuatro convenciones de nombre distintas para el método de mapeo
  (`toDto`, `toResponseDto`, `map`, `compose`).
- Mapeo inline duplicado fuera de los mappers, sin un lugar único donde
  buscarlo.

Al mismo tiempo, `allocation` ya tenía un patrón limpio para resolver datos
de otros módulos sin acoplar entidades JPA: `AcademicEventComposer` resuelve
`Subject`/`Commission` vía las fachadas `SubjectService`/`CommissionService`
(por lote, evitando N+1) y se los pasa a `AcademicEventMapper`, que es un
mapper puro sobre la entidad propia. `AllocationMapper`, en cambio, mezclaba
ambas responsabilidades en una sola clase: mapeo de campos propios de
`Allocation` y resolución de datos ajenos (evento académico compuesto, aula).

## Decisión

Se adopta MapStruct para todos los mappers bean-a-bean del proyecto
(`ExcelRowMapper` queda manual porque parsea celdas de Apache POI, no hace
mapeo bean-a-bean), con una convención única `toDto` / `toEntity` /
`updateEntity`, y una configuración central compartida:

- `common/mapper/CentralMapperConfig.java` — `@MapperConfig(componentModel =
  SPRING, unmappedTargetPolicy = ERROR)`. Todos los `@Mapper` del proyecto lo
  referencian vía `@Mapper(config = CentralMapperConfig.class)`. `common` es
  el único módulo OPEN, así que cualquier módulo puede usarlo sin declarar
  `allowedDependencies`. La policy `ERROR` obliga a decidir conscientemente
  (con `@Mapping(target = "x", ignore = true)` o un `@Mapping` explícito) qué
  pasa con cada propiedad del target, en vez de dejar huecos silenciosos.
- Mappers nuevos por agregado y por módulo: `BuildingMapper` (space) y
  `OccurrenceMapper` (allocation) reemplazan mapeo que antes vivía inline y
  duplicado.
- Donde un mapeo necesita lógica que no es bean-a-bean directo (`durationMinutes`
  a partir de un `Duration`, la constante `type` de cada subtipo de evento, el
  despacho polimórfico de `AcademicEvent` a `RecurringEvent`/`UniqueEvent`
  desproxyando Hibernate), se resuelve con `@Mapping(expression = "java(...)")`,
  `@Mapping(target = "x", constant = "...")`, o un método `default` en la
  interfaz del mapper — nunca con un mapper manual completo.
- Patrón **mapper puro + composer** para resolver datos de otro módulo, ya
  usado por `AcademicEventComposer`/`AcademicEventMapper`, ahora también en
  `allocation`:
  - `AllocationMapper` (MapStruct puro) solo mapea los campos propios de
    `Allocation` — incluida la ocurrencia, que es intra-módulo, vía
    `OccurrenceMapper` — y recibe como parámetros aparte el evento académico
    ya compuesto (`AcademicEventResponseDto`) y el aula ya mapeada
    (`ClassroomResponseDto`).
  - `AllocationComposer` (nuevo, reemplaza el rol público que tenía
    `AllocationMapper`) resuelve esos datos ajenos — evento vía
    `AcademicEventComposer`, aula vía `ClassroomMapper` — y delega el mapeo
    final en `AllocationMapper`. Los servicios de `allocation` ahora dependen
    de `AllocationComposer`, no de `AllocationMapper` directamente.

### Gotcha de MapStruct con parámetros múltiples

Cuando un método de mapeo recibe varios parámetros de origen y la entidad
principal tiene una propiedad JPA con el mismo nombre que uno de esos
parámetros (p. ej. `RecurringEvent.getSubject()` y el parámetro
`SubjectResponseDto subject`; `Allocation.getClassroom()` y el parámetro
`ClassroomResponseDto classroom`), MapStruct **prefiere silenciosamente** la
navegación a la propiedad anidada de la entidad por sobre el parámetro ya
resuelto — sin marcarlo como ambiguo, a diferencia de cuando dos o más rutas
anidadas compiten por el mismo tipo de destino (eso sí lo marca como error:
"Several possible source properties"). Si no se corrige, esto genera un
mapper que ignora por completo el DTO compuesto por el composer y en su
lugar navega la relación `@ManyToOne` hacia la entidad de otro módulo,
reintroduciendo exactamente el acoplamiento cross-módulo que el patrón
composer busca evitar.

La corrección es forzar explícitamente el mapeo del parámetro completo con
`@Mapping(target = "subject", source = "subject")` (el nombre del parámetro
como `source`, sin punto, indica "usar este parámetro entero"). Se aplicó en
`AcademicEventMapper` (`subject`, `commission`) y en `AllocationMapper`
(`event`, `classroom`), con un comentario explicando el motivo para que no se
borre por parecer redundante.

## Consecuencias

- Menos código de mapeo escrito a mano y una única convención de nombres.
- El compilador (vía el procesador de anotaciones de MapStruct) rompe el
  build si un campo del DTO queda sin mapear, en vez de fallar en runtime con
  un campo `null` inesperado.
- Nuevo orden obligatorio en `annotationProcessorPaths` del
  `maven-compiler-plugin`: Lombok, luego `lombok-mapstruct-binding`, luego
  `mapstruct-processor` — necesario para que ambos procesadores de anotaciones
  cooperen sobre las mismas clases (Lombok genera getters/builders que
  MapStruct necesita ver).
- El patrón mapper-puro + composer para datos ajenos ya no es exclusivo de
  `AcademicEventComposer`; queda como convención repetible para cualquier
  agregado nuevo que necesite componer datos de otro módulo.
- El gotcha de parámetros múltiples con nombres de propiedad colisionantes
  queda documentado acá para no repetirlo sin darse cuenta en mappers
  futuros con la misma forma.

## Alternativas consideradas

- **Seguir con mappers manuales, solo unificando la convención de nombres**:
  se descartó porque no resuelve el boilerplate ni da una verificación en
  tiempo de compilación de que todos los campos del DTO están mapeados.
- **Reconstruir `AllocationResponseDto` a mano dentro de `AllocationComposer`**
  (sin pasar `event`/`classroom` como parámetros a un mapper MapStruct): se
  descartó por inconsistencia con el patrón ya establecido en
  `AcademicEventMapper`/`AcademicEventComposer`, que resuelve exactamente el
  mismo problema (datos ajenos) pasándolos como parámetros adicionales al
  mapper puro.
- **Relajar `unmappedTargetPolicy` a `WARN` en vez de `ERROR`**: se descartó
  porque el objetivo es justamente que un campo no mapeado rompa el build,
  no que aparezca como ruido en el log de compilación.
