# Seguridad SIGA — guía para el equipo frontend

Este documento describe el contrato HTTP que expone el backend a partir de la nueva capa de
seguridad (JWT stateless + refresh tokens + rate limiting). Está escrito contra el estado real
del código en la rama `feature/seguridad-inicial`.

## Modelo de autenticación

- **JWT stateless por header**, no hay cookies ni sesión de servidor: cada request autenticado
  debe llevar `Authorization: Bearer <accessToken>`.
- **No usamos `credentials: 'include'` ni cookies** — el CORS del backend tiene
  `allowCredentials: false`. Si el fetch/axios del front manda `credentials: "include"` por
  costumbre, sáquenlo: no hace nada útil acá y puede romper el preflight.
- **Dos tokens por login**: un `accessToken` (JWT, corta duración) y un `refreshToken` (opaco,
  se usa sólo contra `/auth/refresh` y `/auth/logout`, nunca se manda como Bearer).
- **Access token corto: 20 minutos** (`expiresInSeconds` en la respuesta). Al vencer, cualquier
  request a `/v1/**` da 401 y hay que pedir un par nuevo con `/auth/refresh`.
- **Refresh token de un solo uso (rotación)**: cada `/auth/refresh` exitoso invalida el
  `refreshToken` usado y devuelve uno nuevo. **Guardar siempre el `refreshToken` de la última
  respuesta**, nunca reusar uno viejo a propósito.
- **Login sólo con email institucional** `@frc.utn.edu.ar` — cualquier otro dominio da 401
  inmediato (sin consumir el rate limit de login).

## Endpoints de auth

### `POST /api/auth/login`

Request:
```json
{ "email": "admin@frc.utn.edu.ar", "password": "..." }
```

Response `200`:
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInSeconds": 1200,
  "refreshToken": "b64-url-opaco...",
  "refreshExpiresInSeconds": 2592000,
  "email": "admin@frc.utn.edu.ar",
  "roles": ["ADMIN"]
}
```

Errores: `401` credenciales inválidas / email no institucional / usuario deshabilitado (mismo
shape de error para los tres casos, no revela cuál fue — no hacer lógica de UI que dependa de
distinguirlos); `429` si ya hubo 5 intentos fallidos con ese email en los últimos 15 minutos
(ver "Rate limiting" abajo); `400` si `email`/`password` no pasan validación de forma (`email`
vacío o sin forma de email, `password` vacío).

### `POST /api/auth/refresh`

Request:
```json
{ "refreshToken": "b64-url-opaco..." }
```

Response `200`: mismo shape que login (par nuevo completo, `accessToken` + `refreshToken`).

Errores: `401` si el token no existe, expiró, o ya fue usado y la reutilización se considera
sospechosa (ver nota de reintentos abajo). **Un 401 acá significa que hay que mandar al usuario
a loguearse de nuevo** — no hay forma de recuperar la sesión sin credenciales.

**Nota sobre reintentos de red**: si el front reintenta un `/auth/refresh` que en realidad sí
había llegado a rotar en el servidor (timeout de red, doble click, etc.) *dentro de un margen de
~10 segundos*, el backend lo trata como reintento legítimo y devuelve un par fresco en vez de
401 — así que un reintento inmediato con el mismo `refreshToken` es seguro. Pasado ese margen,
un `refreshToken` ya usado se trata como robo/reuso genuino y **invalida todas las sesiones
activas de ese usuario** (fuerza re-login en todos los dispositivos). Conclusión práctica: no
disparen dos refresh en paralelo para el mismo usuario, y no reintenten un refresh fallido
después de mucho tiempo esperando.

### `POST /api/auth/logout`

Request: igual que refresh (`{ "refreshToken": "..." }`).

Response: `204 No Content`. Idempotente (llamarlo dos veces con el mismo token no rompe nada).
Sólo cierra esa sesión puntual (ese refresh token), no todas las sesiones del usuario en otros
dispositivos.

**El logout no invalida el `accessToken` en uso** (JWT stateless, no hay revocación
instantánea) — el front debe igual descartarlo localmente. Sigue siendo válido en el backend
hasta que expire solo (≤20 min), pero sin `refreshToken` no se puede renovar.

## Cómo usar el access token

```
Authorization: Bearer eyJhbGciOi...
```

en cada request a `/v1/**`. Ante un `401` en cualquier endpoint de negocio, la secuencia
esperada del front es: intentar `/auth/refresh` una vez con el `refreshToken` guardado → si da
200, reintentar el request original con el `accessToken` nuevo → si el refresh da 401, mandar al
login.

## Control de acceso por rol

Hay dos roles: `ADMIN` y `AUXILIAR_AULICO`. Un usuario autenticado pero sin el rol requerido
recibe `403` (no `401`) — son casos distintos: 401 = "no sé quién sos", 403 = "sé quién sos pero
no podés hacer esto". Vale la pena distinguirlos en la UI (403 no debería mandar al login).

| Recurso | Endpoints | Rol requerido |
|---|---|---|
| Edificios | `GET /v1/buildings` | `ADMIN`, `AUXILIAR_AULICO` |
| Aulas (lectura) | `GET /v1/classrooms`, `GET /v1/classrooms/{id}` | `ADMIN`, `AUXILIAR_AULICO` |
| Aulas (escritura) | `POST/PUT/DELETE /v1/classrooms...` | `ADMIN` |
| Eventos académicos (lectura) | `GET /v1/events...` | `ADMIN`, `AUXILIAR_AULICO` |
| Eventos académicos (alta) | `POST /v1/events/recurring`, `POST /v1/events/unique` | `ADMIN` |
| Asignaciones | todos los endpoints de `/v1/allocations/**` | `ADMIN`, `AUXILIAR_AULICO` |
| Solver (preview) | `POST /v1/solver/preview` | `ADMIN`, `AUXILIAR_AULICO` |
| Importación Excel | `POST /v1/excelimports` | `ADMIN` |

**Cambios de rol o deshabilitación de un usuario tardan hasta 20 minutos en reflejarse**: los
roles viajan como claim dentro del JWT y no se releen de la base en cada request. Si un admin le
cambia el rol a alguien (hoy sólo se hace directo en base, no hay endpoint todavía), ese usuario
sigue viendo el rol viejo en la UI hasta que su access token expire y pase por `/auth/refresh`.
No es un bug del front si ven esto — es el trade-off elegido del lado del backend.

## Formato de errores

Todos los errores (auth y de negocio) devuelven `application/problem+json`
([RFC 7807](https://www.rfc-editor.org/rfc/rfc7807)):

```json
{
  "type": "about:blank",
  "title": "Invalid credentials",
  "status": 401,
  "detail": "Email o contraseña incorrectos"
}
```

Validación de campos (`400`, p. ej. body de login mal formado) agrega un campo extra `errors`:

```json
{
  "title": "Validation failed",
  "status": 400,
  "errors": { "email": ["must be a well-formed email address"] }
}
```

`403` (rol insuficiente) y `401` (sin token / token inválido o vencido) generados por el filtro
de seguridad usan el mismo formato `problem+json`, con `title` genérico ("Unauthorized" /
"Forbidden") — no dependan del `detail` para lógica de UI, sólo del `status`.

## Rate limiting — respuestas 429 posibles

Hay dos límites independientes, ambos con status `429` y header `Retry-After` (segundos):

1. **General, por IP, 100 requests/minuto** — aplica a *cualquier* endpoint, incluido
   `/auth/login`. Pensado como red de seguridad contra abuso amplio, no debería tocarlo el uso
   normal de la UI. Si lo ven en desarrollo con tráfico legítimo, avisen — puede ser un loop de
   polling o requests duplicados del front, no algo a ignorar.
2. **Login, por email, 5 intentos fallidos cada 15 minutos** — sólo cuenta contra
   `/auth/login` y sólo intentos con credenciales incorrectas (un email fuera del dominio
   institucional no cuenta). Si el usuario ve un 429 acá, el mensaje de UI apropiado es "muchos
   intentos fallidos, esperá unos minutos", no un error genérico.

No hay account lockout ni backoff progresivo — es sólo este corte duro por ventana. No hace
falta que el front implemente su propio throttling de reintentos de login, pero sí debe manejar
el 429 con un mensaje claro (no reintentar automáticamente en loop).

## CORS

El backend sólo acepta origins explícitamente whitelisteados en `cors.allowed-origins`
(configuración del backend, no del front). **Necesitamos que nos confirmen la URL exacta del
frontend desplegado** (dev, staging, prod — pueden ser varias) para agregarla a esa lista; sin
eso, cualquier request desde el navegador falla en el preflight con error de CORS aunque el
backend esté funcionando bien. No hay credentials (cookies) en juego, así que no hace falta
`credentials: 'include'` del lado del front (ver arriba).

## Endpoints públicos (sin token)

- `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
- `GET /api/actuator/health` (y subpaths)
- Swagger UI / OpenAPI (`/api/swagger-ui/**`, `/api/v3/api-docs/**`) — **deshabilitado por
  default** (`springdoc.swagger-ui.enabled: false` en la config base); confirmen con el backend
  si está habilitado en el ambiente contra el que están probando.

Cualquier otra ruta bajo `/api/v1/**` exige `Authorization: Bearer`.

## Recomendación de manejo en el front (no impuesto por el backend, pero relevante)

El `refreshToken` es un secreto de larga vida (30 días por default) que viaja en el body JSON,
no en una cookie `httpOnly` — el backend no toma ninguna decisión de storage por ustedes.
Guardarlo en `localStorage` es cómodo pero expone superficie a XSS; si el front tiene control
sobre el riesgo de XSS (CSP propia, sanitización), es una decisión válida, pero vale la pena que
quede como decisión consciente del equipo y no un default accidental.
