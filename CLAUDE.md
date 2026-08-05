# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es

SIGA — Sistema Inteligente de Gestión Áulica (backend). Gestión y asignación (manual y automática vía solver) de aulas para eventos académicos de la UTN FRC. Spring Boot 4, Java 21, PostgreSQL, Maven.

## Comandos

```bash
./mvnw compile

# Perfil dev: variables DB_* como env vars, sin Docker (ver .env.example)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Perfil dev-local: Postgres efímero vía Docker Compose (create-drop + data.sql)
SPRING_PROFILES_ACTIVE=dev-local ./mvnw spring-boot:run

./mvnw test                                          # toda la suite (Testcontainers, requiere Docker)
./mvnw test -Dtest=SolverServiceImplTest             # un solo test
./mvnw test -Dtest=SolverServiceImplTest#metodo      # un solo método
./mvnw test -Dtest=ModularityTests                   # fronteras entre módulos, correr tras cambios estructurales
```

- La API cuelga de context-path `/api`. Swagger UI habilitado solo en dev/dev-local/test (`/api/swagger-ui.html`).
- CI (`.github/workflows/ci.yml`) corre `./mvnw test -Dspring.profiles.active=integration` en push a `feature/**`/`hotfix/**` y PRs a `develop`/`main`.
- Colección Bruno en `bruno/` (entorno `local`). **Al agregar o cambiar un endpoint, actualizar la colección.**

## Arquitectura: monolito modular (Spring Modulith)

Un solo deployable, una sola BD, pero módulos con fronteras vigiladas. `ModularityTests.verifyBoundaries()` **rompe el build** si un módulo accede a internals de otro.

Módulos (paquetes de primer nivel bajo `ar.edu.utn.frc.siga`) y sus dependencias declaradas en cada `package-info.java`: `space`→common (aulas, edificios, tipos de aula), `academic`→common (materias, comisiones, planes, períodos), `events`→academic::api+common (eventos académicos recurrentes/únicos y sus ocurrencias, sin conocer aulas), `allocation`→events::api+space::api+academic::api+solver::api+common (asignaciones de aula, problemas de asignación, auto-asignación), `solver`→common (asignación automática con Timefold Solver), `excelimport`→academic::api+space::api+allocation::api+events::api+common (importación masiva desde Excel), `auth`→common (autenticación y usuarios), `common`→OPEN (config, excepciones globales, converters).

Reglas del paradigma:
- Lo público de un módulo se marca con `@NamedInterface("api")`; el consumidor lo declara en `allowedDependencies` de su `package-info.java`. Todo lo demás es privado.
- **Comunicación entre módulos: por ID + DTO a través de la interfaz pública, nunca compartiendo entidades JPA.** Refactor en curso (ver `.claude/plans/plan-refactor.md`) para eliminar acoplamiento histórico; **código nuevo no debe agregar deuda de este tipo**. `solver` es el ejemplo limpio: define sus propios records (`SolverEvent`, `SolverRoom`, `SolverOccupancy`, `SolverPreview`, en `solver/model`, no entidades JPA) y `allocation` le mapea los datos.
- Dentro de cada módulo la estructura es por capas: `controller` / `dto` / `mapper` / `model` / `repository` / `service` (interfaz) / `service/impl`. Config del solver en `siga.solver.*` de `application.yaml`.

## Base de datos: esquema externo, sin migraciones

- **No hay Flyway/Liquibase.** El esquema lo administra una persona externa (DBA). La app corre con `ddl-auto: validate`: si las entidades no coinciden con el esquema real, **no levanta**.
- Cambio de esquema necesario → agregarlo a mano a `scripts/sql/ddl.sql` (DDL pendiente consolidado), entregarlo al DBA, y mapear en las entidades **recién cuando esté aplicado**.
- Excepciones: perfil `dev-local` usa `create-drop` + `data.sql`; los tests de integración usan Testcontainers (`jdbc:tc:postgresql:16-alpine`) con `create-drop`.
- Seeds útiles en `scripts/sql/`.

## Perfiles

`dev` (Postgres propio, credenciales por env vars) · `dev-local` (Postgres efímero por Docker Compose, esquema autogenerado) · `test` (servidor de pruebas desplegado) · `integration` (solo tests, Testcontainers) · `prod` (producción).

## Convenciones

- Commits: **Conventional Commits en español** — `fix(solver): ...`, `perf(solver): ...`, `refactor: ...`. Branches: `feature/**`, `hotfix/**`; PRs contra `develop`/`main`.
- Código y comentarios en el estilo existente: nombres de clases en inglés, comentarios y mensajes en español.
- Lombok en todo el proyecto.
- Zona horaria fija UTC (`-Duser.timezone=UTC` en el plugin de Boot).
- Documentación en `docs/`: `adr/`, `modelo-dominio.md`, `modulos/`, `restricciones-asignacion.md`, `calendario-academico-2026.md`. Contexto vivo de trabajo en curso (no versionado) en `.claude/plans/`.
