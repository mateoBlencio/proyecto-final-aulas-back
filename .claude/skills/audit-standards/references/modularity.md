# Arquitectura modular: Spring Modulith 2.0

Vigencia: verificado el 2026-10-13 con la referencia 2.0. El repo usa 2.0.5 y la versión
estable más nueva es la 2.1.x.

Fuentes:
- https://docs.spring.io/spring-modulith/reference/2.0/verification.html
- https://docs.spring.io/spring-modulith/reference/2.0/documentation.html
- https://docs.spring.io/spring-modulith/reference/2.0/events.html

## Lo que `ApplicationModules.verify()` chequea

- Sin ciclos entre módulos.
- Acceso a otros módulos solo por su API (paquete base o `@NamedInterface`).
- `allowedDependencies` de `@ApplicationModule`, si se declaran.

`detectViolations()` devuelve las violaciones sin lanzar. En el repo lo corre `ModularityTests`.

## Lo que `verify()` no ve (revisar a mano)

- Ciclos encubiertos por una interfaz en `common`: el módulo A define en `common` un puerto que
  implementa B, y B depende de A. En 2026-10-13: `events` y `allocation` vía
  `common.service.OccurrenceAllocationQuery`. Arreglo: mover el puerto a la API del módulo
  dueño.
- Acoplamiento por SQL: un módulo que lee tablas de otros por JDBC o SQL nativo (`dashboard`,
  `audit`) no aparece como dependencia.
- API demasiado ancha: exponer el servicio completo, con DTOs de request HTTP, como
  `@NamedInterface`. Las validaciones de esos DTOs no corren cuando otro módulo lo llama.
- `common` como cajón de sastre: tipos de dominio en `common` en vez de su módulo.
- Dependencias declaradas que el código no usa.

## Eventos

- `@ApplicationModuleListener` = transaccional, asíncrono, después del commit.
- El Event Publication Registry (`spring-modulith-starter-jpa`, tabla `event_publication`)
  guarda las publicaciones. Si un listener falla, la publicación queda incompleta y nada la
  reintenta salvo que se configure `spring.modulith.events.republish-outstanding-events-on-restart`
  o se use `IncompleteEventPublications` (por ejemplo desde un `@Scheduled`).
- Consulta de control: `SELECT count(*) FROM event_publication WHERE completion_date IS NULL;`

## Documentación generada

`new Documenter(ApplicationModules.of(SigaApplication.class)).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml()`
escribe diagramas C4/UML y el Application Module Canvas bajo `target/` (carpeta `spring-modulith-docs` por defecto). Sirve
de entregable de la dimensión de arquitectura. Si no se quiere un test permanente, correrlo
desde un test temporal y no commitearlo.

## Señales de clase dios

Usar graphify (`god_nodes`) y medir líneas y responsabilidades. Una entidad rica con muchas
aristas dentro de su propio módulo no es una clase dios; un servicio de 500+ líneas que mezcla
CRUD con sincronización externa sí lo es (`AcademicEventServiceImpl` en 2026-10-13).
