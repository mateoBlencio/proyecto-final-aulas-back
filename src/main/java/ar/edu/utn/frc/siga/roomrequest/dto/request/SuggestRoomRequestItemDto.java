package ar.edu.utn.frc.siga.roomrequest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Aulas a excluir de la sugerencia del algoritmo")
public record SuggestRoomRequestItemDto(@Size(max = 200) List<Long> excludedClassroomIds) {
}
