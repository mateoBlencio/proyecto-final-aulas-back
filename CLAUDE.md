# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es

SIGA — Sistema Inteligente de Gestión Áulica (backend). Gestión y asignación (manual y automática vía solver) de aulas para eventos académicos de la UTN FRC. Spring Boot 4, Java 21, PostgreSQL, Maven.

## Comandos

```bash
# Compilar
./mvnw compile

# Correr la app (desarrollo habitual: perfil dev + variables DB_* como env vars, sin Docker)
# Variables requeridas: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD (ver .env.example)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Alternativa autocontenida: Postgres efímero vía Docker Compose (create-drop + data.sql)
SPRING_PROFILES_ACTIVE=dev-local ./mvnw spring-boot:run

# Toda la suite (los tests de integración usan Testcontainers → requieren Docker corriendo)
./mvnw test

# Un solo test / un solo método
./mvnw test -Dtest=SolverServiceImplTest
./mvnw test -Dtest=SolverServiceImplTest#nombreDelMetodo

# Verificar fronteras entre módulos (correr SIEMPRE tras cambios estructurales)
./mvnw test -Dtest=ModularityTests
```

- La API cuelga de context-path `/api`. Swagger UI habilitado solo en dev/dev-local/test (`/api/swagger-ui.html`).
- CI (`.github/workflows/ci.yml`) corre `./mvnw test -Dspring.profiles.active=integration` en push a `feature/**`/`hotfix/**` y PRs a `develop`/`main`.
- Colección Bruno en `bruno/` (entorno `local`). **Al agregar o cambiar un endpoint, actualizar la colección.**

## Arquitectura: monolito modular (Spring Modulith)

Un solo deployable, una sola BD, pero módulos con fronteras vigiladas. `ModularityTests.verifyBoundaries()` **rompe el build** si un módulo accede a internals de otro.

Módulos (paquetes de primer nivel bajo `ar.edu.utn.frc.siga`) y sus dependencias declaradas en cada `package-info.java`:

| Módulo | Depende de | Responsabilidad |
|---|---|---|
| `space` | `common` | Aulas, edificios, tipos de aula |
| `academic` | `common` | Materias, comisiones, planes, períodos |
| `allocation` | `space::api`, `academic::api`, `common` | Eventos académicos (recurrentes/únicos), ocurrencias, asignaciones de aula |
| `solver` | `common` | Asignación automática con Timefold Solver |
| `excelimport` | `academic::api`, `space::api`, `allocation::api`, `common` | Importación masiva desde Excel (Apache POI) |
| `common` | (OPEN) | Config, excepciones globales, converters |

Reglas del paradigma:
- Lo público de un módulo se marca con `@NamedInterface("api")`; el consumidor lo declara en `allowedDependencies` de su `package-info.java`. Todo lo demás es privado.
- **Comunicación entre módulos: por ID + DTO a través de la interfaz pública, nunca compartiendo entidades JPA.** Refactor en curso (ver `.claude/plans/plan-refactor.md`) para eliminar el acoplamiento histórico (`allocation` mapeaba `Classroom` y `Commission` como `@ManyToOne` de otros módulos). **El código nuevo no debe agregar deuda de este tipo**: referenciar por ID plano y pedir datos a la fachada del otro módulo. El módulo `solver` es el ejemplo limpio: define sus propios records de entrada/salida (`SolverEvent`, `SolverRoom`, `SolverOccupancy`, `SolverPreview`) y `allocation` le mapea los datos; no consume entidades ni DTOs de otros módulos.
- Dentro de cada módulo la estructura es por capas: `controller` / `dto` / `mapper` / `model` / `repository` / `service` (interfaz) / `service/impl`.

### Solver (Timefold)

`solver/optimization` contiene el `ConstraintProvider` (restricciones hard/soft) y `solver/model` las entidades de planificación (`SolverEvent`, `SolverRoom`) — son modelos propios del solver, no entidades JPA. Configuración en `siga.solver.*` de `application.yaml` (paralelismo, límite de segundos sin mejora, environment-mode).

## Base de datos: esquema externo, sin migraciones

- **No hay Flyway/Liquibase.** El esquema lo administra una persona externa (DBA). La app corre con `ddl-auto: validate`: si las entidades no coinciden con el esquema real, **no levanta**.
- Cambio de esquema necesario → escribir script DDL a mano en `docs/ddl/` (numerado, ver `docs/ddl/README.md`), entregarlo al DBA, y mapear en las entidades **recién cuando esté aplicado**.
- Excepciones: perfil `dev-local` usa `create-drop` + `data.sql`; los tests de integración usan Testcontainers (`jdbc:tc:postgresql:16-alpine`) con `create-drop`.
- Seeds útiles en `scripts/sql/`.

## Perfiles

| Perfil | Uso |
|---|---|
| `dev` | Desarrollo habitual: Postgres propio, credenciales por env vars |
| `dev-local` | Postgres efímero por Docker Compose, esquema autogenerado |
| `test` | Servidor de pruebas desplegado |
| `integration` | Solo tests (`src/test/resources/application-integration.yaml`, Testcontainers) |
| `prod` | Producción |

## Convenciones

- Commits: **Conventional Commits en español** — `fix(solver): ...`, `perf(solver): ...`, `refactor: ...`. Branches: `feature/**`, `hotfix/**`; PRs contra `develop`/`main`.
- Código y comentarios en el estilo existente: nombres de clases en inglés, comentarios y mensajes en español.
- Lombok en todo el proyecto.
- Zona horaria fija UTC (`-Duser.timezone=UTC` en el plugin de Boot).
- Documentación del dominio y los módulos en `docs/`: `adr/` (decisiones arquitectónicas), `ddl/` (scripts de esquema pendientes/aplicados), `modelo-dominio.md` (modelo Evento/Occurrence/Allocation), `calendario-academico-2026.md` (referencia calendario académico UTN), `para-dba.md`, `para-front.md`.
- Contexto vivo de trabajo en curso (no versionado) en `.claude/plans/`: `plan-refactor.md` (plan activo de este refactor), `asignacion-automatica-preview.md`.
