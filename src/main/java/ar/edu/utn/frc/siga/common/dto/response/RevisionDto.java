package ar.edu.utn.frc.siga.common.dto.response;

import java.time.LocalDateTime;

public record RevisionDto<T>(
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        T snapshot) {
}
