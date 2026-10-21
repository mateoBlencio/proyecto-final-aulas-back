# Método de auditoría completa

Es el método que produjo `.claude/audits/2026-10-13/`. Sirve para repetir la auditoría o
auditar una sola dimensión con el mismo formato.

## 1. Preparación

- Actualizar el grafo: `.venv/bin/graphify update .` (graphify vive en el venv del repo, no en
  el PATH).
- Fijar el commit auditado y compararlo con `origin/develop` (`git diff --stat HEAD origin/develop`).
- Compilar: `./mvnw -q -DskipTests test-compile`.
- Pedir al usuario que levante Docker y, si hace falta base real, `dev-snapshot`
  (`docker compose -f compose-snapshot.yaml up -d`). El sandbox no arranca Docker.
- Escribir el plan en `.claude/plans/` con: objetivo, restricción de tiempo, línea base
  medida, marco de referencia (dimensión → estándar), método por dimensión, formato de
  hallazgo, fuera de alcance y preguntas. Esperar aprobación antes de ejecutar.

## 2. Dimensiones

| # | Dimensión | Agente sugerido | Referencia |
|---|---|---|---|
| 1 | Estructura y arquitectura | general-purpose | `modularity.md` |
| 2 | Modelo de datos y DER | general-purpose | `data-persistence.md` |
| 3 | Rendimiento barato | (mismo que 2) | `data-persistence.md` |
| 4 | Seguridad | `security-reviewer` | `security.md` |
| 5 | Testing | general-purpose | skill `testing-pyramid`, `tooling.md` |
| 6 | Calidad de código | `diff-reviewer` (por grupo de módulos) + `comment-sicko` | `quality-iso25010.md`, skills `ponytail-*` |
| 7 | API y UX de errores | general-purpose | `api-design-ux.md` |
| 8 | Operación, configuración y CI | general-purpose | `ops-ci.md` |

Orden: 1 y 2 primero si se hacen en serie (dan el mapa). En paralelo, todas a la vez.
El hilo principal corre las herramientas (`tooling.md`) y consolida.

## 3. Brief común para agentes

Todos los agentes reciben el mismo brief en el scratchpad. Reglas que lo hicieron funcionar:

- Solo lectura. La única escritura permitida es el archivo de salida en el scratchpad. Los
  agentes con `Edit` (`diff-reviewer`, `security-reviewer`) tienden a aplicar fixes: el
  prompt tiene que prohibirlo explícitamente. `comment-sicko` borra por defecto: pedir
  "modo solo reporte".
- Ningún agente corre `./mvnw`: dos builds a la vez rompen `target/`. Las herramientas las
  corre el hilo principal y les pasa los resultados.
- Orientarse con graphify antes de leer código.
- Consultar la wiki (`../proyecto-final-aulas-front.wiki`) o `business-rules` antes de
  reportar como bug algo que puede ser regla de negocio; si no se resuelve, "Plausible" con
  la duda explícita.
- Pocos hallazgos confiables antes que muchos dudosos.
- Excluir explícitamente lo que el usuario dejó fuera (en 2026-10-13: el módulo `sysacad`).
- Nunca imprimir secretos ni tokens: referenciarlos por `archivo:línea`.

Si un agente se corta por límite de uso, reanudarlo con `SendMessage` (conserva el
contexto) en vez de lanzarlo de nuevo.

## 4. Formato de hallazgo

```
### <ID> <título corto>
- Severidad: Crítica | Alta | Media | Baja
- Esfuerzo: S (<1h) | M (<1 día) | L (>1 día)
- Referencia: <requisito citado>
- Evidencia: <ruta:línea> (+ comando o consulta)
- Verificación: Confirmado | Plausible
- Problema: <1-3 oraciones>
- Arreglo: <cambio mínimo>
```

IDs por dimensión: `ARQ`, `DAT`, `PERF`, `SEC`, `TEST`, `QA-<grupo>`, `API`, `OPS`, `DEP`.
"Confirmado" exige haber leído el código que lo prueba o haberlo reproducido.

## 5. Verificación y consolidación

- El hilo principal relee el código de cada hallazgo Alta antes de consolidarlo. En
  2026-10-13 eso corrigió una afirmación propia sobre la migración V23.
- Marcar duplicados entre anexos (mismo problema visto desde seguridad y desde calidad).
- El reporte final (`README.md` en `.claude/audits/<fecha>/`) tiene: números de base, tabla
  de hallazgos por dimensión, "Arreglar ahora" (Alta, o Media con esfuerzo S, ordenado por
  valor sobre costo), deuda anotada, preguntas abiertas que cambian severidades, y cómo se
  hizo. Los anexos por dimensión van en la misma carpeta.
- Repo público: antes de dejar anexos en una carpeta versionada, buscar IPs, rutas absolutas
  del usuario, tokens y datos reales (`grep -nE '\b[0-9]{1,3}(\.[0-9]{1,3}){3}\b|eyJ|/home/'`).

## 6. Tests que no pueden fallar (chequeo de la dimensión 5)

Asserts tautológicos, tests sin assert ni verify, mocks que reemplazan lo que se quiere probar,
`verify()` de la llamada que hizo el mismo test, `assertNotNull` como único assert, tests
`@Disabled`, catch que traga la excepción, y tests de autorización que solo prueban el caso
denegado (PIT lo detecta: el mutante que niega la condición sobrevive).
