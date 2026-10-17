---
name: bruno-sync
description: Actualiza la colección Bruno en bruno/ cuando se agrega o cambia un endpoint HTTP del backend. Detecta los controllers tocados en el diff y crea/edita el .bru correspondiente (método, path /v1/..., auth, body, params, docs). Usar tras agregar/modificar/borrar un endpoint o antes de mergear una feature con cambios de API. Palabras clave: bruno, colección, endpoint, request, .bru, API.
---

# bruno-sync

Mantené la colección Bruno (`bruno/`, entorno `local`) al día con los endpoints del
backend. `CLAUDE.md` lo exige: "al agregar o cambiar un endpoint, actualizarla".

## Pasos

1. **Detectá qué cambió.** Mirá el `git diff` en busca de archivos `*Controller.java`:
   endpoints nuevos, paths modificados, params/body nuevos, roles (`@PreAuthorize`)
   o endpoints borrados.

2. **Ubicá la carpeta destino.** La colección agrupa por área en español, no por
   módulo: `bruno/Académico/`, `bruno/Espacios físicos/`, `bruno/Asignaciones/`,
   `bruno/Solicitudes de aula/`, `bruno/Tipos de recurso/`, `bruno/Eventos/`,
   `bruno/Configuración/`, `bruno/Importación de Excel/`, `bruno/Sysacad/`,
   `bruno/Usuarios/`, `bruno/Vista previa de asignación/`, `bruno/auth/`.
   Elegí la que corresponda al recurso; si no hay uná clara, preguntá.

3. **Creá o editá el `.bru`.** Formato (ver ejemplos existentes en la carpeta):

   ```
   meta {
     name: <nombre legible en español>
     type: http
     seq: <siguiente número libre en la carpeta>
   }

   <get|post|put|patch|delete> {
     url: {{baseUrl}}/v1/<path>
     body: <none|json>
     auth: inherit
   }

   params:query {
     <param>: <valor de ejemplo>
     ~<paramOpcional>: <valor>     // el ~ deja el param desactivado
   }

   body:json {
     { ... payload de ejemplo realista ... }
   }

   docs {
     <MÉTODO> /v1/<path>. Rol: <SUBSECRETARIA|AUXILIAR_AULICO|público>.
     <reglas de validación y forma de la respuesta, breve>
   }
   ```

   - `{{baseUrl}}` ya resuelve a `http://localhost:8080/api` (no hardcodees host).
   - `auth: inherit` salvo endpoints públicos (`POST /v1/room-requests`,
     `GET /v1/room-requests/catalog/**`, `/auth/**`), donde va `auth: none`.
   - Body de ejemplo: valores plausibles del dominio, no `"string"`.
   - Endpoint borrado ⇒ borrá el `.bru` y renumerá `seq` si quedó hueco molesto.

4. **Verificá** que el `.bru` nuevo abre bien (sintaxis de bloques balanceada) y
   listá lo que tocaste.

No corras la colección ni hagas requests reales; solo editás archivos.
