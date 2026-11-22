---
name: stop-slop
description: >
  Catálogo extendido y rúbrica de autoevaluación contra "AI slop" en prosa:
  planes de `.claude/plans/`, descripciones de PR, hallazgos de revisión,
  documentación de `.claude/docs/` y cualquier comentario de más de 2-3
  oraciones. Complementa, sin repetir, las reglas base de `../../CLAUDE.md`
  ("Prosa sin AI slop"): acá vive el catálogo completo de frases y
  estructuras a evitar, ejemplos antes/después del dominio de este repo, y
  una rúbrica de 1 a 10 en cinco ejes para decidir si un texto necesita
  reescritura antes de entregarse. Usala cuando el texto a producir supere
  unas pocas líneas; un mensaje de commit de una sola línea no la necesita.
  Adaptada al español y a SIGA desde el playbook "Stop Slop"
  (claudecodehq.com/playbooks/stop-slop) y `rules/writing-voice.md` del
  repositorio BioInfo/slopless.
license: MIT
---

# stop-slop

Esto no reemplaza las reglas de `../../CLAUDE.md` ("Prosa sin AI slop"), las
extiende. Si no las tenés frescas, leelas ahí antes de seguir: voz activa,
sin muletillas de arranque, sin adverbios de relleno en "-mente", sin
contrastes telegrafiados, sin guiones largos, afirmaciones concretas,
variación de ritmo. Lo de acá es el catálogo completo, ejemplos y la
rúbrica para decidir si vale la pena reescribir.

## Cuándo invocarla

Antes de dar por cerrado cualquiera de estos, si tienen más de 2-3
oraciones seguidas:

- Un plan en `.claude/plans/` (`planner`).
- La descripción de un PR o un hallazgo de revisión largo (`diff-reviewer`).
- Un documento en `.claude/docs/` (`frontend-docs-writer`).
- El cuerpo de un mensaje de commit, si lleva descripción además del título.

Un commit de una línea, un nombre de test o un comentario de una oración no
necesitan rúbrica: alcanza con las reglas base.

## Catálogo de frases y patrones a evitar

**Muletillas de arranque y cierre** (throat-clearing): "Cabe destacar
que...", "Es importante notar que...", "Dicho esto,", "En resumen,", "A modo
de conclusión,", "Como era de esperar,". Si la oración funciona sin la
muletilla, la muletilla sobra.

**Relleno con vocabulario "de IA"**, hueco salvo que aporte información
nueva: "juega un papel crucial", "es fundamental destacar", "un enfoque
integral/holístico", "aprovechar" (leverage) sin decir para qué, "robusto"
como adjetivo genérico, "de manera fluida/sin fisuras" (seamless), "en el
panorama actual de", "un mundo en constante cambio". La prueba: si al
borrar la frase el párrafo no pierde información, era relleno.

**Contrastes binarios forzados**: "No es X, es Y", "No se trata de X sino
de Y", "Más que X, es Y", "Esto no es solo X, es Y". Todas dicen lo mismo
con más palabras que afirmar Y directamente.

**Listas de negación**: enumerar lo que algo *no* es antes de decir qué
*es* ("No es un bug de concurrencia. No es un problema de caché. Es una
condición de carrera en..."). Andá directo a la afirmación.

**Fragmentación dramática**: frases sueltas de un renglón como cierre de
párrafo para simular impacto ("Así de simple.", "Eso es todo.", "Nada
más."). Si el párrafo anterior ya lo dijo, sobra.

**Agencia falsa**: sujeto abstracto actuando solo ("el problema se
resolvió", "la arquitectura decidió", "el bug se corrigió") en vez de quién
hizo qué ("Mateo corrigió el choque de horario en
`RoomRequestService.validate`").

**Preguntas retóricas como transición**: "¿Pero qué significa esto en la
práctica?", "¿Por qué importa esto?". Contestá la pregunta en la oración
siguiente sin haberla hecho.

**Cierres grandilocuentes / "quotables"**: frases que suenan a epígrafe de
charla ("Y así es como un pequeño cambio transforma todo el sistema."). Un
hallazgo de revisión o un plan terminan cuando se acaba la información, no
con una moraleja.

**Énfasis performativo**: negrita o cursiva para dar peso emocional en vez
de marcar un término técnico real. Si todo está en negrita, nada lo está.

**Ritmo uniforme**: oraciones todas del mismo largo, o listas de
exactamente tres ítems por reflejo (three-item rule). Variá; una lista de
dos o de cuatro es tan válida como una de tres.

## Rúbrica de autoevaluación (1-10 por eje, antes de entregar)

| Eje | Pregunta |
|---|---|
| Directness | ¿Afirma o le da vueltas antes de afirmar? |
| Rhythm | ¿Varía el largo de oración o suena a metrónomo? |
| Trust | ¿Le explica de más al lector cosas que ya sabe (es del equipo)? |
| Authenticity | ¿Suena a alguien de este equipo escribiendo, o a marketing? |
| Density | ¿Hay algo que se pueda cortar sin perder información? |

Sumá los cinco. Menos de 35/50: reescribí antes de entregar. Un puntaje
alto no valida que el contenido sea correcto, solo que la prosa no estorba;
la corrección del hallazgo o del plan sigue siendo responsabilidad de quien
lo escribió.

## Ejemplos antes/después

❌ "Cabe destacar que el endpoint de `RoomRequestController` no está
validando correctamente las fechas, lo cual podría generar un
comportamiento inesperado en ciertos casos."

✅ "`RoomRequestController.create` no valida que `fechaFin` sea posterior a
`fechaInicio`: un payload con fechas invertidas crea la solicitud igual."

❌ "No se trata simplemente de un problema de rendimiento, sino de una
arquitectura que no fue diseñada pensando en la escala."

✅ "`AllocationService.findConflicts` recorre todas las asignaciones del
cuatrimestre por cada request; con 8000 asignaciones tarda 4s. Necesita un
índice por `aula_id + franja_horaria`, no un rediseño."

❌ "El equipo trabajó arduamente para resolver este desafío, y finalmente,
tras un proceso iterativo, se logró una solución robusta y escalable."

✅ "Agregamos un `UNIQUE INDEX` en `(aula_id, dia, hora_inicio)` para que
Postgres rechace el choque en vez de detectarlo en Java después del
insert."

## Cuándo no aplica

Código, nombres de variables, JSON, logs, tablas de referencia técnica
(mapeo de endpoints, enums) — ahí la precisión importa más que el ritmo, no
hace falta variar el largo de una fila de tabla. Tampoco aplica a texto de
una o dos oraciones: la rúbrica es para prosa larga, no para todo lo que se
escribe.

## Fuentes

Catálogo adaptado del playbook "Stop Slop"
(claudecodehq.com/playbooks/stop-slop, reglas core + catálogo de frases +
rúbrica) y de `rules/writing-voice.md` en BioInfo/slopless (categorías de
palabras huecas y anti-patrones estructurales). Traducido, recortado a lo
que aplica a texto en español, y con ejemplos propios del dominio de SIGA
en vez de los originales en inglés. No se importaron los hooks de
`slopless` (bloqueo de `rm -rf`, `kill -9 -pgid`, statusline, routing de
modelos): son mecanismos de seguridad/operación sin relación con prosa, y
esta skill es solo sobre escritura.
