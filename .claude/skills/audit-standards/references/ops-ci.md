# Operación y CI: Docker, GitHub Actions, configuración

Vigencia: verificado el 2026-10-13.

## Docker

Fuente: https://docs.docker.com/build/building/best-practices/

- Multi-stage: el runtime no lleva Maven ni el JDK completo.
- Imágenes base fijadas por versión completa o digest (nunca `latest`, también en
  `compose*.yaml`).
- `USER` no root antes del `ENTRYPOINT`.
- `.dockerignore` que excluya `target/`, `.git`, `.env`, `graphify-out/`.
- `HEALTHCHECK` que no dependa de servicios externos opcionales (SMTP).
- Puertos de servicios de desarrollo (Postgres, MailHog) publicados en `127.0.0.1`, no en
  `0.0.0.0`.
- Límites de memoria y flags de JVM para contenedores.

## GitHub Actions

Fuente: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

Citas textuales usadas como criterio:
- "Pin references to actions to a commit SHA in your workflow files." Un tag es mutable: en
  marzo de 2026 se comprometió `trivy-action` (CVE-2026-33634).
- "self-hosted runners should almost never be used for public repositories on GitHub." Un PR
  desde un fork corre su código en el runner. Si el runner además despliega y tiene secretos,
  es Alta salvo que la aprobación obligatoria de PRs externos esté activa.
- "It's good security practice to set the default permission for the GITHUB_TOKEN to read
  access only for repository contents."

Además: no usar `pull_request_target` con checkout del código del PR; que los escáneres
(Trivy, OSV) corten el build (`exit-code` distinto de 0) al menos para severidad alta; umbral
de cobertura en CI; Dependabot o equivalente.

## Configuración Spring Boot

Fuente: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html

- Actuator: exponer solo `health` (y `info` si hace falta). `show-details: always` y Swagger
  abiertos en entornos accesibles fuera de la VPN son hallazgo.
- Secretos sin default inseguro (`${JWT_SECRET}` sin valor por defecto) y validados al arrancar.
- Zona horaria: una sola regla. Si la JVM fija una zona, los tests y los `@Scheduled(cron)`
  deben usar la misma (`zone = ...`).
- Logging de prod: nivel, correlación de requests, sin datos personales.

## Repo público

Regla del equipo: sin IPs internas, rutas absolutas del servidor ni volcados de Sysacad en
archivos versionados. Revisar `scripts/`, `bruno/environments/` y workflows.
