# Convenciones técnicas del backend

Los agentes de `.claude/agents/` leen este archivo al empezar. No es de
negocio: las reglas de negocio las responde el subagente `business-rules`.

## Idioma

Identificadores y mensajes de código en inglés. En el dominio se usa
"allocation", nunca "assignation".

## Comentarios

Sin comentarios: el código se entiende leyéndolo. Si algo necesita
explicación, renombrá, extraé un método con nombre descriptivo o expresalo
en un tipo. Excepciones: licencias, contrato de API pública (Javadoc),
comportamiento impuesto por una dependencia externa que no se puede cambiar
y los marcadores `ponytail:` de atajos deliberados.
Nunca comentarios que narran, banners, código comentado ni justificaciones de
workarounds. Limpieza de existentes: skill `no-comments`.

## Stack

Versiones en `pom.xml`. Spring Boot, Java, PostgreSQL, Maven, Spring
Modulith, Timefold Solver, Apache POI, Hibernate Envers, Spring Security
(JWT), Bucket4j. Lombok en todo el proyecto, DTOs como `record`, MapStruct
con `componentModel = SPRING`. Zona horaria UTC.

## Módulos

Monolito modular: un deployable, una base. Las dependencias permitidas de
cada módulo están en su `package-info.java` (`@ApplicationModule(
allowedDependencies = ...)`); esa es la fuente de verdad, no una tabla copiada
acá. Para verlas: `grep -rh allowedDependencies src/main/java --include=package-info.java`.

- Lo público de un módulo se expone solo con tipos `@NamedInterface("api")`.
- Entre módulos se pasa ID plano o DTO. Nunca entidades JPA.
- `allocation` publica `ClassroomMovements` (registro de publicación
  persistido, entrega at-least-once) y `buildingactivity` lo consume con
  `@ApplicationModuleListener`.
- Si un cambio toca paquetes o dependencias entre módulos:
  `./mvnw test -Dtest=ModularityTests`.
- Dudas de diseño de módulos o eventos: skill `spring-boot-modulith`.

## Autorización

Chequeos con permisos `PERM_*` vía `RoleAssignment`, no `hasRole(...)`. Todo
cambio de autorización queda auditado como el resto de `auth` (etiqueta de
dominio en el registro de auditoría). Qué significa cada permiso en términos
de negocio: preguntar a `business-rules`.

## Base de datos

Cambio de esquema: migración Flyway nueva (reglas en `CLAUDE.md`). Una
migración mergeada no se edita.

## Endpoints

Al agregar o cambiar un endpoint, actualizar la colección Bruno en `bruno/`.

## Tests

- Unitarios: AssertJ y Mockito, sin contexto Spring. Constraints del
  optimizer con `ConstraintVerifier` de Timefold.
- Integración (`*IntegrationTest`): `@SpringBootTest` y MockMvc contra
  Postgres real con Testcontainers, sin `@Transactional` (Envers necesita
  commits reales). Sin Docker se saltean.
- Antes de un test nuevo, leer uno existente del mismo módulo y seguir su
  estilo.

```bash
./mvnw test
./mvnw test -Dtest=NombreDelTest
./mvnw test -Dtest=NombreDelTest#metodo
./mvnw test -Dtest=ModularityTests
```

Medición de calidad de tests (PIT, JaCoCo): skill `audit-standards`,
`references/tooling.md`.

## Repositorio

Remote `origin`, ramas base `develop` y `main`. `gh` autenticado. Los
commits y PR siguen `CLAUDE.md`.

## Preguntas al usuario (subagentes)

Un subagente no tiene `AskUserQuestion`. Cuando un agente debe preguntar,
avanza hasta donde puede sin asumir y termina con una sección `Preguntas
para el usuario`: cada pregunta, las opciones y qué cambia con cada una. El
hilo principal pregunta y vuelve a invocar al agente con la respuesta.
