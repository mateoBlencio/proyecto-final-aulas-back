package ar.edu.utn.frc.siga.space.dto.response;

import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClassroomPermissionTargetDto(
        PermissionTargetKind targetKind,
        Long targetId,
        String name,
        @Schema(description = "Nombre de la especialidad de la materia. Null si el target no es SUBJECT o la materia ya no existe.")
        String specialtyName,
        @Schema(description = "Abreviatura de la especialidad de la materia. Null si el target no es SUBJECT o la materia ya no existe.")
        String specialtyAbbreviation,
        @Schema(description = "Código de la especialidad de la materia. Null si el target no es SUBJECT o la materia ya no existe.")
        Integer specialtyCode,
        @Schema(description = "Código del plan de estudio de la materia. Null si el target no es SUBJECT o la materia ya no existe.")
        Integer studyPlanCode
) {
}
