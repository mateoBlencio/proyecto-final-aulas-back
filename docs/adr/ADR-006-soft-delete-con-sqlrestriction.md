# ADR-006: Soft-delete declarativo con @SQLRestriction

## Estado

Aceptado — 2026-07-10

## Contexto

SIGA no elimina físicamente ciertas filas: `Subject`, `Specialty`,
`StudyPlan`, `Commission`, `SubjectCommission` (`academic`) y `Classroom`,
`Building`, `ClassroomType` (`space`) tienen una columna `eliminado`
(`Boolean deleted`) que los flujos de borrado ponen en `true` en vez de
hacer `DELETE`. Hasta esta decisión, ese filtro se aplicaba a mano:

- Un finder derivado por nombre con sufijo `...AndDeletedFalse` por cada
  consulta que necesitaba excluir borrados (`findByIdAndDeletedFalse`,
  `findByRoomNumberAndDeletedFalse`, `findAllByDeletedFalse`, etc.) — ocho
  repositorios, cada uno repitiendo el mismo condicional.
- Un predicado manual `cb.isFalse(root.get("deleted"))` agregado a mano en
  `ClassroomSpecification.withFilter()`.

Esto era frágil de una forma muy concreta: **cualquier consulta que use el
finder heredado sin sufijo** (`findById` plano, `findAll()`, o cualquier
finder derivado nuevo al que alguien se olvide de agregarle
`AndDeletedFalse`) **devuelve también las filas borradas**, sin que el
compilador ni un test de arquitectura lo detecten — depende enteramente de
que quien escribe la consulta se acuerde de agregar el sufijo.

`AcademicPeriod` no tiene este problema porque no usa soft-delete: su columna
análoga es `activo` (`Boolean active`), con semántica distinta (un período
puede reactivarse) y sin flujo de borrado asociado.

## Decisión

Se anota `@SQLRestriction("eliminado = false")` (Hibernate, no JPA estándar)
en las 8 entidades con soft-delete: `Subject`, `Specialty`, `StudyPlan`,
`Commission`, `SubjectCommission`, `Classroom`, `Building`, `ClassroomType`.
`AcademicPeriod` queda sin tocar.

Hibernate agrega esa condición a **todo** `SELECT` que la entidad genere —
`findById`, `findAll`, `findAllById`, cualquier finder derivado o
`Specification` — sin que cada consulta tenga que declararlo. Como
consecuencia:

- Se eliminan los 8 métodos custom `...AndDeletedFalse` /
  `findAllByDeletedFalse`: el mismo resultado se obtiene con el finder
  heredado de `JpaRepository` (`findById`, `findAll`) o con el finder
  derivado sin el sufijo (`findByRoomNumber` en vez de
  `findByRoomNumberAndDeletedFalse`, etc.).
- Se elimina el predicado manual `cb.isFalse(root.get("deleted"))` de
  `ClassroomSpecification.withFilter()`: la restricción ya la aplica la
  entidad, agregarla también en la `Specification` sería redundante.
- Los flujos de borrado (`entity.setDeleted(true); repository.save(entity)`)
  no cambian: siguen escribiendo `eliminado = true` normalmente: la
  restricción actúa solo en lectura.

## Consecuencias

- **Imposible de omitir por accidente**: no existe un camino de lectura que
  devuelva una fila borrada sin pasar explícitamente por SQL nativo — ni
  siquiera `findById`, que antes exigía acordarse del sufijo
  `AndDeletedFalse` para excluir borrados.
- **Limitación conocida y aceptada**: los registros borrados quedan
  invisibles también para lookups históricos por ID. No hay forma, con
  `@SQLRestriction`, de pedir "dame esta fila aunque esté borrada" sin una
  consulta nativa que bypasee la entidad — es el comportamiento deseado hoy
  (decisión B6 del plan de refactor: soft-delete sirve para ocultar, no para
  versionar), pero si en el futuro se necesita un endpoint de "ver
  eliminados" (auditoría, recuperación), va a requerir una consulta nativa o
  `@Filter` (activable/desactivable) en vez de `@SQLRestriction` (siempre
  activo).
- **Relaciones `LAZY` hacia un registro borrado fallan al inicializarse**:
  si una fila viva referencia (por FK) a una fila que después se soft-borró
  del lado `@SQLRestriction` (p. ej. una `Commission` viva cuyo
  `AcademicPeriod`... no aplica acá porque `AcademicPeriod` no tiene
  restricción; pero sí aplica, por ejemplo, a un `SubjectCommission` vivo
  cuyo `Subject` fue borrado), Hibernate no encuentra la fila al resolver el
  proxy `LAZY` y lanza `EntityNotFoundException` en vez de devolver la
  entidad borrada silenciosamente. Se acepta como comportamiento correcto:
  navegar a un dato borrado debe fallar visiblemente, no devolver datos
  inconsistentes.
- Los 8 finders renombrados (`findByRoomNumber`, `findByName`,
  `findByDescriptionIgnoreCase`, `findByCodeAndStudyPlan`,
  `findBySpecialtyCode`, `findByPlanCodeAndSpecialty`,
  `findByCourseCodeAndCommissionNumberAndAcademicPeriod`,
  `findBySubjectAndCommission`) generan el mismo SQL que antes (Hibernate
  agrega la condición de `@SQLRestriction` al `WHERE` generado por Spring
  Data), solo que ahora el nombre del método no la menciona porque no hace
  falta.

## Alternativas consideradas

- **`@Filter` de Hibernate (activable/desactivable por sesión)** en vez de
  `@SQLRestriction` (siempre activo): se descartó porque hoy no existe
  ningún flujo que necesite ver registros borrados — agregar un filtro
  desactivable es complejidad sin un consumidor real, y `@SQLRestriction` es
  más simple de razonar (siempre aplica, sin estado de sesión que gestionar).
  Si aparece esa necesidad, migrar de `@SQLRestriction` a `@Filter` es un
  cambio acotado a la entidad puntual que lo requiera.
- **Mantener los finders `...AndDeletedFalse` además de `@SQLRestriction`**:
  se descartó por ser filtrado duplicado (`WHERE eliminado = false AND
  eliminado = false`) sin ningún beneficio — la restricción de la entidad ya
  cubre el caso.
- **Borrado físico (`DELETE`) en vez de soft-delete**: fuera de alcance de
  esta decisión; el esquema y los flujos de negocio ya asumen soft-delete
  desde antes de este refactor, y cambiarlo implica decisiones de producto
  (¿se puede recuperar un aula borrada?) ajenas a esta fase.
