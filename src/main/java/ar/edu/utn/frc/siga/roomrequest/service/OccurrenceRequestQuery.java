package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import java.util.Collection;
import java.util.Map;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface OccurrenceRequestQuery {

    /**
     * Estado de pedido por ocurrencia, solo de las que tienen algún ítem. Con varios ítems gana el más
     * fuerte: {@code PENDING}, luego {@code ACCEPTED}, luego {@code CANCELLED}.
     */
    Map<Long, OccurrenceRequestStateDto> findStates(Collection<Long> occurrenceIds);
}
