---
name: kb-check
description: Audita el código del backend contra la base de conocimiento (KB) de SIGA — reglas de negocio, ADRs y módulos — y reporta dónde el código las incumple o dónde la KB quedó desactualizada. Usar cuando el usuario pida revisar/auditar contra la KB, verificar reglas de negocio, chequear cumplimiento de ADRs, o antes de cerrar una feature grande. Palabras clave: kb, reglas de negocio, ADR, auditoría, incumple, desactualizado, drift.
---

# Auditoría de código contra la KB

Compara este repo (backend SIGA) contra la KB en Obsidian y reporta **desvíos concretos**,
con archivo y línea. No es un code review general: solo lo que contradice la KB.

## Ubicación de la KB

`/Users/mateoblencio/Documents/FACULTAD/ProyectoFinal/Proyecto/knowledge base/proyecto-final-aulas-kb`

Está en `additionalDirectories`, así que se puede leer directo. Si diera error de permisos,
pedile al usuario que corra `/add-dir` con esa ruta.

Estructura relevante:
- `Reglas de negocio/` — reglas duras del dominio. **Máxima prioridad.**
- `ADRs/` — decisiones técnicas vinculantes (ADR-001 a ADR-009).
- `Conceptos/` — semántica del dominio (Aula, Comisión, Ocurrencia, …).
- `Módulos/` — qué hace cada módulo y qué responsabilidades tiene.
- `ASPECTOS A DEFINIR.md` y `PENDIENTE DE IMPLEMENTACIÓN.md` — gaps ya conocidos.

## Antes de empezar: leer los registros de gaps

Leé **primero** `ASPECTOS A DEFINIR.md` y `PENDIENTE DE IMPLEMENTACIÓN.md`.
Lo que ya figura ahí **no es un hallazgo** — es un gap asumido por el equipo.
Reportarlo de nuevo es ruido. En el informe va, como mucho, una línea de "gaps ya registrados: N".

Ojo con las alertas de Obsidian dentro de las notas:
- `[!QUESTION]` → tema abierto, todavía no decidido. **No auditar contra esto.**
- `[!TIP]` inmediatamente después de un `[!QUESTION]` → la duda ya se resolvió; la resolución **sí** es regla vigente.
- `[!CAUTION]` (marca `#pendiente/implementacion`) → gap conocido, no reportar.

## Mapa KB → código

| Módulo KB | Paquete |
|---|---|
| Académico | `ar.edu.utn.frc.siga.academic` |
| Espacios físicos | `ar.edu.utn.frc.siga.space` |
| Eventos | `ar.edu.utn.frc.siga.events` |
| Asignación de aulas | `ar.edu.utn.frc.siga.allocation` |
| Asignación automática | `ar.edu.utn.frc.siga.optimizer` |
| Importación de Excel | `ar.edu.utn.frc.siga.ingest` |
| Común | `ar.edu.utn.frc.siga.common` |
| Solicitudes de aula | `ar.edu.utn.frc.siga.roomrequest` |

Paquetes **sin nota en la KB**: `auth`, `preview`, `settings`.
Código sin documentar también es drift — reportalo, pero en su propia sección
(es deuda de documentación, no un incumplimiento).

## Alcance

Por defecto, auditar **solo lo que cambió** contra `main`:

```
git diff --stat main...HEAD
```

Auditoría completa (los ~7 módulos) solo si el usuario la pide explícitamente:
es cara y tiende a repetir hallazgos viejos.

## Qué buscar

Arrancá por las tres reglas de negocio — son las de mayor valor y las más fáciles de violar
sin darse cuenta:

**Baja lógica** — dar de baja oculta, no borra. Buscá `DELETE FROM`, `repository.delete(`,
`deleteById`, `@Modifying` con delete. Verificá que las queries de listado filtren el flag de baja,
y que las asignaciones históricas **sí** sigan resolviendo la entidad dada de baja (no filtrarla ahí).

**No modificar el pasado** — no se puede tocar la asignación de una ocurrencia ya ocurrida.
En operaciones masivas las fechas pasadas **se saltean**, no abortan el lote.
Chequeá que exista esa validación en los servicios de asignación, y que en el camino
masivo sea "skip", no "throw".

**Operación atómica** — toda operación de asignación (alta, reasignación, importación masiva,
alta/modificación de evento único con aula) es todo-o-nada. Buscá `@Transactional` faltante,
`REQUIRES_NEW` que rompa la atomicidad del lote, o try/catch por ítem que trague la excepción
y deje persistido el resto.

Después los ADRs. Los que más se incumplen en la práctica:
- **ADR-002** manejo de errores centralizado → excepciones manejadas ad-hoc en el controller.
- **ADR-004** DTOs como records → DTOs escritos como clases.
- **ADR-005** OSIV desactivado → acceso lazy fuera de transacción (`LazyInitializationException` latente).
- **ADR-003** MapStruct + composer → mapeo a mano donde debería haber mapper.
- **ADR-001** Spring Modulith → import cruzado entre módulos que saltea la API pública del módulo.

Y por último los `Conceptos/`: que los nombres y la semántica del código coincidan con el dominio
(p. ej. que "Ocurrencia" signifique en el código lo mismo que en la KB).

## Verificar antes de reportar

Cada hallazgo se lee en su archivo antes de entrar al informe. Un `grep` que matchea
no es una violación: puede ser un test, un método sin usar, código muerto, o un caso
que la regla no cubre. **Si no podés apuntar a archivo y línea, no es un hallazgo.**

Cuando el código y la KB discrepan, no asumas de entrada que el código está mal.
Puede ser que la KB haya quedado atrás — y eso es igual de importante de reportar.

## Salida

Markdown en el chat, agrupado así:

1. **Incumple regla de negocio** — lo más grave, siempre primero.
2. **Incumple ADR**.
3. **KB desactualizada** — el código evolucionó y la nota no.
4. **Sin documentar** — código sin nota en la KB.

Cada hallazgo, tres líneas:
- Qué regla/ADR (con el nombre de la nota).
- Dónde: `ruta/Archivo.java:línea`.
- Por qué incumple, en una oración.

Cerrá con una línea sobre qué se auditó (diff vs. módulos completos) y cuántos gaps ya
registrados se saltearon.

**No arregles nada.** Esto reporta. Los arreglos se piden aparte, después de que
el usuario decida cuáles valen la pena — algunos hallazgos se resuelven editando la KB,
no el código.
