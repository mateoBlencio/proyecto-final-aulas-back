package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import org.springframework.stereotype.Component;

/**
 * Una ocurrencia no sabe en qué edificio está: lo define su asignación. El tablero de Eventos muestra a todos las
 * fechas sin aula, así que un alta, una liberación o un cambio de fecha se avisa sin edificio.
 */
@Component
class OccurrenceLiveChangeMapper implements LiveChangeMapper<Occurrence> {

    @Override
    public Class<Occurrence> entityType() {
        return Occurrence.class;
    }

    @Override
    public LiveChange map(Occurrence current, PreviousState previous) {
        return LiveChange.unscoped(LiveTopic.ALLOCATIONS);
    }
}
