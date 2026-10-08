## Pull requests

- Nunca agregues la línea/firma "Generated with Claude Code" (ni links a
  claude.com/claude-code) en la descripción del PR.

## Commits

- Nunca te agregues como coautor (sin línea `Co-Authored-By: Claude ...`).
- Mensaje breve; si lleva descripción/cuerpo, que sea lo más compacto posible
  (evitar párrafos largos o listas extensas).
- Conventional Commits en español, contra `feature/**`/`hotfix/**` (ver
  README).
- Nunca hagas `git push` sin autorización explícita o implícita del usuario
  en ese momento de la conversación.

## Migraciones (Flyway)

- Toda migración nueva va en `src/main/resources/db/migration/`, nombre
  `V{n}__descripcion_en_snake_case.sql`, `{n}` siguiente al último
  existente.
- Una migración ya aplicada (mergeada) no se edita: si hace falta corregir
  algo, se agrega una migración nueva.

## Estilo de comunicación

- Sin emojis, nunca.
- No asumas nada; ante la duda, preguntá.
- Al explicar código o funcionalidad: minimizá referencias al código en sí,
  usá snippets solo si hacen falta. Priorizá claridad sobre exhaustividad:
  explicación simple primero, más detalle solo si te lo piden (drill-down).
- Al evaluar una idea: sin elogios ni cumplidos ("buena observación", "buena
  idea", etc.). Directo y objetivo, respondé lo que se pregunta.
- General: lo más conciso posible sin sacrificar que se entienda.

### Prosa sin "AI slop" (planes, PRs, commits, comentarios)

Aplica a todo texto que escribas: planes en `.claude/plans/`, mensajes de
commit, descripciones de PR, hallazgos de revisión, comentarios de código.

- Voz activa siempre; sujeto humano/concreto haciendo la acción. Nada de
  "la decisión emerge" o "el bug se resuelve": decí quién hizo qué.
- Nada de muletillas de arranque ("cabe destacar", "en resumen", "la
  realidad es que") ni cierres grandilocuentes.
- Nada de adverbios terminados en "-mente" como relleno (realmente,
  simplemente, literalmente, básicamente).
- Nada de contrastes telegrafiados ("no es X, es Y") ni listas de negación
  ("no es A... no es B... es C"). Andá directo al punto.
- Nada de guiones largos (—) como muletilla de puntuación.
- Afirmaciones concretas, no vagas ("mejora el rendimiento" sin decir cómo
  ni cuánto). Nombrá archivos, clases, métodos y números reales.
- Variá el largo de las oraciones; evitá listas de exactamente tres ítems
  por reflejo.

Catálogo completo de frases/patrones a evitar, ejemplos y rúbrica de
autoevaluación (1-10 en cinco ejes) para textos largos: skill `stop-slop`
(`.claude/skills/stop-slop/`). Un commit de una línea no la necesita; un
plan, una descripción de PR o un hallazgo largo sí.

### Verificación real

Un instrumento que no puede fallar certifica cualquier cosa que le apuntes.
Aplica a tests y a cualquier verificación antes de dar algo por terminado:

- Nunca digas que algo "funciona" o "está testeado" sin haber corrido el
  comando correspondiente en esta sesión.
- Un test que no puede fallar (asserts tautológicos, mocks que nunca
  ejercitan el comportamiento real) no cuenta como verificación: es un
  hallazgo de revisión, no una garantía.
- Preferí verificación incremental (compilar/testear el módulo tocado a
  medida que avanzás) sobre correr todo al final y asumir que pasó.

## Entorno de desarrollo

- `dev-snapshot`: Postgres persistente por Docker
  Compose que importa un dump de la base de `dev` antes de levantar, y
  después corre Flyway real. Sirve para probar contra datos reales sin
  tocar `dev`.
- El sandbox de este agente no tiene acceso al Docker del host: pedirle al
  usuario que levante los contenedores antes de correr contra
  `dev-local`/`dev-snapshot`.

## Skills y herramientas

- `.claude/dependencies.json` lista las skills, CLIs y plugins que usan
  este CLAUDE.md, los agentes y los hooks, con quién los usa y cómo se
  instalan. El hook `check-dependencies.sh` los verifica al iniciar la
  sesión y reporta los que faltan.
- Si falta una dependencia que la tarea necesita (reportada al inicio, o
  porque la skill o el comando no aparece al usarlo), preguntale al usuario
  con `AskUserQuestion` si continúa sin ella o la instala, mostrando el
  comando de instalación del manifiesto. No instales nada sin su ok. Si
  sigue sin ella, decí qué paso se saltea. Un subagente devuelve la
  pregunta según su sección "Preguntas al usuario".
- Las skills `code-review`, `simplify`, `security-review` y `run` vienen
  con Claude Code; no van en el manifiesto.
- Al agregar una skill, CLI, plugin o servidor MCP que use la
  configuración de este repo, sumalo a `.claude/dependencies.json` en el
  mismo cambio. Un servidor MCP stdio entra como CLI de su comando.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Si el tool MCP de graphify está disponible (`mcp__graphify__query_graph`, `get_neighbors`, `get_node`, `shortest_path`), preferilo sobre invocar la CLI por Bash para consultas puntuales: evita el subproceso y el hook de Bash/Grep, que ya recomendó esa misma consulta. La CLI sigue siendo necesaria para construir o actualizar el grafo (`graphify`, `graphify update`).
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
