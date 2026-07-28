# ADR-011: `subjectId` obligatorio por `eventType`, `commissionId` siempre dependiente de `subjectId`

## Estado

Aceptado — 2026-07-28

## Contexto

El frontend va a mandar un contrato nuevo para el alta de eventos únicos, con un
`eventType` (`Parcial` | `Trabajo Práctico` | `Examen final` | `Otro`) y datos de
materia/comisión que en el contrato original aparecían agrupados como "solo para
Parcial / Trabajo Práctico / Examen final".

Como paso previo (ver el ADR de movimiento de `id_materia`/`id_comision` de
`RecurringEvent` a `AcademicEvent`), `UniqueEvent` pasó a poder referenciar materia y
comisión reales (`subjectId`/`commissionId`, IDs planos al módulo `academic`), igual
que ya hacía `RecurringEvent`. Faltaba decidir la obligatoriedad real de esos dos
campos en el DTO de alta/edición de evento único — el contrato del frontend agrupaba
ambos bajo la misma condición, pero la regla de negocio real que aplica es más fina y
**no es simétrica entre los dos campos**:

- Un **Parcial**, **Trabajo Práctico** o **Examen final** siempre tiene que tener
  materia (`subjectId`), tenga o no comisión asignada todavía.
- La comisión (`commissionId`) **nunca es obligatoria por sí sola**, para ningún
  `eventType` — ni siquiera para los tres anteriores.
- Un evento **`Otro`** puede no tener ni materia ni comisión. Pero **no puede tener
  comisión sin materia**: una comisión siempre pertenece a una materia (`Commission`
  vive bajo un `Subject` en el dominio de `academic`), así que si alguien carga
  `commissionId` en un evento `Otro`, tiene que venir acompañado de `subjectId`.

Dos implementaciones previas de este ADR quedaron descartadas en el camino (ver
"Alternativas consideradas"): primero `subjectId` obligatorio siempre y `commissionId`
condicional (asimetría sin respaldo real), después ambos obligatorios juntos salvo
`OTRO` (todavía no capturaba que `commissionId` nunca es obligatorio por sí solo, ni
que puede depender de `subjectId` incluso dentro de `OTRO`).

## Decisión

`subjectId` y `commissionId` son ambos nullable en `CreateUniqueEventRequestDto`/
`UpdateUniqueEventRequestDto`. La regla real:

```java
private void validateAcademicReference(UniqueEventKind eventType, Long subjectId, Long commissionId) {
    boolean subjectRequired = eventType != UniqueEventKind.OTRO || commissionId != null;
    if (subjectRequired && subjectId == null) {
        throw new MissingAcademicReferenceException(
                "subjectId es obligatorio para eventType=" + eventType
                        + (commissionId != null ? " cuando se indica commissionId" : ""));
    }
}
```

- `subjectId` es obligatorio si `eventType != OTRO`, **o** si viene `commissionId`
  (sin importar el `eventType`) — captura ambas fuentes de obligatoriedad con una sola
  condición.
- `commissionId` nunca se valida como obligatorio por sí mismo: no hay ningún camino
  del código que exija `commissionId` — solo que, si está, necesita `subjectId` al lado.
- Si vienen, ambos se validan igual contra `academic` (`subjectService.findById`/
  `commissionService.findById`, 404 si no existen) — la validación de existencia es
  independiente de la de obligatoriedad, y solo se dispara si el id correspondiente no
  es null.

No hizo falta ningún cambio de esquema: `evento_academico.id_materia`/`id_comision` ya
son columnas nullable (se agregaron así al mover el campo desde `evento_recurrente`),
la restricción de obligatoriedad condicional vive solo en el service.

### Validación adicional (solo en `UniqueEvent`): la comisión tiene que pertenecer a la materia

Lo de arriba valida que ambos campos existan cada uno por su lado (`subjectService
.findById`, `commissionService.findById`), pero no que estén realmente vinculados: se
podía mandar una materia real y una comisión real, sin ninguna relación entre sí (ej.
la comisión de otra materia completamente distinta), y el sistema lo aceptaba igual.
**Esto también aplica hoy a `createRecurringEvent`: es una deuda pre-existente que no
se toca en este ADR.** Acá se agrega, exclusivamente para `UniqueEvent`, un chequeo
extra apoyado en la fachada ya existente `SubjectCommissionService`
(`academic::api`), que resuelve el catálogo materia×comisión:

```java
private void validateCommissionBelongsToSubject(Long subjectId, Long commissionId) {
    if (commissionId == null) {
        return;
    }
    try {
        subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId);
    } catch (ResourceNotFoundException e) {
        throw new InvalidCommissionForSubjectException(
                "La comisión " + commissionId + " no pertenece a la materia " + subjectId + ".");
    }
}
```

Se llama después de `validateAcademicReference` y de los `findById` individuales, en
`createUniqueEvent`/`updateUniqueEvent` — nunca en `createRecurringEvent`. El
`catch` es seguro: para cuando se llega a esta línea, `subjectId`/`commissionId` ya
se validó que existen cada uno por separado, así que la única razón por la que
`findBySubjectAndCommission` puede tirar `ResourceNotFoundException` en este punto es
que el par materia-comisión no está vinculado (no que falte alguno de los dos) — se
traduce a una excepción de dominio propia (`InvalidCommissionForSubjectException`,
400) con un mensaje específico, en vez de dejar pasar el `"SubjectCommission not
found with id: 42-7"` genérico de `academic`.

**Por qué solo en `UniqueEvent` y no también en `RecurringEvent`:** decisión explícita
de alcance — se pidió puntualmente para esta parte del trabajo (eventos únicos).
Extenderlo a `RecurringEvent` (y de paso a `excelimport`, que ya usa esta misma
fachada para otro propósito) queda pendiente como mejora futura, no como parte de
este ADR.

## Consecuencias

- Parcial/Trabajo Práctico/Examen final sin `subjectId` → 400
  (`MissingAcademicReferenceException`), tengan o no `commissionId`.
- Parcial/Trabajo Práctico/Examen final sin `commissionId` pero con `subjectId` →
  persiste sin problema; `commissionId` es información que puede completarse después.
- `Otro` sin `subjectId` ni `commissionId` → persiste sin tocar `academic` en absoluto.
- `Otro` con `commissionId` pero sin `subjectId` → 400: una comisión suelta sin materia
  no es un estado válido para ningún `eventType`.
- La validación es de negocio (cruza `eventType` con dos campos relacionados entre
  sí), no expresable con `@NotNull` simple a nivel de campo — vive como chequeo
  explícito en `AcademicEventServiceImpl`, mismo lugar donde ya vive
  `validateBusinessHours`.
- Cualquier `commissionId` que exista pero no pertenezca al `subjectId` indicado → 400
  (`InvalidCommissionForSubjectException`), **solo para `UniqueEvent`**.
  `createRecurringEvent` sigue sin este cruce — sigue siendo posible crear un evento
  recurrente con una materia y una comisión válidas pero no relacionadas entre sí.
  Es una asimetría deliberada de alcance entre los dos subtipos, no un descuido.

## Alternativas consideradas

- **`subjectId` siempre obligatorio, `commissionId` condicional por `eventType`**
  (primera versión): descartada por no tener respaldo de negocio — mapear la
  puntuación (`?`) del contrato TypeScript del frontend literalmente no es lo mismo
  que la regla de dominio real, y un evento `Otro` puede no tener materia tampoco.
- **`subjectId` y `commissionId` obligatorios juntos salvo `OTRO`** (segunda versión):
  descartada porque exigía `commissionId` para Parcial/TP/Examen final incluso cuando
  la comisión todavía no está definida, y no modelaba que `commissionId` pueda faltar
  también dentro de `OTRO` sin que eso bloquee nada — la única restricción real ahí es
  la dirección de la dependencia (`commissionId` → requiere → `subjectId`), no una
  obligatoriedad conjunta.
- **Ambos siempre obligatorios** (igual que `RecurringEvent`): descartada porque el
  propio contrato del frontend define `Otro` como el caso sin materia/comisión, y
  porque `commissionId` puede legítimamente no existir todavía para un Parcial recién
  cargado.
- **Bean Validation con grupos de validación** (`@Validated` con grupos por
  `eventType`): descartada por sobre-ingeniería para una regla de dos campos con una
  dependencia cruzada; un chequeo explícito en el service es más simple y ya sigue el
  patrón existente del módulo.
