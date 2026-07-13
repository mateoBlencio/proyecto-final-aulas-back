# SIGA — Sistema Inteligente de Gestión Áulica (backend)

Gestión y asignación de aulas para eventos académicos de la UTN FRC: asignación
manual, importación masiva desde Excel y asignación automática mediante un solver
de optimización (Timefold).

**Stack**: Spring Boot 4 · Java 21 · PostgreSQL · Maven · Spring Modulith ·
Timefold Solver 2.2 · Apache POI · Hibernate Envers.

## Arquitectura: monolito modular

Un solo deployable y una sola base de datos, pero módulos con fronteras vigiladas
por Spring Modulith: `ModularityTests.verifyBoundaries()` rompe el build si un
módulo accede a internals de otro. Lo público de cada módulo se marca con
`@NamedInterface("api")`; la comunicación entre módulos es por ID plano + DTO a
través de esa interfaz, nunca compartiendo entidades JPA.

| Módulo | Depende de | Responsabilidad |
|---|---|---|
| `space` | `common` | Aulas, edificios, tipos de aula |
| `academic` | `common` | Materias, comisiones, planes, períodos |
| `allocation` | `space::api`, `academic::api`, `solver::api`, `common` | Eventos académicos (recurrentes/únicos), ocurrencias, asignaciones de aula |
| `solver` | `common` | Asignación automática con Timefold Solver |
| `excelimport` | `academic::api`, `space::api`, `allocation::api`, `common` | Importación masiva desde Excel |
| `common` | (OPEN) | Config, excepciones globales, converters |

Documentación de detalle:

- [docs/modulos/](docs/modulos/) — ficha técnica por módulo: responsabilidad, API pública, invariantes, gaps y testing.
- [docs/adr/](docs/adr/) — decisiones arquitectónicas (manejo de errores, MapStruct/composers, DTOs como records, fronteras de módulo, OSIV off, soft-delete, auditoría Envers).
- [docs/modelo-dominio.md](docs/modelo-dominio.md) — modelo Evento → Ocurrencia → Asignación.

## Setup

Requisitos: JDK 21 (el wrapper `./mvnw` trae Maven). Docker solo para el perfil
`dev-local` y los tests de integración.

```bash
# Compilar
./mvnw compile

# Desarrollo habitual: Postgres propio + variables de entorno (ver .env.example)
# Requiere: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Alternativa autocontenida: Postgres efímero vía Docker Compose
SPRING_PROFILES_ACTIVE=dev-local ./mvnw spring-boot:run
```

| Perfil | Uso |
|---|---|
| `dev` | Desarrollo habitual: Postgres propio, credenciales por env vars |
| `dev-local` | Postgres efímero por Docker Compose, esquema autogenerado + `data.sql` |
| `test` | Servidor de pruebas desplegado |
| `integration` | Solo tests: Testcontainers (`src/test/resources/application-integration.yaml`) |
| `prod` | Producción |

## API

- Context-path `/api`, endpoints bajo `/v1` (p. ej. `GET /api/v1/buildings`).
- Swagger UI habilitado solo en dev/dev-local/test: `/api/swagger-ui.html`.
- Colección [Bruno](https://www.usebruno.com/) en [bruno/](bruno/) (entorno `local`).
  Al agregar o cambiar un endpoint, actualizar la colección.

## Testing

```bash
# Toda la suite (unitarios + integración; integración requiere Docker corriendo)
./mvnw test

# Un solo test / un solo método
./mvnw test -Dtest=SolverServiceImplTest
./mvnw test -Dtest=SolverServiceImplTest#nombreDelMetodo

# Verificar fronteras entre módulos (correr SIEMPRE tras cambios estructurales)
./mvnw test -Dtest=ModularityTests
```

- **Unitarios**: AssertJ + Mockito, sin contexto Spring. Las constraints del solver
  se verifican con `ConstraintVerifier` de Timefold.
- **Integración** (sufijo `*IntegrationTest`): `@SpringBootTest` + MockMvc contra
  Postgres real vía Testcontainers (URL `jdbc:tc:...`, contenedor singleton por
  JVM). **Sin Docker se saltean limpiamente** (`@Testcontainers(disabledWithoutDocker = true)`);
  no usan `@Transactional` porque la auditoría Envers necesita commits reales.
- CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) corre
  `./mvnw test -Dspring.profiles.active=integration` en push a
  `feature/**`/`hotfix/**` y PRs a `develop`/`main`.

## Base de datos

- **No hay Flyway/Liquibase**: el esquema lo administra un DBA externo. La app corre
  con `ddl-auto: validate` — si las entidades no coinciden con el esquema real, no levanta.
- Cambio de esquema → agregarlo a mano a [scripts/sql/ddl.sql](.claude/sql/sql/ddl.sql)
  (DDL pendiente consolidado), entregarlo al DBA, y mapear en las entidades recién
  cuando esté aplicado.
- Excepciones: `dev-local` y los tests de integración usan `create-drop`.

## Convenciones

- Commits: [Conventional Commits](https://www.conventionalcommits.org/) **en español**
  — `fix(solver): ...`, `test(allocation): ...`. Branches `feature/**` / `hotfix/**`;
  PRs contra `develop`/`main`.
- Código en inglés (nombres de clases/métodos); comentarios, mensajes y
  documentación en español.
- Lombok en todo el proyecto; DTOs como records; MapStruct con `componentModel = SPRING`.
- Zona horaria fija UTC (plugin de Boot y Surefire corren con `-Duser.timezone=UTC`).
