package ar.edu.utn.frc.classroom_allocation.allocation.controller;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AssignOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Asignación manual de aulas para ocurrencias específicas")
public class AllocationController {

    private final AllocationService allocationService;

    @PostMapping("/occurrences/{occurrenceId}")
    @Operation(summary = "Asignar aula a ocurrencia",
               description = "Asigna manualmente un aula a una ocurrencia específica. Falla si la ocurrencia ya tiene asignación o si ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> assign(
            @PathVariable Long occurrenceId,
            @Valid @RequestBody AssignOccurrenceRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allocationService.assign(occurrenceId, dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reasignar aula",
               description = "Cambia el aula de una asignación existente. Falla si la ocurrencia ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> reassign(
            @PathVariable Long id,
            @Valid @RequestBody AssignOccurrenceRequestDto dto) {
        return ResponseEntity.ok(allocationService.reassign(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar asignación",
               description = "Elimina la asignación de aula para una ocurrencia específica. La ocurrencia en sí queda programada.")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        allocationService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/from-date")
    @Operation(summary = "Asignar aula desde una fecha",
               description = "Asigna un aula a todas las ocurrencias futuras de un evento recurrente a partir de la fecha indicada. Crea nuevas asignaciones o actualiza las existentes.")
    public ResponseEntity<List<AllocationResponseDto>> assignFromDate(
            @Valid @RequestBody AssignFromDateRequestDto dto) {
        return ResponseEntity.ok(allocationService.assignFromDate(dto));
    }
}
