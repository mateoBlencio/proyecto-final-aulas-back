# Pipeline de CD y estado de MailHog en ServerPF

Relevamiento por SSH en ServerPF (2026-09-20), hecho para saber qué falta
para tener MailHog desplegado ahí como backend del canal de email del
módulo `notification`. Referencia interna del equipo, no reemplaza la nota
abierta en `.claude/docs/notification/infraestructura-smtp.md` sobre el
SMTP real de producción.

## Pipeline de CD

El deploy a `dev` y `test` es automático de punta a punta vía GitHub
Actions con runners self-hosted en ServerPF, sin ningún paso manual:

- Runner del backend: servicio systemd
  `actions.runner.mateoBlencio-proyecto-final-aulas-back.debian-server.service`.
- Runner del frontend: servicio systemd
  `actions.runner.ZoiLyp-proyecto-final-aulas-front.server-frontend.service`.

Un push a `develop` dispara `.github/workflows/cd-dev.yml`: el job `test`
corre `./mvnw -B --no-transfer-progress verify`, y si pasa, `deploy-dev`
hace el deploy. `deploy-dev` valida primero que exista
`/home/adminmate/proyecto-aulas/proyecto-final-aulas-back/.env` con las
variables que pide el bloque `backend-dev:` de `docker-compose.deploy.yml`;
el chequeo extrae con `awk`/`grep` solo las variables sin valor por
default (`${VAR}`, no `${VAR:-default}`) para no exigir ahí las del otro
perfil. Después corre
`docker compose -f docker-compose.deploy.yml --profile dev --env-file
$ENV_FILE up -d --build` y espera con reintentos a que
`proyecto-final-aulas-back-backend-dev-1` reporte `healthy`. Existe un
`cd-test.yml` con la misma lógica para el perfil `test`, más un `ci.yml`
que no se inspeccionó en este relevamiento.

## MailHog: nada que hacer a mano en el servidor

Al momento de este relevamiento, el checkout que usa el runner en
`/home/github-runner/actions-runner/_work/proyecto-final-aulas-back/proyecto-final-aulas-back/docker-compose.deploy.yml`
está en `develop`, y esa rama todavía no tiene los servicios `mailhog-dev`
y `mailhog-test`. Esos servicios entraron al repo en el commit `b40fad8`
(`feature/solicitudes-envio-notificaciones`), sin mergear a `develop`
todavía.

Mergear esa rama y pushear a `develop` alcanza: no hace falta ninguna
acción manual en ServerPF. El próximo deploy automático de `deploy-dev`
crea el container `mailhog-dev` en la red `app-network`, con la imagen
`mailhog/mailhog:v1.0.1` y el puerto `8025` publicado. `backend-dev` ya
apunta ahí por default: `application-dev.yaml` define `spring.mail.host`
como `${MAIL_HOST:mailhog-dev}`, `mail.enabled` como `${MAIL_ENABLED:true}`
y `mail.from` como `${MAIL_FROM:no-reply@siga.local}`. Como esas tres
variables no aparecen en el bloque `environment:` de `backend-dev` en
`docker-compose.deploy.yml`, el chequeo de variables requeridas del
workflow no las exige, y el `.env` del servidor no necesita ningún agregado
para que esto funcione.

## Topología de containers en ServerPF

El `docker ps` relevado mostró `backend-dev`, `backend-test`,
`frontend-dev` y `frontend-test` sin puerto publicado al host: exponen
8080 (backend) u 80 (frontend) solo puertas adentro de `app-network`. Todo
el tráfico externo entra por un container `apache-proxy` (imagen
`proxy-apache-apache-proxy`, configuración en
`/home/adminmate/proxy-apache`), que sí publica `8090` y `8091` al host.

`postgres_entorno_dev` (puerto de host `5432`) y `postgres_entorno_test`
(puerto de host `5433`) corren fuera de este repo, sin estar en
`docker-compose.deploy.yml`. Hay también un `sqlserver-test` detenido, sin
más contexto relevado sobre su uso. La red `app-network` es una red bridge
externa con subnet `172.19.0.0/16`, y es la misma red a la que se van a
sumar `mailhog-dev`/`mailhog-test` una vez desplegados.

## Firewall y acceso a la UI de MailHog

`ufw` está activo en ServerPF con política `DROP` en `INPUT` y `FORWARD`, y
solo permite entrante `22/tcp`. Eso incluye a MailHog: ni el puerto 8025
(UI web) ni el 1025 (SMTP) van a ser alcanzables desde afuera del servidor
una vez que el container exista, salvo que alguien abra el puerto en `ufw`
o se acceda por túnel SSH.

`CORS_ALLOWED_ORIGINS_DEV` en el `.env` del servidor incluye una IP con
forma de Tailscale (`<ip-tailscale>`), lo que sugiere que el equipo ya tiene
una tailnet armada para acceso privado a ServerPF. Si es así, esa sería la
vía más directa para entrar a `http://<ip-tailscale>:8025` sin tocar
`ufw`. Esto quedó sin confirmar con el equipo: falta preguntar si la
tailnet es en efecto el mecanismo pensado para llegar a la UI de MailHog, o
si van a abrir el puerto públicamente.

## `.env` del servidor

El `.env` que usa `deploy-dev` vive en
`/home/adminmate/proyecto-aulas/proyecto-final-aulas-back/.env`, separado
del working directory efímero del runner
(`/home/github-runner/actions-runner/_work/...`). Ese path no es un
repositorio git: es solo la ubicación fija del `.env` que el workflow lee
vía `--env-file`.

## Punto abierto

Confirmar con el equipo si el acceso a la UI de MailHog en ServerPF
(`:8025`/`:8026` para test) va a ser por la tailnet ya usada en
`CORS_ALLOWED_ORIGINS_DEV`, o si hace falta abrir esos puertos en `ufw`.
