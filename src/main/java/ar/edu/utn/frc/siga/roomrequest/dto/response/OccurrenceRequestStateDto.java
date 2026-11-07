package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OccurrenceRequestStateDto(Long occurrenceId, Long itemId, OccurrenceRequestState state) {
}
