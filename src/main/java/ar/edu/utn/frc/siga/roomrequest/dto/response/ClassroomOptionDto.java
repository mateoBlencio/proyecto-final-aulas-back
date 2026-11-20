package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClassroomOptionDto(
        Long id,
        Integer roomNumber,
        String buildingName,
        UnavailableReason unavailableReason,
        @Schema(description = "false si SysAcad inhabilitó el aula (solo informativo). Solo lo trae la respuesta del personal; las respuestas públicas lo omiten.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean enabledInSysacad
) {

    public ClassroomOptionDto(Long id, Integer roomNumber, String buildingName, UnavailableReason unavailableReason) {
        this(id, roomNumber, buildingName, unavailableReason, null);
    }

    public ClassroomOptionDto(Long id, Integer roomNumber, String buildingName) {
        this(id, roomNumber, buildingName, null, null);
    }

    public ClassroomOptionDto withoutSysacad() {
        return new ClassroomOptionDto(id, roomNumber, buildingName, unavailableReason, null);
    }
}
