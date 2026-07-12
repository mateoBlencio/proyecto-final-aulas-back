# ADR-008: Timefold Solver (metaheurísticas) en vez de OR-Tools/MILP para la asignación automática

## Estado

Aceptado — 2026-07-11

## Contexto

La asignación automática (sprint 03) debe proponer un aula para cada evento
académico seleccionado, respetando una restricción dura (dos eventos no pueden
compartir aula en franjas horarias que se solapan, considerando la ocupación ya
firme en BD como inamovible/"pinned") y optimizando preferencias blandas con una
jerarquía explícita: sobrecupo (peso 100.000 por alumno excedente) ≫ misma
comisión en el mismo edificio (4.000) > misma aula (2.000) > capacidad ociosa (1).

El problema es una variante de *timetabling*/asignación con conflictos, NP-hard
en el caso general. Dos familias de herramientas open source dominan este
espacio:

- **Programación lineal entera mixta (MILP)** — p. ej. Google OR-Tools (CP-SAT o
  su wrapper MILP): modelo declarativo exacto, garantiza optimalidad (o cota de
  gap) si termina.
- **Metaheurísticas con scoring incremental** — Timefold Solver (fork activo de
  OptaPlanner): búsqueda local (late acceptance, tabu, etc.) sobre un modelo de
  dominio Java, sin garantía de optimalidad pero con comportamiento *anytime*.

Fuerzas en juego, en el contexto concreto de SIGA:

1. **UX de preview interactiva.** El solve corre detrás de
   `POST /allocations/auto-preview` con un límite de tiempo corto y configurable
   (`timeLimitSeconds`, `unimproved-seconds-limit`). El usuario espera una
   propuesta "buena en segundos", que luego ajusta a mano (validate-move) y
   confirma. No hay requisito de optimalidad demostrada.
2. **Tamaño y forma de la instancia.** Decenas a cientos de eventos por corrida,
   decenas de aulas, conflictos por solape **precalculados** por barrido
   fecha/hora antes del solve (no se modelan los horarios dentro del solver). El
   espacio de búsqueda es "evento → aula", no un problema de scheduling temporal
   completo.
3. **Re-resolución con ocupación pinned.** Cada corrida parte de un estado
   parcialmente fijo (ocupación firme de BD que no se puede mover) y re-resuelve
   solo los eventos seleccionados. El modelo debe expresar "pinned" de forma
   natural.
4. **Stack 100% Java/Spring.** El equipo mantiene un monolito Spring Boot; el
   solver corre en el mismo proceso, sin servicios aparte.
5. **Evolución esperada de las reglas.** Las preferencias blandas van a cambiar
   (nuevos criterios de agrupamiento, penalizaciones por piso/distancia, etc.);
   agregar/ajustar una regla debe ser barato y testeable de forma aislada.

## Decisión

Usamos **Timefold Solver** (Apache 2.0, `timefold-solver-core`) con:

- Modelo de planificación propio del módulo `solver` (`ScheduleSolution`,
  `ClassAllocation` con `@PlanningVariable` aula, `SolverRoom`), desacoplado de
  las entidades JPA.
- `ConstraintProvider` con 1 restricción HARD (no-solape sobre pares de
  conflicto precalculados), 1 MEDIUM (asignar todo lo posible) y 4 SOFT con la
  jerarquía de pesos descripta en [docs/modulos/solver.md](../modulos/solver.md).
  La variable de planificación `classroom` es `allowsUnassigned`: el solver deja
  sin aula lo verdaderamente inubicable en vez de forzar un solape.
- Terminación por `unimproved-seconds-limit` + límite total por request:
  comportamiento *anytime* — siempre hay una mejor-solución-hasta-ahora que
  devolver al usuario.
- Ocupación existente como entidades `pinned` (el solver las ve para calcular
  conflictos pero no puede moverlas).

## Consecuencias

Positivas:

- **Anytime + límites de tiempo** encajan exactamente con la UX de preview: la
  calidad degrada con gracia si la instancia crece, en vez de "o termina o no
  hay respuesta". Con MILP, un timeout puede dejar al usuario sin solución
  entera factible que mostrar.
- **Scoring incremental**: mover un evento de aula recalcula solo los deltas
  afectados; las corridas con mayoría de ocupación pinned (el caso típico:
  re-resolver pocos eventos sobre un calendario ya cargado) son baratas.
- **Reglas como código Java testeable**: cada constraint se verifica aislada con
  `ConstraintVerifier` (ya hay suite: `ClassroomConstraintProviderTest`).
  Agregar una preferencia nueva es un método más, sin re-derivar una formulación
  lineal (linearizaciones, big-M, variables auxiliares).
- **Integración nativa**: dependencia Maven pura Java, `SolverManager` se
  configura como bean Spring, sin binarios JNI por plataforma (OR-Tools carga
  librería nativa por SO/arquitectura, complica build multiplataforma, CI en
  runners self-hosted y el despliegue).
- La jerarquía de pesos SOFT (100.000 ≫ 4.000 > 2.000 > 1) expresa prioridades
  lexicográficas "suficientemente separadas" sin necesidad de objetivos
  multinivel explícitos.

Negativas / trade-offs aceptados:

- **Sin certificado de optimalidad ni de infactibilidad.** Un MILP/CP-SAT puede
  demostrar "no existe asignación sin conflictos"; Timefold solo devuelve la
  mejor encontrada. El caso "false-feasible" original (variable no-nullable que
  obligaba a proponer un aula en solape, score hard negativo indistinguible de
  una fila sana) quedó **resuelto**: `classroom` es `allowsUnassigned` y una
  restricción MEDIUM ("asignar todo lo posible") hace que el solver deje sin aula
  solo lo inubicable, que viaja explícito en `unresolved` (allocation además
  aplica un floor: un evento que ya tenía aula nunca regresa a `unresolved`).
  Sigue sin haber certificado formal de infactibilidad, pero ya no hay propuestas
  con hard negativo camufladas.
- **No determinista entre corridas** (salvo semilla fija): dos previews sobre el
  mismo estado pueden diferir. Aceptable porque la propuesta es editable y el
  usuario confirma explícitamente.
- Para el tamaño actual (decenas-cientos de eventos), un CP-SAT probablemente
  resolvería a óptimo en segundos; se renuncia a ese óptimo demostrable a cambio
  de los puntos anteriores. Si el problema mutara a scheduling temporal completo
  (elegir también día/hora, no solo aula) con instancias masivas, la decisión
  debería revisarse.

## Alternativas consideradas

- **OR-Tools CP-SAT / MILP**: descartado por costo de integración (JNI
  nativo por plataforma), formulación menos mantenible para reglas blandas
  cambiantes (pesos y linearizaciones vs. métodos Java), y peor encaje con el
  requisito *anytime* de la preview. Su ventaja (optimalidad/infactibilidad
  demostrables) no es requisito del negocio hoy.
- **Heurística ad-hoc propia** (greedy por orden de inscriptos + backtracking):
  costo inicial bajo pero sin camino de crecimiento; cada regla nueva complica
  el algoritmo, no hay score incremental ni framework de testing de reglas.
- **OptaPlanner**: predecesor de Timefold; el desarrollo activo (y la
  documentación para Spring Boot 3+/Java 21) migró a Timefold. Misma API
  conceptual, sin razón para elegir la rama sin mantenimiento.
