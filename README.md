# SIGA — Sistema Inteligente de Gestión Áulica (backend)

Gestión y asignación de aulas para eventos académicos de la UTN FRC: asignación
manual, importación masiva desde Excel y asignación automática mediante un solver
de optimización (Timefold), con revisión previa a confirmar.

**Stack**: Spring Boot 4 · Java 21 · PostgreSQL · Maven · Spring Modulith ·
Timefold Solver 2.2 · Apache POI · Hibernate Envers · Spring Security (JWT) · Bucket4j.

## Arquitectura: monolito modular

Un solo deployable y una sola base de datos, pero módulos con fronteras vigiladas
por Spring Modulith: `ModularityTests.verifyBoundaries()` rompe el build si un
módulo accede a internals de otro. Lo público de cada módulo se marca con
`@NamedInterface("api")`; la comunicación entre módulos es por ID plano + DTO a
través de esa interfaz, nunca compartiendo entidades JPA. `events` y `allocation`
además se comunican de forma asíncrona: `events` publica `OccurrenceVacated`
(registro de publicación persistido, entrega al menos una vez) y `allocation` lo
escucha vía `@ApplicationModuleListener` para desasignar automáticamente.

| Módulo | Depende de | Responsabilidad |
|---|---|---|
| `common` | (OPEN) | Manejo de errores centralizado, resultado uniforme "buscar o crear", auditoría (Envers), config de mapeo/CORS/API |
| `auth` | `common` | Autenticación (JWT), usuarios, rate limiting de login |
| `space` | `common` | Aulas, edificios, tipos de aula |
| `academic` | `common` | Especialidades, planes, materias, períodos académicos, comisiones |
| `events` | `academic::api`, `common` | Eventos académicos (recurrentes/únicos) y sus ocurrencias |
| `optimizer` | `common` | Motor de asignación automática puro (Timefold), sin persistencia ni conocimiento de otros módulos |
| `allocation` | `events::api`, `space::api`, `academic::api`, `common` | Asignación de aula (alta/reasignación/baja en lote), detección de conflictos |
| `preview` | `allocation::api`, `optimizer::api`, `events::api`, `space::api`, `academic::api`, `common` | Orquesta el flujo de asignación automática: arma pedido al optimizer, guarda vista previa revisable, confirma atómicamente contra `allocation` |
| `ingest` | `academic::api`, `space::api`, `allocation::api`, `events::api`, `common` | Importación masiva desde Excel (carga académica y de asignación) |

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
- Autenticación JWT: `POST /auth/login`, `/auth/refresh`, `/auth/logout`.
- Colección [Bruno](https://www.usebruno.com/) en [bruno/](bruno/) (entorno `local`).
  Al agregar o cambiar un endpoint, actualizar la colección.

## Testing

```bash
# Toda la suite (unitarios + integración; integración requiere Docker corriendo)
./mvnw test

# Un solo test / un solo método
./mvnw test -Dtest=NombreDelTest
./mvnw test -Dtest=NombreDelTest#nombreDelMetodo

# Verificar fronteras entre módulos (correr SIEMPRE tras cambios estructurales)
./mvnw test -Dtest=ModularityTests
```

- **Unitarios**: AssertJ + Mockito, sin contexto Spring. Las constraints del
  optimizer se verifican con `ConstraintVerifier` de Timefold.
- **Integración** (sufijo `*IntegrationTest`): `@SpringBootTest` + MockMvc contra
  Postgres real vía Testcontainers (URL `jdbc:tc:...`, contenedor singleton por
  JVM). **Sin Docker se saltean limpiamente** (`@Testcontainers(disabledWithoutDocker = true)`);
  no usan `@Transactional` porque la auditoría Envers necesita commits reales.
- CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) corre
  `./mvnw test -Dspring.profiles.active=integration` en push a
  `feature/**`/`hotfix/**` y PRs a `develop`/`main`.

## Convenciones

- Commits: [Conventional Commits](https://www.conventionalcommits.org/) **en español**
  — `fix(allocation): ...`, `test(ingest): ...`. Branches `feature/**` / `hotfix/**`;
  PRs contra `develop`/`main`.
- Código en inglés (nombres de clases/métodos); comentarios, mensajes y
  documentación en español.
- Lombok en todo el proyecto; DTOs como records; MapStruct con `componentModel = SPRING`.
- Zona horaria fija UTC (plugin de Boot y Surefire corren con `-Duser.timezone=UTC`).
