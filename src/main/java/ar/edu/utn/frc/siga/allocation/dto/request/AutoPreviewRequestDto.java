package ar.edu.utn.frc.siga.allocation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Pedido de preview de asignación automática: qué eventos asignar y cuánto tiempo darle al
 * solver. Dos modos mutuamente excluyentes para elegir los eventos: lista explícita en
 * {@code eventIds}, o {@code selectAll=true} para resolver contra todos los eventos sin aula
 * (mismo criterio que {@code GET /unassigned} sin filtros), descontando {@code excludedIds}.
 */
@Schema(description = "Solicitud de preview automático: eventos a asignar + tiempo del solver")
public record AutoPreviewRequestDto(

        @Schema(description = "IDs explícitos de los eventos a asignar (excluyente con selectAll)")
        List<Long> eventIds,

        @Schema(description = "Si es true, asigna todos los eventos sin aula (excluyente con eventIds)")
        Boolean selectAll,

        @Schema(description = "IDs a excluir de la resolución cuando selectAll=true")
        List<Long> excludedIds,

        @Min(1)
        @Max(300)
        @Schema(description = "Tiempo máximo del solver en segundos (1-300)", example = "30", defaultValue = "30")
        Integer timeLimitSeconds
) {
}
