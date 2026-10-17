---
name: commit-msg
description: Genera el mensaje de commit en el formato del repo a partir de los cambios staged/unstaged. Solo imprime el mensaje; nunca ejecuta git.
disable-model-invocation: true
---

# commit-msg

Producí UN mensaje de commit para los cambios actuales. No ejecutás `git commit`
ni ningún git que escriba: solo mostrás el texto para que el usuario lo copie.

## Pasos

1. Corré `git status` y `git diff` (y `git diff --staged`) para ver qué cambió.
2. Si hay varios cambios sin relación, decilo y proponé cómo separarlos en commits.
3. Redactá el mensaje:

   ```
   tipo(módulo): descripción
   ```

   - **tipo**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.
   - **módulo**: el paquete bajo `ar.edu.utn.frc.siga.*` afectado
     (`roomrequest`, `allocation`, `sysacad`, `space`, `academic`, `events`,
     `optimizer`, `preview`, `ingest`, `settings`, `auth`, `common`). Si el cambio
     es transversal, usá el más representativo o el área (`ci`, `build`).
   - **descripción**: una sola línea, corta, en **español**, en jerga de negocio,
     solo lo más importante del cambio. Sin punto final.

   Ejemplo: `fix(roomrequest): rechazar el pedido si el día o la fecha ya tienen una clase`

4. Mostrá el mensaje en un bloque para copiar. Si aplica, recordá al usuario que
   el commit se hace a mano (regla de `CLAUDE.md`).
