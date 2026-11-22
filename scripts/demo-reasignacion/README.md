# Datos demo: reasignación por rango

Datos sintéticos para la demo del martes 24/11/2026. Extienden todas las clases regulares que terminan
el 30/11/2026 hasta el 28/02/2027 (unas 13 semanas más), como si se hubiera ampliado el período
académico, para que "Clase completa" alcance decenas de fechas. No cambian código de la app.

| Archivo | Qué hace |
|---|---|
| `preparar.sql` | Extiende eventos y períodos, crea las clases nuevas con la misma aula de la última semana. Todo o nada. |
| `verificar.sql` | Solo lectura: resumen, choques comparados con la semana real, feriados, clases con dos días y candidatos para `probar.sh`. |
| `probar.sh` | Prueba contra la API la reasignación por rango y por lotes: impacto, rango temporal, 409 por aula ocupada, lote que se pisa a sí mismo (`SAME_BATCH`) y lote que destraba el conflicto corriendo a la otra clase (a una alternativa del impacto si no entra en el aula liberada). Deja todo como estaba, también si corta a mitad de camino. |
| `limpiar.sql` | Deshace `preparar.sql` y restaura el fin de los períodos desde la auditoría. |

Los dos SQL de escritura dejan rastro en la auditoría ("Corrección manual") con las funciones
`auditoria_abrir_revision` y `auditoria_registrar`.

## Antes de correrlo

- Apagar los envíos externos desde la pantalla de Configuración (o `PUT /v1/settings`):
  `sysacad.write.mode = OFF`, `sysacad.autogestion.mode = OFF`, `notifications.enabled = false` y
  `notifications.channel.email.enabled = false`. Un UPDATE directo en `configuracion` no alcanza con la
  app levantada: la app lee la configuración al arrancar y la guarda en memoria. `preparar.sql` aborta
  si `sysacad.write.mode` no está en `OFF`.
- La base tiene que estar migrada a la versión actual de la app (usa `habilitado_siga`, V71). En
  dev-snapshot, levantar la app una vez antes.

## Uso

```bash
# dev-snapshot (local)
docker exec -i dev-snapshot-db-container psql -U myuser -d mydatabase -v usuario=admin@frc.utn.edu.ar \
  < scripts/demo-reasignacion/preparar.sql
docker exec -i dev-snapshot-db-container psql -U myuser -d mydatabase < scripts/demo-reasignacion/verificar.sql

# probar.sh con una fila de "Candidatos" de verificar.sql
SIGA_BASE_URL=http://localhost:8080/api SIGA_EMAIL=admin@frc.utn.edu.ar SIGA_PASSWORD=... \
  scripts/demo-reasignacion/probar.sh <evento> <aula actual> <aula libre> <aula ocupada>

docker exec -i dev-snapshot-db-container psql -U myuser -d mydatabase -v usuario=admin@frc.utn.edu.ar \
  < scripts/demo-reasignacion/limpiar.sql
```

En test, lo mismo contra su contenedor de Postgres con el usuario de la app (alcanza con DML).

## Importante

- **`limpiar.sql` va antes del sync de SysAcad del domingo 29/11 a las 3:00.** El sync identifica cada
  clase por sus fechas: si encuentra las extendidas, crea copias con fin 30/11 y da de baja las
  extendidas junto con sus clases futuras.
- Cada reasignación de la demo, incluidas las de `probar.sh`, queda registrada en "Movimientos por
  edificio". Con las notificaciones apagadas no sale ningún mail.
- En la demo, elegir fechas desde el 1/12. `limpiar.sql` no revierte cambios de aula en fechas hasta
  el 30/11.
- Hay clases en feriados (7, 8, 25 y 26/12), igual que en el resto del año: la app no saltea feriados
  al generar clases. Los feriados de 2027 todavía no están cargados.
- Los choques de aula y las clases sin aula vienen de los datos reales: la última semana real
  (23/11) tiene 145 choques y 300 clases sin aula de 1827, y las semanas demo quedan en números parecidos.
