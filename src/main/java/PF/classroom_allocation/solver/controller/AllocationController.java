package PF.classroom_allocation.solver.controller;

import PF.classroom_allocation.solver.doc.AllocationApiExamples;
import PF.classroom_allocation.solver.dto.request.AllocationRequestDto;
import PF.classroom_allocation.solver.dto.response.AllocationPreviewResponseDto;
import PF.classroom_allocation.solver.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignación de Aulas", description = "Operaciones de asignación automática de aulas")
public class AllocationController {

    private final AllocationService allocationService;

    @PostMapping("/preview")
    @Operation(
            summary = "Generar preview de asignación",
            description = "Ejecuta el solver de optimización y devuelve una asignación provisional de aulas para los eventos provistos. No persiste el resultado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Asignación calculada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AllocationPreviewResponseDto.class))
            ),
            @ApiResponse(responseCode = "400", description = "Request inválido (validación fallida)", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error interno del solver", content = @Content)
    })
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AllocationRequestDto.class),
                    examples = @ExampleObject(
                            name = "Ejemplo con 2 materias y 3 aulas",
                            value = AllocationApiExamples.PREVIEW_REQUEST
                    )
            )
    )
    public ResponseEntity<AllocationPreviewResponseDto> preview(
            @Valid @org.springframework.web.bind.annotation.RequestBody AllocationRequestDto request) {
        return ResponseEntity.ok(allocationService.preview(request));
    }
}