package ar.edu.utn.frc.siga.allocation.service.command;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import java.util.List;
import java.util.Map;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
/** {@code swappedMirrors}: espejo -> aula nueva, cuando el espejo ocupaba el aula original del principal y tomó la que este dejó. {@code deactivated}: ocurrencias cuya aula está desactivada; no se escriben y no cuentan como ocupadas. */
public record RestoreResult(List<Long> restored, List<OccurrenceConflictDto> occupied, List<Long> deactivated,
        Map<Long, Long> swappedMirrors) {
}
