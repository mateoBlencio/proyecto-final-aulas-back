package ar.edu.utn.frc.classroom_allocation.allocation.controller;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AcademicEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Tag(name = "Eventos Académicos", description = "Creación y gestión de eventos académicos y sus ocurrencias")
public class AcademicEventController {

    private final AcademicEventService academicEventService;

    @GetMapping("/{id}")
    @Operation(summary = "Obtener evento académico por ID",
               description = "Devuelve los datos de un evento académico existente.")
    public ResponseEntity<AcademicEventResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(academicEventService.findById(id));
    }

    @GetMapping("/{id}/occurrences")
    @Operation(summary = "Listar ocurrencias de un evento",
               description = "Devuelve todas las ocurrencias generadas para un evento académico.")
    public ResponseEntity<List<OccurrenceResponseDto>> findOccurrences(@PathVariable Long id) {
        return ResponseEntity.ok(academicEventService.findOccurrencesByEventId(id));
    }

    @PostMapping("/recurring")
    @Operation(summary = "Crear evento recurrente",
               description = "Crea un evento recurrente semanal y genera todas sus ocurrencias.")
    public ResponseEntity<AcademicEventResponseDto> createRecurring(
            @Valid @RequestBody CreateRecurringEventRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicEventService.createRecurringEvent(dto));
    }

    @PostMapping("/unique")
    @Operation(summary = "Crea un evento único",
               description = "Crea un evento que ocurre una única vez y genera una única ocurrencia.")
    public ResponseEntity<AcademicEventResponseDto> createUnique(
            @Valid @RequestBody CreateUniqueEventRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicEventService.createUniqueEvent(dto));
    }
}
