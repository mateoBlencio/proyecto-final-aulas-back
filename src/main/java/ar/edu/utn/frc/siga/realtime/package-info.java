/**
 * Avisa en tiempo real a los navegadores conectados que cambiaron datos que están mirando. El aviso no lleva datos:
 * solo el tema ({@link ar.edu.utn.frc.siga.common.realtime.LiveTopic}), y el front vuelve a pedir lo afectado por
 * los endpoints de siempre, con sus permisos. Cada módulo declara qué entidades disparan avisos
 * ({@link ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper}) y quién los recibe
 * ({@link ar.edu.utn.frc.siga.common.realtime.LiveTopicAudience}).
 *
 * <p>Las conexiones viven en memoria: con más de una instancia del back haría falta repartir los avisos entre
 * instancias (por ejemplo con LISTEN/NOTIFY de Postgres).
 */
@ApplicationModule(allowedDependencies = { "common", "space :: api" })
package ar.edu.utn.frc.siga.realtime;

import org.springframework.modulith.ApplicationModule;
