# Dimensión 5: Testing (parte estática)

Alcance: `src/test/java` sin `sysacad`, commit 22c693f2. Sin correr `./mvnw`: los números de
ejecución (2263 tests, 0 fallas, 0 skipped, 3m30s) y la cobertura JaCoCo vienen de la corrida del
hilo principal (`coverage.txt`).

## Inventario

Clasifiqué cada clase con al menos un `@Test`/`@ParameterizedTest` por lo que levanta:

| Tipo | Clases | Métodos declarados | % métodos |
|---|---:|---:|---:|
| Unitario puro (sin Spring, sin mocks) | 61 | 456 | 22% |
| Unitario con Mockito | 81 | 931 | 44% |
| Slice (`@DataJpaTest`, Testcontainers sin contexto completo) | 8 | 51 | 2% |
| Integración amplia (`AbstractIntegrationTest` / `DashboardIntegrationSupport` / `@SpringBootTest`, Postgres por Testcontainers + MockMvc) | 85 | 676 | 32% |
| ArchUnit / Modulith (`ModularityTests`) | 1 | 3 | 0,1% |
| Total | 236 | 2117 | |

Los 2117 métodos declarados se expanden a ~2263 ejecuciones por los 18 `@ParameterizedTest` y por
`sysacad`.

Por módulo (métodos, % integración amplia): roomrequest 447 (33%), audit 438 (47%), space 182
(31%), allocation 194 (30%), academic 180 (23%), dashboard 131 (57%), events 115 (3%), common 109
(0%), auth 100 (36%), preview 73 (24%), ingest 53 (9%), optimizer 35 (0%), notification 27 (25%),
settings 27 (40%).

Lectura contra la pirámide (skill `testing-pyramid`): la base es ancha (66% unitarios, tamaño
small), así que la suite no tiene forma de cono de helado. El hueco está en el medio: casi no hay
integración angosta (8 clases slice); toda consulta JPA, specification o migración se prueba
levantando el contexto completo. Eso no rompe nada hoy porque las 85 clases comparten un único
contexto Spring (solo 6 clases usan `@TestPropertySource`/`@MockitoSpyBean`/`@DirtiesContext`) y un
único Postgres (`TC_DAEMON=true`), y la suite tarda 3m30s. Las 172 aristas de
`AbstractIntegrationTest` en el grafo reflejan esa herencia, no un cono.

Aislamiento de datos: ninguna clase de integración limpia la base ni usa `@Transactional` a nivel
clase. Los tests evitan choques generando claves únicas (`IntegrationTestData.nextSeq()`, semilla
`System.nanoTime()`) y aserciones por delta (`count()` antes/después, ej.
`RoomRequestItemStatusCountsApiIntegrationTest`). Funciona porque Surefire corre secuencial (no hay
`junit-platform.properties` ni configuración de paralelismo); activar paralelismo rompería las
aserciones por delta. `IntegrationTestData` (160 aristas) es una fábrica de entidades sin estado
compartido salvo el contador, así que no acopla tests entre sí.

Esperas: 7 `await().atMost(...)` de Awaitility para listeners asíncronos (correcto) y un único
`Thread.sleep` (TEST-01). No encontré `@Disabled`, `@Order` ni `@TestMethodOrder`.

Tests sin aserción: 4 (`ApplicationSmokeIntegrationTest.contextLoads`, dos métodos de
`ModularityTests`, `ManualEmailSendTest`). Tests que solo hacen `verify(...)`: 59, en su mayoría
pruebas de interacción legítimas (que el servicio llame o no llame al validador, al lock, al
repositorio). No los cuento como hallazgo; los mutantes que sobrevivan en PIT dirán cuáles no
alcanzan.

Cruce con JaCoCo (ramas): optimizer 75,6%, settings 76,3%, academic 78,5%, notification 78,8%. De
esos, el que pesa por riesgo es optimizer (TEST-04). allocation (82,6%), roomrequest (82,9%) y auth
(85,8%) tienen cobertura de ramas aceptable; sus huecos son de autorización (TEST-02, TEST-03), que
la cobertura de líneas no mide porque la rama "denegar" vive en Spring Security.

## Hallazgos

### TEST-01 ManualEmailSendTest corre en cada build, sin aserción y con sleep de 5s
- Severidad: Media
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Testability; `.claude/CLAUDE.md` "Verificación real"
- Evidencia: `src/test/java/ar/edu/utn/frc/siga/notification/ManualEmailSendTest.java:22-61`
- Verificación: Confirmado (la corrida reporta 0 skipped, así que con Docker arriba se ejecutó)
- Problema: Un test manual levanta un segundo contexto Spring y un contenedor MailHog, manda un mail a `TU_EMAIL_REAL@gmail.com`, imprime por `System.out` y duerme 5 s (línea 60). No tiene ningún assert: pasa aunque el envío falle o quede en la cola, y suma unos 10-20 s y un contexto extra a cada build.
- Arreglo: Marcarlo `@Tag("manual")` y excluir ese tag en Surefire (o `@EnabledIfSystemProperty(named = "siga.manualMail", matches = "true")`). Si se quiere conservar como test automático, reemplazar el sleep por `await()` contra la API HTTP de MailHog (`/api/v2/messages`) y asertar asunto y destinatario.

### TEST-02 Endpoints de escritura de allocation, preview, imports y room-requests sin test de 403
- Severidad: Alta
- Esfuerzo: M (<1 día)
- Referencia: OWASP API5:2023 (Broken Function Level Authorization); ASVS v5.0.0-8.2.1; skill `testing-pyramid` `api-testing.md` (zero-trust)
- Evidencia: `src/main/java/ar/edu/utn/frc/siga/allocation/controller/AllocationController.java:130,145,164,182` (`PERM_ALLOCATION_WRITE`); `src/main/java/ar/edu/utn/frc/siga/roomrequest/controller/RoomRequestController.java:137,181,197,212,238,256` (`PERM_ROOM_REQUEST_WRITE`: assign, derive, return, notify, suggestion, confirm); `PreviewController` (`/v1/previews`, salvo `reallocation-suggestion`); `IngestController` (`/v1/imports`). Búsqueda: `grep -rln isForbidden src/test/java | xargs grep -l "/v1/allocations"` solo devuelve `AuditRegistryApiIntegrationTest` y `ReallocationSuggestionIntegrationTest`, que no golpean esos endpoints de escritura. En roomrequest, el único 403 de escritura es `cancel` (`RoomRequestItemCancelApiIntegrationTest.java:112-129`).
- Verificación: Confirmado
- Problema: `SecurityAnnotationsArchTest` garantiza que cada endpoint tenga `@PreAuthorize`, pero nada verifica que sea el permiso correcto. Si alguien cambia `PERM_ALLOCATION_WRITE` por `PERM_ALLOCATION_READ` en `AllocationController`, el rol `CONSULTA` pasa a poder asignar aulas y ningún test falla: el usuario fixture por defecto es `SUBSECRETARIA` con todos los permisos (`AbstractIntegrationTest.java:53`).
- Arreglo: Un `@ParameterizedTest` por controller con `@MethodSource` que liste (método HTTP, URI, body mínimo) de cada endpoint de escritura y aserte 403 con `mockMvcAs("consulta-<modulo>@...", SystemRole.CONSULTA)` y 401 sin token. Unas 25 filas en total; ninguna necesita datos sembrados porque Spring Security corta antes del servicio.

### TEST-03 Alcance por edificio en allocation probado solo con el resolver mockeado
- Severidad: Media
- Esfuerzo: S (<1h)
- Referencia: OWASP API1:2023 (BOLA); ASVS v5.0.0-8.2.2
- Evidencia: `src/test/java/ar/edu/utn/frc/siga/allocation/service/impl/AllocationServiceImplTest.java:218-230,487-499` (`doThrow(...).when(buildingScopeResolver).requireAccess(...)`); `src/main/java/ar/edu/utn/frc/siga/allocation/service/impl/AllocationServiceImpl.java:193-213`. Ningún `*IntegrationTest` de `allocation` usa `mockMvcAsScoped`.
- Verificación: Confirmado
- Problema: El unitario prueba que el servicio pide acceso para el edificio que devuelve el `ClassroomService` mockeado, y `DefaultBuildingScopeResolverTest` prueba el resolver aislado. Ningún test junta las dos piezas: un `AUXILIAR_AULICO` acotado al edificio A que hace `POST /v1/allocations` sobre un aula del edificio B. roomrequest sí tiene ese test (`RoomRequestItemDetailApiIntegrationTest.java:116-146`).
- Arreglo: Agregar a `AllocationApiIntegrationTest` un caso con `mockMvcAsScoped(..., AUXILIAR_AULICO, ScopeType.BUILDING, edificioA)` que asigne en un aula del edificio B y espere 403, más el caso positivo sobre su propio edificio.

### TEST-04 Restricciones del optimizer sin test de ConstraintVerifier
- Severidad: Media
- Esfuerzo: M (<1 día)
- Referencia: ISO 25010 Functional suitability/Correctness; skill `testing-pyramid` (Timefold `ConstraintVerifier` es la herramienta de test angosto prevista por `test-writer`)
- Evidencia: `src/main/java/ar/edu/utn/frc/siga/optimizer/constraint/NoOverlap.java:22-28` y otras 6 restricciones del paquete; `src/test/java/ar/edu/utn/frc/siga/optimizer/constraint/` solo tiene `OptimizerConstraintsTest` (nombres y pesos) y `PermissionNotSatisfiedTest` (predicado estático, sin stream). `pom.xml:196-198` declara `timefold-solver-core` pero no `timefold-solver-test`. JaCoCo: optimizer 75,6% de ramas, el más bajo de la suite.
- Verificación: Confirmado
- Problema: Ningún test ejecuta los constraint streams de forma aislada. `NoOverlap` (la hard constraint que evita asignar dos clases a la misma aula), `AllocateAllPossible`, `MinimizeOvercrowding`, `MinimizeUnusedCapacity` y las dos `PreferSame*` solo se ejercitan a través de `PreviewFlowIntegrationTest`, con el solver real y 1 s de límite. Ese test detecta un resultado infactible, pero no un peso mal puesto ni el filtro `!a.isPinned() || !b.isPinned()`. El riesgo de doble asignación queda acotado porque `confirm` revalida solapes en `AllocationValidator`.
- Arreglo: Agregar `ai.timefold.solver:timefold-solver-test` con scope test y un `ConstraintVerifier` por restricción: penaliza / no penaliza, con casos borde (franjas contiguas, ambos pinned, capacidad igual a inscriptos).

### TEST-05 Vencimiento de REGULAR_ROOM_CHANGE sin caso borde endDate == hoy, y sin Clock
- Severidad: Media
- Esfuerzo: S (<1h)
- Referencia: ISTQB BVA (skill `testing-pyramid` `boundary-value-analysis.md`)
- Evidencia: `src/main/java/ar/edu/utn/frc/siga/roomrequest/service/impl/RoomRequestExpiryServiceImpl.java:41,73` (`LocalDate.now()`, `endDate.isBefore(today)`); `src/test/java/ar/edu/utn/frc/siga/roomrequest/service/impl/RoomRequestExpiryServiceImplTest.java:59-105` prueba ayer, futuro (mañana) y null.
- Verificación: Confirmado
- Problema: Para los ítems por fecha directa, el integration test cubre el borde `date == hoy` (`RoomRequestExpiryServiceImplIntegrationTest.java:55`). Para REGULAR_ROOM_CHANGE falta `endDate == hoy`, así que cambiar `isBefore` por `!isAfter` (cancelar pedidos todavía vigentes) no rompe ningún test. El servicio usa `LocalDate.now()` aunque existe el bean `Clock` (`common/config/ClockConfig.java:12`), y el test calcula su propio `now()`: los dos pueden caer en días distintos si la suite corre a medianoche.
- Arreglo: Inyectar `Clock` en `RoomRequestExpiryServiceImpl` (como ya hace `HolidayServiceImpl`) y agregar el caso `endDate == hoy → no vence` con `Clock.fixed`.

### TEST-06 Asserts que solo verifican el valor que devuelve un mock
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: `.claude/CLAUDE.md` "Verificación real" (mocks que no ejercitan el comportamiento)
- Evidencia: `src/test/java/ar/edu/utn/frc/siga/events/service/impl/AcademicEventServiceImplTest.java:630,666`
- Verificación: Confirmado
- Problema: `assertThat(result).isNotNull()` sobre el retorno de `composer.compose(...)`, que el mismo test stubbea en las líneas 626 y 662. Ese assert no puede fallar. El nombre promete "persiste igual", pero el test no inspecciona qué entidad llega a `eventRepository.save`.
- Arreglo: Capturar el argumento de `save` con `ArgumentCaptor` y asertar fecha, hora, duración y `commission` null o presente; borrar el `isNotNull`.

### TEST-07 mockMvcAs ignora el rol pedido si el email ya tiene una asignación
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Testability
- Evidencia: `src/test/java/ar/edu/utn/frc/siga/AbstractIntegrationTest.java:94-113` (`if (user.getRoleAssignments().isEmpty())`)
- Verificación: Confirmado (latente: hoy cada email fijo se usa siempre con el mismo rol, ej. `auxiliar@frc.utn.edu.ar` 20 veces como `AUXILIAR_AULICO`)
- Problema: Como la base se comparte entre clases y no se limpia, si un test futuro llama `mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.CONSULTA)`, recibe en silencio el rol que haya guardado el primer test que usó ese email. El resultado depende del orden de ejecución.
- Arreglo: Que `ensureUserWithRole` falle con `IllegalStateException` si la asignación existente no coincide con (rol, scopeType, scopeId) pedido, o generar siempre el email con `nextSeq()`.

### TEST-08 Código de test muerto o sin verificación en ModularityTests e IntegrationAuthTestSupport
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Analysability
- Evidencia: `src/test/java/ar/edu/utn/frc/siga/support/IntegrationAuthTestSupport.java` (sin usos: `grep -rln IntegrationAuthTestSupport src/test/java` solo devuelve el propio archivo); `src/test/java/ar/edu/utn/frc/siga/ModularityTests.java` (`printStructure`, `generateDocumentation` sin assert)
- Verificación: Confirmado
- Problema: `IntegrationAuthTestSupport` hace login por `RestTemplate` a un puerto real y nadie lo llama. Los dos métodos de `ModularityTests` imprimen y escriben PlantUML en `target/` en cada build: cuentan como tests pasados sin verificar nada.
- Arreglo: Borrar `IntegrationAuthTestSupport`. Mover la generación de documentación a un test con `@Tag("docs")` excluido por defecto, o dejarla y quitarla del conteo con `@Disabled("generación manual de docs")`.

## Paquetes recomendados para PIT

1. `ar.edu.utn.frc.siga.allocation.validator` (junto con `ar.edu.utn.frc.siga.common.util`, donde viven `TimeRanges`, `Clashes` y `Overcrowding`): reglas de choque de horario y de capacidad. Los tests de BVA existen (adyacencia, margen de 40/41 min, capacidad exacta), y PIT confirma si alcanzan para matar mutantes de `<`/`<=`. Clases chicas y puras, corrida rápida.
2. `ar.edu.utn.frc.siga.roomrequest.validator`: máquina de estados de pedidos (`RoomRequestTransitionValidator`), elegibilidad de derivación y `RoomRequestAccessControl` (alcance por edificio). Una transición aceptada por error resuelve o cancela pedidos de docentes. `RoomRequestTransitionValidatorTest` tiene 8 tests que agrupan varias transiciones cada uno; PIT dirá si cada rama está observada.
3. `ar.edu.utn.frc.siga.auth.security`: `DefaultBuildingScopeResolver`, `JwtService`, `LoginRateLimiter`. Es el núcleo de autorización por edificio que usan allocation, preview y roomrequest; un mutante sobreviviente acá equivale a un bypass.

Descarté `ar.edu.utn.frc.siga.optimizer.constraint` aunque tiene la peor cobertura de ramas: sus restricciones son lambdas que solo se ejecutan dentro del solver, y sin `ConstraintVerifier` (TEST-04) PIT reportaría casi todo como sin cobertura, sin información nueva.

## Resumen

La suite tiene 2117 métodos declarados sin sysacad: 66% unitarios, 2% slices y 32% integración
amplia sobre un contexto y un Postgres compartidos. La forma es de pirámide, con el medio angosto
casi vacío. Las prácticas de base son sanas: no hay `@Disabled`, el orden no importa, Awaitility
reemplaza a los sleeps y el BVA es explícito en solapes, capacidad, límites de exportación y
truncado. El hueco serio es de autorización: los endpoints de escritura de allocation, preview,
imports y la mayoría de room-requests no tienen un test de 403 por permiso faltante (TEST-02), y el
alcance por edificio en allocation solo se prueba con el resolver mockeado (TEST-03). Le siguen las
restricciones del optimizer sin `ConstraintVerifier` (TEST-04) y un borde de vencimiento sin probar
(TEST-05). Hay un test manual que corre en cada build sin assert (TEST-01). Conteo: 0 Críticas,
1 Alta, 4 Medias, 3 Bajas.

## Preguntas para el usuario

Ninguna bloqueante.
