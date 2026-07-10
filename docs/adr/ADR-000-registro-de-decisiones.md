# ADR-000: Registro de decisiones arquitectónicas

## Estado

Aceptado — 2026-07-10

## Contexto

El proyecto SIGA no contaba con un mecanismo para dejar constancia de las
decisiones de diseño y arquitectura tomadas durante su desarrollo. Esto
dificulta entender, más adelante, por qué el sistema está construido de
una manera determinada y qué alternativas se descartaron.

## Decisión

El proyecto registra sus decisiones arquitectónicas relevantes como
Architecture Decision Records (ADRs) en la carpeta `docs/adr/`.

- Las decisiones globales (que afectan a todo el proyecto o a más de un
  módulo) se numeran con el prefijo `ADR-NNN` de forma correlativa, por
  ejemplo `ADR-001-nombre-de-la-decision.md`.
- Las decisiones específicas de un módulo también se registran en
  `docs/adr/` con el mismo esquema de numeración correlativa, indicando
  el módulo afectado en el título o en el ámbito del documento, por
  ejemplo `ADR-004-allocation-soft-delete-con-envers.md`.
- Todo ADR sigue el formato definido en `docs/adr/plantilla.md`
  (Título, Estado, Contexto, Decisión, Consecuencias, Alternativas
  consideradas).
- Un ADR puede quedar `Reemplazado` por otro posterior; en ese caso se
  actualiza su estado y se referencia al ADR que lo reemplaza.

## Consecuencias

- Cada decisión de arquitectura significativa debe documentarse antes de
  darse por cerrada, lo que agrega un pequeño costo de disciplina al
  equipo.
- El historial de decisiones queda versionado junto con el código, en
  lugar de vivir en herramientas externas o en la memoria del equipo.
- Facilita el onboarding de nuevos integrantes y las revisiones futuras
  de diseño.

## Alternativas consideradas

- **No documentar decisiones**: se descartó por ser la situación actual,
  que ya generó pérdida de contexto.
- **Usar una wiki externa**: se descartó porque separa la documentación
  del código y del historial de cambios (git), dificultando mantenerla
  actualizada.
