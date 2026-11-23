package ar.edu.utn.frc.siga.common.realtime;

import ar.edu.utn.frc.siga.common.security.BuildingScope;

/** Quién recibe los avisos de un tema. Lo implementa el módulo que conoce las reglas de lectura de esos datos. */
public interface LiveTopicAudience {

    LiveTopic topic();

    /** Alcance del usuario autenticado en el hilo actual; {@link BuildingScope#denied()} si no ve el tema. */
    BuildingScope currentScope();
}
