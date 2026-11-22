package ar.edu.utn.frc.siga.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Cadena de causa y efecto de una operación de auditoría")
public record AuditOperationChainDto(
        @Schema(description = "Operaciones (type=OPERATION) por revisión ascendente, la causa primero. Máximo 200.")
        List<AuditLogEntryDto> entries,
        @Schema(description = "true si la cadena está incompleta: se cortó a las 200 operaciones, o el recorrido "
                + "llegó al tope de 10 niveles sin alcanzar la raíz real o las hojas.")
        boolean truncated) {
}
