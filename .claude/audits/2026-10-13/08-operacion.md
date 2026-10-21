# Dimensión 8: Operación, configuración y CI

Commit auditado: 22c693f2. Lectura de `application*.yaml`, `Dockerfile`, `.dockerignore`, `compose*.yaml`, `docker-compose.deploy.yml`, `.github/workflows/*`, `pom.xml`, `scripts/dev-snapshot.sh`, `bruno/environments/*`. Versiones contrastadas contra `maven-metadata.xml` de Maven Central (2026-10-13). Visibilidad del repo y environments consultados con `gh api` (repo público; environments `dev` y `test` sin reglas de protección). La configuración de Actions para PRs de forks devolvió 403.

Lo que quedó bien y no se reporta: `siga.jwt.secret` no tiene default y `JwtProperties` exige 32 bytes; Actuator expone solo `health` con `show-details: never` en el perfil base; Swagger apagado en base y prod; imagen multi-stage con usuario no root y `HEALTHCHECK`; límites de memoria/CPU y `MaxRAMPercentage` en el compose de deploy; `GITHUB_TOKEN` con `contents: read` por defecto; CI corre `./mvnw verify`, que incluye `ModularityTests.verifyBoundaries()`; devtools y docker-compose quedan fuera del jar repackageado (default de `spring-boot-maven-plugin`).

## Hallazgos

### OPS-01 CI de PRs corre en runner self-hosted en repo público
- Severidad: Alta
- Esfuerzo: S
- Referencia: GitHub Actions security hardening ("Hardening for self-hosted runners"), ASVS v5.0.0-13.1 (configuración segura de build/deploy)
- Evidencia: `.github/workflows/ci.yml:8-12` (trigger `pull_request`), `ci.yml:24` (`runs-on: self-hosted`); `cd-dev.yml:62` y `cd-test.yml:62` muestran que ese runner es el servidor de deploy con el `.env` de dev/test. `gh api repos/.../ ` → `visibility: public`.
- Verificación: Plausible (no pude leer la política de aprobación de PRs de forks: 403)
- Problema: Un PR desde un fork ejecuta `./mvnw verify` con `pom.xml`, `mvnw` y tests del atacante en la máquina que guarda los secretos de dev/test (JWT, DB, SMTP, API key de Sysacad) y el socket de Docker. GitHub desaconseja runners self-hosted en repos públicos por esto. El default de GitHub solo pide aprobación a contribuidores primerizos.
- Arreglo: Correr `ci.yml` en `ubuntu-latest` (los tests usan Testcontainers, que funciona en runners hospedados), o como mínimo activar "Require approval for all outside collaborators" en Settings > Actions y separar el runner de CI del host de deploy.

> #### Corrección a la premisa del hallazgo, a verificar y aplicar en el reporte

> 1. El runner self-hosted está dentro de la red Tailscale: para acceder haría falta primero comprometer la cuenta de Google asociada a Tailscale.
> 2. El repositorio NO es público en la práctica: aunque esté alojado en GitHub, el acceso requiere invitación del propietario/administrador del repo.
> 3. El runner de CI y el servidor de deploy son la misma máquina a propósito, por tema de costos (decisión consciente, no descuido).

> **Conclusión**: el vector de ataque "PR desde un fork anónimo llega a secretos de dev/test" no aplica como está descrito, porque no hay acceso público real ni forks de terceros sin invitación. Revisar si OPS-01 baja de severidad o se reformula, y si la visibilidad "public" de GitHub refleja la política real de acceso del equipo.

### OPS-02 Health con detalles y Swagger públicos en dev y test desplegados
- Severidad: Media
- Esfuerzo: S
- Referencia: ASVS v5.0.0-13.4 (exposición de información/endpoints de diagnóstico), Spring Boot Actuator docs (`show-details: when-authorized`)
- Evidencia: `application-dev.yaml:16-25`, `application-test.yaml:20-29` (`swagger-ui.enabled: true`, `show-details: always`); `auth/config/SecurityConfig.java:67-68` (`/actuator/health/**` y `/swagger-ui/**`, `/v3/api-docs/**` con `permitAll`). Verificado en ejecución el 2026-10-14 con dev-snapshot: `GET /api/actuator/health` sin autenticación devuelve el detalle de cada componente, incluida la ruta absoluta del disco (`diskSpace.details.path`). Arreglo: `show-details: when-authorized`.
- Verificación: Confirmado (configuración); Plausible el alcance de red de los servidores
- Problema: Cualquiera que llegue a los backends de dev/test obtiene sin login el mapa completo de la API y el detalle de health (motor y versión de base, espacio en disco, estado del SMTP).
- Arreglo: `management.endpoint.health.show-details: when-authorized` en dev/test, y exigir rol ADMIN para Swagger fuera de `dev-local`/`dev-snapshot` (o apagarlo en `test`).

### OPS-03 Tests en UTC, aplicación en America/Argentina/Buenos_Aires
- Severidad: Media
- Esfuerzo: S
- Referencia: ISO 25010 Reliability/Faultlessness
- Evidencia: `SigaApplication.java:14` fija `TimeZone.setDefault("America/Argentina/Buenos_Aires")` en `main`; `pom.xml:262` corre surefire con `-Duser.timezone=UTC` y `pom.xml:254` pasa UTC a `spring-boot:run` (que `main` después pisa). `@SpringBootTest` no ejecuta `main`. 22 archivos de producción usan `LocalDate/LocalDateTime/LocalTime.now()`.
- Verificación: Confirmado
- Problema: La suite valida el código con una zona horaria distinta a la de producción. Un cálculo de "hoy" o de cron sin `zone` (`AuditArchiveScheduler.java:20`, `RoomRequestExpiryScheduler.java:16`) puede pasar en CI y fallar entre las 21:00 y 24:00 hora argentina. `hibernate.jdbc.time_zone` tampoco está fijado.
- Arreglo: Unificar en un solo lugar: `-Duser.timezone=America/Argentina/Buenos_Aires` en surefire (o `ENV TZ`/`JAVA_TOOL_OPTIONS` en el Dockerfile y quitar el `setDefault`), y declarar `zone` en todos los `@Scheduled` con cron.

### OPS-04 Acciones de GitHub fijadas por tag mutable
- Severidad: Media
- Esfuerzo: S
- Referencia: GitHub Actions security hardening ("Using third-party actions": pin a SHA completo); CVE-2026-33634 (tags de `aquasecurity/trivy-action` reescritos el 19/03/2026)
- Evidencia: `ci.yml:33,35,46,62,76,83,95`; `cd-dev.yml:24,26,37,49,64`; `cd-test.yml` análogo. Terceros: `Wandalen/wretry.action@v3`, `mikepenz/action-junit-report@v6`, `aquasecurity/trivy-action@v0.36.0`.
- Verificación: Confirmado
- Problema: Un tag reescrito en cualquiera de esas acciones corre en el runner self-hosted con acceso al `.env` del servidor (ver OPS-01). La versión de trivy-action en uso es posterior al incidente, pero el mecanismo sigue abierto.
- Arreglo: Fijar las acciones de terceros a SHA completo con el tag como comentario; agregar `.github/dependabot.yml` con ecosistema `github-actions` para mantenerlas.

### OPS-05 Escaneo de imagen que no puede fallar y sin umbral de cobertura
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-15.2 (componentes con vulnerabilidades conocidas); CLAUDE.md "Verificación real"
- Evidencia: `ci.yml:92` (`exit-code: '0'` en Trivy); `pom.xml:266-284` (JaCoCo sin goal `check`); no existe `.github/dependabot.yml` ni Renovate.
- Verificación: Confirmado
- Problema: Trivy sube el SARIF pero nunca corta el build ante una CVE CRITICAL/HIGH con fix disponible, y nadie abre PRs de actualización de dependencias. La cobertura se reporta sin piso.
- Arreglo: `exit-code: '1'` para `CRITICAL` con `ignore-unfixed: true`; Dependabot para `maven` y `github-actions`. El piso de JaCoCo queda como deuda opcional.

### OPS-06 Dependencias con parches pendientes
- Severidad: Baja
- Esfuerzo: S (parches) / M (minors)
- Referencia: ASVS v5.0.0-15.2.1, ISO 25010 Maintainability/Modifiability
- Evidencia: `pom.xml:11,24-28,119,125,269`. Última estable en Maven Central:
  - Spring Boot 4.0.6 → 4.0.8 (parche, S); 4.1.1 existe (minor, M)
  - Spring Modulith 2.0.5 → 2.0.8 (parche, S); 2.1.1 (minor, M, alinear con Boot 4.1)
  - jjwt 0.12.6 → 0.12.7 (parche, S); 0.13.0 (minor)
  - Apache POI 5.4.0 → 5.4.1 (parche, S); 5.5.1 (minor)
  - springdoc 3.0.2 → 3.0.3 (parche, S); 3.1.1 (minor)
  - JaCoCo 0.8.12 → 0.8.15 (S)
  - Bucket4j 8.19.0 → 8.21.0 (minor de bajo riesgo, S)
  - Timefold 2.2.0 → 2.7.1 (cinco minors, M: el solver cambia scores/defaults entre versiones)
  - MapStruct 1.6.3: al día
- Verificación: Confirmado (versiones leídas de `maven-metadata.xml`); el impacto de seguridad de cada salto queda para el OSV-Scanner del hilo principal
- Problema: Los parches de Boot, Modulith, POI y jjwt traen correcciones que el proyecto no recibe.
- Arreglo: Subir en un PR los saltos de parche (Boot 4.0.8, Modulith 2.0.8, jjwt 0.12.7, POI 5.4.1, springdoc 3.0.3, JaCoCo 0.8.15) y correr la suite. Boot 4.1/Modulith 2.1 y Timefold 2.7 van como deuda.

### OPS-07 IP de Tailscale y ruta del servidor en un repo público
- Severidad: Baja
- Esfuerzo: S
- Referencia: Regla del equipo (repo público, sin IPs); ASVS v5.0.0-13.4
- Evidencia: `scripts/dev-snapshot.sh:10` (`ADMIN_HOST` con IP de Tailscale y puerto de Postgres de dev), `bruno/environments/dev.bru:2`, `bruno/environments/test.bru:2` (misma IP con puertos 8090/8091); `cd-dev.yml:62`, `cd-test.yml:62` (ruta absoluta con el usuario del servidor).
- Verificación: Confirmado (`git grep` sobre HEAD)
- Problema: La IP pertenece al rango CGNAT de Tailscale y no es ruteable desde Internet, pero el repo publica la topología (host de la base de dev, puertos de los backends, usuario de sistema) que la regla del equipo pide no versionar.
- Arreglo: Leer `ADMIN_HOST` y `baseUrl` de `.env`/`bruno/.env` (ya ignorados) y la ruta del `.env` de un secret o variable del environment. La IP queda en el historial; si importa, rotarla en Tailscale.

### OPS-08 MailHog de test publicado en todas las interfaces del servidor
- Severidad: Baja
- Esfuerzo: S
- Referencia: Docker docs (publicar puertos en `127.0.0.1`), ASVS v5.0.0-13.4
- Evidencia: `docker-compose.deploy.yml:83-85` (`8026:8025`, `1026:1025` sin IP de bind).
- Verificación: Plausible (depende del firewall del host)
- Problema: La UI de MailHog no tiene autenticación y muestra los correos de test (tokens de recuperación, avisos con datos de usuarios). El backend llega a MailHog por `app-network`, así que el puerto SMTP publicado no hace falta.
- Arreglo: Quitar `1026:1025` y publicar la UI como `127.0.0.1:8026:8025` (acceso por túnel o Tailscale).

### OPS-09 Imágenes base sin fijar y `postgres:latest`
- Severidad: Baja
- Esfuerzo: S
- Referencia: Docker docs "Pin base image versions"; ISO 25010 Reliability
- Evidencia: `Dockerfile:1` (`maven:3.9-eclipse-temurin-21`), `Dockerfile:8` (`eclipse-temurin:21-jre-alpine`), `compose.yaml:5` (`postgres:latest`, mientras dev corre 16.x según `scripts/dev-snapshot.sh:26`), `compose.yaml:10` y `compose-snapshot.yaml:10-11` (5432 en 0.0.0.0 con credenciales de ejemplo).
- Verificación: Confirmado
- Problema: Dos builds del mismo commit pueden salir con JRE o Maven distintos; `dev-local` puede levantar un Postgres 17/18 y divergir del 16 de dev. El build usa `mvn` de la imagen y no `./mvnw`, así que tampoco respeta la versión del wrapper.
- Arreglo: Fijar `postgres:16-alpine` en `compose.yaml`, fijar las imágenes del Dockerfile por versión completa (o digest) y bindear 5432 a `127.0.0.1`.

### OPS-10 Logging de prod en ERROR, sin formato estructurado ni correlación
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-16.1/16.2 (logging de seguridad), ISO 25010 Maintainability/Analysability
- Evidencia: `application-prod.yaml:11-14` (`root: ERROR`, `ar.edu.utn.frc.siga: WARN`); no hay `logback-spring.xml` ni `logging.structured.*`; `grep -r "MDC|X-Request-Id|traceId"` sin resultados fuera de `sysacad`.
- Verificación: Confirmado
- Problema: En prod se pierden los WARN de Spring, Hikari y Flyway (pool agotado, migraciones ignoradas) y los INFO de la propia app; no hay forma de unir las líneas de log de un mismo request.
- Arreglo: `root: WARN` y `ar.edu.utn.frc.siga: INFO` en prod; `logging.structured.format.console: ecs` (soporte nativo de Spring Boot); un filtro que ponga un request id en el MDC queda como deuda.

### OPS-11 Health del contenedor atado al SMTP externo
- Severidad: Baja
- Esfuerzo: S
- Referencia: Spring Boot Actuator docs (health groups, liveness/readiness); ISO 25010 Reliability/Availability
- Evidencia: `pom.xml:70-73` (`spring-boot-starter-mail` activa `MailHealthIndicator`); `Dockerfile:18-19` y `cd-dev.yml:112-125` usan el health agregado; `application.yaml:57` deja `MAIL_HOST` vacío por default.
- Verificación: Plausible (no levanté la app)
- Problema: Si Gmail SMTP no responde o falta `MAIL_HOST`, el health agregado pasa a DOWN, el contenedor queda `unhealthy` y el job de deploy falla aunque la API funcione.
- Arreglo: `management.health.mail.enabled: false`, o apuntar el `HEALTHCHECK` a `/api/actuator/health/liveness` con `management.endpoint.health.probes.enabled: true`.

### OPS-12 Flyway de dev tolera migraciones faltantes y futuras
- Severidad: Baja
- Esfuerzo: S
- Referencia: Flyway docs (`ignoreMigrationPatterns`); CLAUDE.md "Migraciones"
- Evidencia: `application-dev.yaml:14` (`ignore-migration-patterns: "*:missing,*:future"`); el comentario de `application-dev-snapshot.yaml:12-15` explica el origen (una fila huérfana en `flyway_schema_history` de dev).
- Verificación: Confirmado
- Problema: El entorno desplegado `dev` arranca aunque su historial no coincida con el código, así que una migración renombrada o borrada pasa desapercibida hasta llegar a `test`/prod, donde ese patrón no está.
- Arreglo: Borrar la fila huérfana de `flyway_schema_history` en dev (o `flyway repair`) y quitar el patrón de `application-dev.yaml`; mantenerlo solo en `dev-snapshot`.

## Resumen

- 12 hallazgos: 0 Crítica, 1 Alta, 3 Media, 8 Baja. Todos con arreglo S salvo los saltos de minor de OPS-06.
- Lo urgente es OPS-01: el repo es público y los PRs corren `./mvnw verify` en el runner self-hosted que también despliega y guarda el `.env` de dev/test. Junto con OPS-04 (acciones por tag) forma el único camino realista a los secretos del servidor.
- OPS-03 es el riesgo funcional: CI prueba en UTC y la app corre en hora argentina.
- dev/test exponen Swagger y health con detalles sin login (OPS-02).
- Parches pendientes de bajo riesgo: Boot 4.0.8, Modulith 2.0.8, jjwt 0.12.7, POI 5.4.1, springdoc 3.0.3, JaCoCo 0.8.15.
- La IP de Tailscale sigue versionada en `scripts/dev-snapshot.sh` y `bruno/environments/` (OPS-07).

## Preguntas para el usuario

1. ¿Settings > Actions tiene "Require approval for all outside collaborators"? Si está activo, OPS-01 baja a Media.
2. ¿Los backends dev/test (puertos 8090/8091) y MailHog son accesibles solo por Tailscale o también desde Internet? Define la severidad final de OPS-02 y OPS-08.
