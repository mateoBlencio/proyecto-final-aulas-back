# ADR-005: Open Session In View desactivado

## Estado

Aceptado — 2026-07-10

## Contexto

Spring Boot habilita Open Session In View (OSIV) por defecto: la sesión de
Hibernate (y la conexión JDBC subyacente) queda abierta durante todo el ciclo
de vida del request HTTP, no solo durante la transacción del servicio. Esto
permite que capas fuera de la transacción (típicamente serialización JSON en
el controller) inicialicen relaciones `LAZY` sin lanzar
`LazyInitializationException`, a costa de mantener una conexión del pool
tomada más tiempo del necesario por request y de esconder accesos a datos
fuera de la transacción que deberían resolverse explícitamente ahí.

Hasta la Fase 4 de este refactor, SIGA no podía desactivarlo con seguridad:

- Las fronteras de módulo eran mixtas (ADR-004) — algunas fachadas `api`
  devolvían entidades JPA, y nada impedía que un consumidor navegara una
  relación `@ManyToOne`/`@OneToOne` ajena fuera de la transacción del módulo
  dueño.
- Varios mappers y composers dependían implícitamente de que la sesión
  siguiera abierta al momento de serializar la respuesta, sin que quedara
  explícito en el código dónde terminaba la transacción y dónde empezaba la
  serialización.

Con la Fase 3 (relaciones cross-módulo reemplazadas por IDs planos) y la
Fase 4 (fronteras `api` que solo exponen DTOs/records, nunca entidades) ya
aplicadas, todo el mapeo entidad→DTO ocurre necesariamente **dentro** de un
método de servicio `@Transactional` — es la única forma en que un DTO llega a
un controller, porque ya no hay ningún camino por el que una entidad JPA
cruce esa frontera. Eso hace que OSIV deje de ser necesario: ya no hay
traversals de relaciones `LAZY` pendientes de resolver en la capa de
serialización.

## Decisión

Se agrega `spring.jpa.open-in-view: false` en `application.yaml` (base,
heredado por todos los perfiles; ningún `application-{perfil}.yaml`
redefine `spring.jpa.open-in-view`).

La sesión de Hibernate y la conexión JDBC quedan atadas exclusivamente a la
transacción del método de servicio (`@Transactional` a nivel de clase en
todos los servicios de `academic`, `space` y `allocation`). El controller
recibe siempre un DTO ya completamente materializado; no hay ningún punto del
código, fuera de un método `@Transactional`, donde se espere poder navegar
una relación `LAZY`.

## Consecuencias

- Cada conexión del pool Hikari se retiene solo durante la transacción de
  negocio, no durante todo el request — relevante en particular para
  endpoints con serialización lenta o payloads grandes (listados paginados,
  exportaciones), que ya no bloquean una conexión mientras Jackson escribe la
  respuesta.
- **`LazyInitializationException` fuera de una transacción pasa a ser un bug
  de diseño, no un accidente de configuración que se "arregla" reactivando
  OSIV.** Si aparece, significa que algún código intenta navegar una relación
  `LAZY` fuera del método de servicio que la resolvió — la corrección es
  mover ese acceso dentro de la transacción (o, si el dato es de otro
  módulo, resolverlo vía la fachada `api` correspondiente), nunca volver a
  poner `open-in-view: true`.
- Mapeo entidad→DTO (mappers MapStruct, composers) debe completarse siempre
  dentro del método `@Transactional` que obtuvo la entidad. Esto ya era la
  práctica del código antes de esta decisión; ahora además está garantizado
  por la ausencia de OSIV, no solo por convención.
- Sin cambio de comportamiento observable en los flujos existentes: se
  verificó que la app sigue arrancando y sirviendo los endpoints con
  `open-in-view: false` (perfil `dev-local`, Docker Compose efímero).

## Alternativas consideradas

- **Mantener OSIV activo y confiar en la disciplina del equipo**: se
  descartó porque OSIV activo es precisamente lo que permite que un futuro
  descuido (un mapper que navega una relación ajena desde el controller, por
  ejemplo) compile y funcione en desarrollo sin que nada lo detecte, hasta
  que el patrón de acceso cambia en producción y aparece contención de
  conexiones. Desactivarlo convierte ese descuido en un error inmediato y
  local (`LazyInitializationException` al ejecutar el flujo), no en un
  problema de performance silencioso.
- **Desactivar OSIV en una fase anterior del refactor**: se descartó porque
  antes de la Fase 4 las fachadas `api` todavía exponían entidades JPA
  directamente; desactivar OSIV en ese momento habría generado
  `LazyInitializationException` en los consumidores cross-módulo que
  navegaban esas entidades fuera de su transacción de origen, sin que la
  causa real (fronteras mixtas) estuviera resuelta todavía.
