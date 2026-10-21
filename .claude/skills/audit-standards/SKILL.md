---
name: audit-standards
description: "Biblioteca de referencia para auditar el backend de SIGA contra estándares publicados: ISO/IEC 25010:2023 (modelo de calidad), OWASP ASVS 5.0.0, OWASP Top 10:2025, OWASP API Security Top 10:2023, RFC 8725 (JWT), RFC 9457 (Problem Details), Zalando RESTful API Guidelines, heurísticas de Nielsen, PostgreSQL y Hibernate (índices, constraints, N+1), Spring Modulith 2.0 (verify, eventos), Docker y GitHub Actions hardening, más los comandos de herramientas que ya funcionaron en este repo (JaCoCo, PIT, SpotBugs + FindSecBugs, OSV-Scanner con SBOM, gitleaks). Usala cuando haya que auditar el repo completo o una dimensión (seguridad, datos, API, arquitectura, testing, operación), cuando un hallazgo necesite citar el requisito que incumple, o para correr una herramienta de análisis sin redescubrir sus trampas. No es un checklist para recorrer entero: abrí solo la referencia de la dimensión en juego."
---

# audit-standards

Fuentes y método de la auditoría completa del 2026-10-13 (`.claude/audits/2026-10-13/`),
guardados para reusarlos. Cada hallazgo de una auditoría compara el código contra un
requisito publicado y lo cita (`ASVS v5.0.0-8.2.1`, `API1:2023`, `RFC 9457`), así quien lo
lee puede verificar el criterio sin depender de la opinión del auditor.

## Cómo usarla

1. Identificá la dimensión: calidad general, seguridad, API/UX, datos y rendimiento,
   arquitectura modular, testing, operación/CI.
2. Abrí solo el archivo de esa dimensión. Para una auditoría completa, empezá por
   `references/workflow.md`.
3. Citá el requisito exacto en el hallazgo. Si el estándar no cubre el caso, decilo en vez de
   forzar una cita.
4. Antes de afirmar algo sobre versiones o fechas de un estándar, revisá la sección "Vigencia"
   del archivo: los estándares cambian y la fecha de verificación está anotada.

| Dimensión | Referencia | Estándares |
|---|---|---|
| Método de auditoría completa | `references/workflow.md` | Plan, dimensiones, brief para agentes, formato de hallazgo, consolidación |
| Calidad general | `references/quality-iso25010.md` | ISO/IEC 25010:2023 |
| Seguridad | `references/security.md` | ASVS 5.0.0, Top 10:2025, API Security Top 10:2023, RFC 8725 |
| API y UX de errores | `references/api-design-ux.md` | RFC 9457, Spring `ProblemDetail`, Zalando, Nielsen |
| Datos y rendimiento | `references/data-persistence.md` | PostgreSQL, Flyway, Hibernate (N+1, OSIV, batch fetch) |
| Arquitectura modular | `references/modularity.md` | Spring Modulith 2.0 |
| Operación y CI | `references/ops-ci.md` | Docker build best practices, GitHub Actions hardening, Actuator |
| Herramientas | `references/tooling.md` | Suite + JaCoCo, PIT, SpotBugs/FindSecBugs, OSV-Scanner, gitleaks |

Testing no tiene archivo propio: la teoría vive en la skill `testing-pyramid`, y
`references/tooling.md` cubre cobertura y mutation testing.

## Relación con otras skills

- `security-audit` (Cloudflare): metodología de caza de vulnerabilidades por dominio. Esta
  skill aporta los requisitos a citar; aquella, cómo buscarlos.
- `testing-pyramid`: nivel y tamaño de test, BVA.
- `ponytail-audit`, `ponytail-debt`: sobre-ingeniería.
- `stop-slop`: redacción de hallazgos y reportes largos.
