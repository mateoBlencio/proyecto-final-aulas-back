package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
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
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Endpoints de asignación manual de aulas (individual, en lote, desde una fecha) y de detección de problemas de asignación. */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Asignación manual de aulas para ocurrencias específicas")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class AllocationController {

    private final AllocationService allocationService;
    private final AllocationProblemService allocationProblemService;

    /** Eventos con ocurrencias sin aula asignada en el rango indicado. */
    @GetMapping("/unassigned")
    @Operation(summary = "Listar eventos sin aula asignada",
               description = "Devuelve, agrupados por evento, las ocurrencias SCHEDULED (sin aula) en el rango "
                       + "indicado. Por defecto 'from' es hoy y 'to' es el fin del período académico activo "
                       + "(o 'from' + 6 meses si no hay período activo con fecha de fin).")
    public ResponseEntity<List<AcademicEventResponseDto>> findUnassigned(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/allocations/unassigned: from={}, to={}", from, to);
        return ResponseEntity.ok(allocationProblemService.findUnassigned(from, to));
    }

    /** Aulas con sobrecupo (inscriptos > capacidad) en el rango indicado. */
    @GetMapping("/overcrowded")
    @Operation(summary = "Listar aulas con sobrecupo",
               description = "Devuelve los pares evento-aula donde la cantidad de inscriptos supera la capacidad "
                       + "del aula asignada, en el rango indicado. Mismo rango por defecto que /unassigned.")
    public ResponseEntity<List<OvercrowdedAllocationDto>> findOvercrowded(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/allocations/overcrowded: from={}, to={}", from, to);
        return ResponseEntity.ok(allocationProblemService.findOvercrowded(from, to));
    }

    /** Superposiciones de horario en la misma aula en el rango indicado. */
    @GetMapping("/overlaps")
    @Operation(summary = "Listar superposiciones de horario-aula",
               description = "Devuelve los pares de eventos cuyos horarios se superponen en la misma aula, "
                       + "en el rango indicado. Mismo rango por defecto que /unassigned.")
    public ResponseEntity<List<ClassroomOverlapDto>> findOverlaps(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/allocations/overlaps: from={}, to={}", from, to);
        return ResponseEntity.ok(allocationProblemService.findOverlaps(from, to));
    }

    /** Todas las asignaciones vigentes en la fecha indicada. */
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

    /** Datos de una asignación existente por su ID. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener asignación por ID",
               description = "Devuelve los datos de una asignación existente.")
    public ResponseEntity<AllocationResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/allocations/{}", id);
        return ResponseEntity.ok(allocationService.findById(id));
    }

    /** Asigna manualmente un aula a una ocurrencia puntual (source MANUAL). */
    @PostMapping("/occurrences/{occurrenceId}")
    @Operation(summary = "Asignar aula a ocurrencia",
               description = "Asigna manualmente un aula a una ocurrencia específica. Falla si la ocurrencia ya tiene asignación o si ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> allocateManually(
            @PathVariable Long occurrenceId,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("POST /v1/allocations/occurrences/{}: classroomId={}", occurrenceId, dto.classroomId());
        AllocationResponseDto response = allocationService.allocateManually(occurrenceId, dto);
        log.info("Asignación creada: id={}, occurrenceId={}", response.id(), occurrenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Cambia el aula de una asignación existente (source MANUAL). */
    @PutMapping("/{id}")
    @Operation(summary = "Reasignar aula",
               description = "Cambia el aula de una asignación existente. Falla si la ocurrencia ya ocurrió.")
    public ResponseEntity<AllocationResponseDto> reassign(
            @PathVariable Long id,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("PUT /v1/allocations/{}: classroomId={}", id, dto.classroomId());
        AllocationResponseDto response = allocationService.reallocate(id, dto);
        log.info("Asignación reasignada: id={}", id);
        return ResponseEntity.ok(response);
    }

    /** Cambia el aula de todas las occurrences futuras de un evento recurrente (source MANUAL). */
    @PutMapping("/events/{eventId}")
    @Operation(summary = "Reasignar aula de un evento",
               description = "Cambia el aula de todas las ocurrencias futuras de un evento recurrente. Las ocurrencias ya pasadas quedan intactas. Falla si el evento no es recurrente o si ya finalizó.")
    public ResponseEntity<List<AllocationResponseDto>> reassignEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("PUT /v1/allocations/events/{}: classroomId={}", eventId, dto.classroomId());
        List<AllocationResponseDto> response = allocationService.reassignEvent(eventId, dto);
        log.info("Evento reasignado: eventId={}, count={}", eventId, response.size());
        return ResponseEntity.ok(response);
    }

    /** Reasigna varias asignaciones en una única operación atómica (source MANUAL). */
    @PutMapping("/batch")
    @Operation(summary = "Reasignar aulas en lote",
               description = "Cambia el aula de múltiples asignaciones en una sola operación atómica. Falla si cualquier ocurrencia ya ocurrió.")
    public ResponseEntity<List<AllocationResponseDto>> batchReassign(
            @Valid @RequestBody BatchReassignRequestDto dto) {
        log.debug("PUT /v1/allocations/batch: moves={}", dto.moves().size());
        List<AllocationResponseDto> response = allocationService.batchReallocate(dto);
        log.info("Reasignación en lote completa: moved={}", response.size());
        return ResponseEntity.ok(response);
    }

    /** Asigna un aula a todas las occurrences futuras de un evento recurrente desde una fecha (source MANUAL). */
    @PostMapping("/from-date")
    @Operation(summary = "Asignar aula desde una fecha",
               description = "Asigna un aula a todas las ocurrencias futuras de un evento recurrente a partir de la fecha indicada. Crea nuevas asignaciones o actualiza las existentes.")
    public ResponseEntity<List<AllocationResponseDto>> assignManuallyFromDate(
            @Valid @RequestBody AllocateFromDateRequestDto dto) {
        log.debug("POST /v1/allocations/from-date: recurringEventId={}, classroomId={}, fromDate={}",
                dto.recurringEventId(), dto.classroomId(), dto.fromDate());
        List<AllocationResponseDto> response = allocationService.allocateManuallyFromDate(dto);
        log.info("Asignaciones creadas desde fecha: recurringEventId={}, count={}", dto.recurringEventId(), response.size());
        return ResponseEntity.ok(response);
    }
}
