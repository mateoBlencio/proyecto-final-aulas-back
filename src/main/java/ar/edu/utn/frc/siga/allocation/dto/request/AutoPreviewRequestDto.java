package ar.edu.utn.frc.siga.allocation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Solicitud de preview automático: eventos a asignar + tiempo del solver")
public class AutoPreviewRequestDto {

    @NotEmpty
    @Schema(description = "IDs de los eventos a asignar", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> eventIds;

    @Min(1)
    @Max(300)
    @Schema(description = "Tiempo máximo del solver en segundos (1-300)", example = "30", defaultValue = "30")
    private Integer timeLimitSeconds;
}
