package ar.edu.utn.frc.siga.allocation.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record AllocationLastRevisionDto(String user, String operationId) {
}
