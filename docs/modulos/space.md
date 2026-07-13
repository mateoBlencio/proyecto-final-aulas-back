# Módulo `space`

## Responsabilidad

Catálogo físico: **aulas** (`Classroom`), **edificios** (`Building`) y **tipos de
aula** (`ClassroomType`). Provee a `allocation` y `excelimport` los datos de aula
(por ID + DTO) y el `findOrCreate` usado en la importación.

No decide asignaciones ni conoce eventos; es el módulo de referencia espacial.

## API pública (`::api`)

Servicios marcados `@NamedInterface("api")`:

| Servicio | Métodos clave |
|---|---|
| `ClassroomService` | `create`, `findById`, `findAllAvailable`, `findByIds`, `findAll(filter,pageable)`, `update`, `delete`, `findOrCreate` |
| `BuildingService` | `findAll` (+ lo que consuma excelimport) |
| `ClassroomTypeService` | interfaz existe, **sin controller** |

Endpoints REST:

| Método | Path | Notas |
|---|---|---|
| POST | `/v1/classrooms` | Crea aula (201) |
| GET | `/v1/classrooms/{id}` | Por ID (filtra eliminadas) |
| GET | `/v1/classrooms` | Paginado + filtro (`ClassroomFilter` vía `ClassroomSpecification`) |
| PUT | `/v1/classrooms/{id}` | Actualiza |
| DELETE | `/v1/classrooms/{id}` | Soft-delete (204) |
| GET | `/v1/buildings` | Solo lista edificios activos |

### Detalles de diseño relevantes

- **Soft-delete declarativo**: `@SQLRestriction("eliminado = false")` en las
  tres entidades. El `ClassroomSpecification` **no** repite el filtro (ya lo aplica la
  entidad).
- **`findByIds` NO filtra eliminadas** — a propósito: una asignación histórica puede
  referenciar un aula ya borrada y su dato debe seguir componiéndose sin 404. Contrasta
  con `findById`, que sí filtra. Este matiz es fácil de romper sin un test que lo fije.
- Doble booleano `available` + `deleted`: significan cosas distintas (deshabilitada
  temporal vs baja lógica) pero el solapamiento semántico invita a bugs.

## Estructura interna

`controller` / `dto` (+ `request`, `response`, `ClassroomFilter`) / `mapper` (MapStruct)
/ `model` / `repository` / `service` (+ `impl`) / `specification`.

Entidades → tablas: `Classroom`→`aula` (unique `id_edificio`+`num_aula`),
`Building`→`edificio`, `ClassroomType`→`tipo_aula`.

## Dependencias

Solo `common`. No depende de ningún otro módulo de dominio (hoja del grafo, junto con
`academic` y `solver`).

## Gaps y oportunidades

- **`ClassroomType` sin endpoints.** Existe servicio y entidad, pero no hay controller.
  El front no puede listar/crear tipos vía API; hoy solo se materializan por importación.
- **`Building` solo lectura.** No hay alta/baja/edición de edificios por API.
- **`findOrCreate` sin manejo de carrera.** Dos importaciones concurrentes (o filas que
  resuelven el mismo aula) pueden intentar crear duplicados; la unique constraint
  `(id_edificio, num_aula)` lo frena a nivel BD pero se traduce en excepción cruda, no en
  reintento/resolución. Revisar si importa para el caso de uso.
- **`available` vs `deleted`**: documentar (o unificar) la semántica; el filtro expone
  `available` pero no `deleted` (correcto), aunque no está escrito en ningún lado.
- **`ClassroomFilter` sin validación de rangos**: `capacityMin > capacityMax` produce
  resultado vacío silencioso en lugar de 400.

## Testing

**Estado actual: cero tests.**

### Unitarios recomendados
- `ClassroomSpecification.withFilter`: cada predicado por separado y combinados
  (roomNumber `like` case-insensitive, rango de capacidad, building/type/floor/available).
- `ClassroomServiceImpl.findOrCreate`: rama "encontrada" vs "creada" (`FindOrCreateResult`).
- Mapeo MapStruct `Classroom ↔ ClassroomResponseDto` (incl. buildingId aplanado).

### Integración (Testcontainers) recomendados
- `@SQLRestriction`: un aula con `eliminado=true` no aparece en `findById`/`findAll`
  **pero sí** en `findByIds`.
- Unique constraint `(id_edificio, num_aula)`: alta duplicada falla como se espera.
- Paginación + filtro sobre dataset sembrado.
</content>
