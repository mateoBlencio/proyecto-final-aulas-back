# Servidor de correo (SMTP) — pendiente

El módulo `notification` (`.claude/plans/notificaciones-modulo-email.md`) manda mail
por `JavaMailSender`. Hoy no hay ningún servidor SMTP real detrás: en `dev`, `dev-local`
y `test` el canal apunta por defecto a MailHog, un servidor de prueba que captura los
mails y los muestra en una página web en vez de entregarlos a un destinatario real. En
`prod` el canal queda deshabilitado hasta que exista una respuesta a lo que sigue.

Esto no bloquea nada del desarrollo ni de las pruebas. Es un pedido para resolver antes
de que el sistema mande un mail de verdad a un docente.

## Qué pedir, y a quién

Plantear esto por escrito a quien administre la infraestructura de la facultad (o a
quien decida usar un proveedor externo):

1. **Host y puerto de un servidor SMTP** alcanzable desde donde corre el backend: hoy
   eso es una PC dentro de la facultad (ver "Dónde corre hoy" abajo). Confirmar también
   si el servidor usa STARTTLS o SSL directo.
2. **Usuario y contraseña (o API key)** para autenticarse, o confirmación de que el
   relay no pide autenticación para conexiones desde esa red interna.
3. **Qué dirección remitente está autorizada a usar el sistema**
   (`no-reply@frc.utn.edu.ar` es lo que asume el plan por defecto, pero puede ser otra),
   y si hace falta que alguien configure SPF/DKIM en el DNS del dominio para que esos
   mails no terminen marcados como spam en la casilla del docente.
4. **Si no hay servidor propio de la facultad**: autorización para usar un proveedor
   externo (SendGrid, Mailgun, Amazon SES, una cuenta de Gmail con contraseña de
   aplicación) y quién queda a cargo de esa cuenta (quién la crea, quién tiene el
   acceso, qué pasa si esa persona se va del proyecto).

## Dónde corre hoy (contexto para quien resuelva el pedido)

El ambiente de desarrollo y testing (`dev`/`test`) está desplegado en una PC dentro de
la facultad, no en un servidor cloud. El servidor SMTP tiene que ser alcanzable desde
esa red, no necesariamente desde internet.

## Mientras no haya respuesta

`dev`, `dev-local` y `test` usan MailHog por defecto (definido en
`application-dev.yaml`, `application-dev-local.yaml` y `application-test.yaml`, más los
servicios `mailhog`/`mailhog-dev`/`mailhog-test` en `compose.yaml` y
`docker-compose.deploy.yml`). Nadie tiene que configurar nada para que el envío
funcione en esos ambientes; los mails se ven en una interfaz web
(`http://localhost:8025` en `dev-local`, `http://<ip-de-la-pc-de-la-facultad>:8025`/
`:8026` en `dev`/`test`), no llegan a ninguna casilla real. Esto sirve para probar el
mecanismo de envío, no reemplaza la respuesta a los puntos de arriba.

`prod` no tiene ningún override: hasta que se resuelva este pedido, el canal de email
queda deshabilitado ahí (`MAIL_ENABLED=false`), y las notificaciones quedan registradas
como `DISCARDED` en la tabla `notificacion`, sin mandar nada y sin errores.

## Actualizar este documento cuando haya respuesta

Reemplazar esta sección con los valores reales (host, puerto, si pide autenticación,
remitente autorizado) y cargarlos como `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`, `MAIL_FROM` en el `.env` de cada servidor, sumándolos al bloque
`environment:` del servicio correspondiente en `docker-compose.deploy.yml`. No hace
falta tocar código: los perfiles ya leen esas variables si están presentes.
