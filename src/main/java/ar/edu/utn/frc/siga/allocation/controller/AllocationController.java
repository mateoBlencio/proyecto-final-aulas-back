package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationProblemsResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Asignación manual de aulas para ocurrencias específicas")
public class AllocationController {

    private final AllocationService allocationService;
    private final AllocationProblemService allocationProblemService;

    @GetMapping("/problems")
    @Operation(summary = "Listar problemas de asignación de aulas",
               description = "Devuelve, para la pantalla de asignación automática, tres listados en el rango "
                       + "indicado: eventos sin aula, aulas con sobrecupo y superposiciones de horario-aula. "
                       + "Por defecto 'from' es hoy y 'to' es el fin del período académico activo "
                       + "(o 'from' + 6 meses si no hay período activo con fecha de fin).")
    public ResponseEntity<AllocationProblemsResponseDto> findProblems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/allocations/problems: from={}, to={}", from, to);
        AllocationProblemsResponseDto response = allocationProblemService.findProblems(from, to);
        log.info("Problemas de asignación listados: unassigned={}, overcrowded={}, overlaps={}",
                response.unassigned().size(), response.overcrowded().size(), response.overlaps().size());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Listar asignaciones por fecha",
               description = "Devuelve todas las asignaciones del día indicado.")
    public ResponseEntity<List<AllocationResponseDto>> findByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /v1/allocations: date={}", date);
        List<AllocationResponseDto> allocations = allocationService.findByDate(date);
        log.info("Asignaciones listadas: date={}, count={}", date, allocations.size());
        return ResponseEntity.ok(allocations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener asignación por ID",
               description = "Devuelve los datos de una asignación existente.")
    public ResponseEntity<AllocationResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/allocations/{}", id);
        return ResponseEntity.ok(allocationService.findById(id));
    }

    @PostMapping("/occurrences/{occurrenceId}")
    @Operation(summary = "Asignar aula a ocurrencia",
               description = "Asigna manualmente un aula a una ocurrencia específica. Falla si la ocurrencia ya tiene asignación o si ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> assignManually(
            @PathVariable Long occurrenceId,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("POST /v1/allocations/occurrences/{}: classroomId={}", occurrenceId, dto.classroomId());
        AllocationResponseDto response = allocationService.assignManually(occurrenceId, dto);
        log.info("Asignación creada: id={}, occurrenceId={}", response.id(), occurrenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reasignar aula",
               description = "Cambia el aula de una asignación existente. Falla si la ocurrencia ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> reassign(
            @PathVariable Long id,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("PUT /v1/allocations/{}: classroomId={}", id, dto.classroomId());
        AllocationResponseDto response = allocationService.reassign(id, dto);
        log.info("Asignación reasignada: id={}", id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/batch")
    @Operation(summary = "Reasignar aulas en lote",
               description = "Cambia el aula de múltiples asignaciones en una sola operación atómica. Falla si cualquier ocurrencia ya ocurrió.")
    public ResponseEntity<List<AllocationResponseDto>> batchReassign(
            @Valid @RequestBody BatchReassignRequestDto dto) {
        log.debug("PUT /v1/allocations/batch: moves={}", dto.moves().size());
        List<AllocationResponseDto> response = allocationService.batchReassign(dto);
        log.info("Reasignación en lote completa: moved={}", response.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/from-date")
    @Operation(summary = "Asignar aula desde una fecha",
               description = "Asigna un aula a todas las ocurrencias futuras de un evento recurrente a partir de la fecha indicada. Crea nuevas asignaciones o actualiza las existentes.")
    public ResponseEntity<List<AllocationResponseDto>> assignManuallyFromDate(
            @Valid @RequestBody AllocateFromDateRequestDto dto) {
        log.debug("POST /v1/allocations/from-date: recurringEventId={}, classroomId={}, fromDate={}",
                dto.recurringEventId(), dto.classroomId(), dto.fromDate());
        List<AllocationResponseDto> response = allocationService.assignManuallyFromDate(dto);
        log.info("Asignaciones creadas desde fecha: recurringEventId={}, count={}", dto.recurringEventId(), response.size());
        return ResponseEntity.ok(response);
    }
}
