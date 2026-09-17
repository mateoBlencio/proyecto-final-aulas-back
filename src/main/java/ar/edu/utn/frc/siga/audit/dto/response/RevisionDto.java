package ar.edu.utn.frc.siga.audit.dto.response;

import java.time.LocalDateTime;

import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record RevisionDto<T>(
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        T snapshot) {
}
