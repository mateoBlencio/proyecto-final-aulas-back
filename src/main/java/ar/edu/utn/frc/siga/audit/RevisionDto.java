package ar.edu.utn.frc.siga.audit;

import java.time.LocalDateTime;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record RevisionDto<T>(
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        T snapshot) {
}
