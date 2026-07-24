package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.AuditHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final AuditHistoryService auditHistoryService;

    @GetMapping("/unassigned")
    @Operation(summary = "Listar eventos sin aula asignada",
               description = "Devuelve, agrupados por evento, las ocurrencias SCHEDULED (sin aula) en el rango "
                       + "indicado. Por defecto 'from' es hoy y 'to' es el fin del período académico activo "
                       + "(o 'from' + 6 meses si no hay período activo con fecha de fin). Excluye ocurrencias "
                       + "ya pasadas (fecha+hora de inicio) salvo que 'includePast' sea true.")
    public ResponseEntity<Page<AcademicEventResponseDto>> findUnassigned(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/unassigned: from={}, to={}, includePast={}", from, to, includePast);
        return ResponseEntity.ok(allocationProblemService.findUnassigned(from, to, includePast, pageable));
    }

    @GetMapping("/overcrowded")
    @Operation(summary = "Listar aulas con sobrecupo",
               description = "Devuelve los pares evento-aula donde la cantidad de inscriptos supera la capacidad "
                       + "del aula asignada, en el rango indicado. Mismo rango por defecto que /unassigned. "
                       + "Excluye ocurrencias ya pasadas salvo que 'includePast' sea true.")
    public ResponseEntity<Page<OvercrowdedAllocationDto>> findOvercrowded(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/overcrowded: from={}, to={}, includePast={}", from, to, includePast);
        return ResponseEntity.ok(allocationProblemService.findOvercrowded(from, to, includePast, pageable));
    }

    @GetMapping("/overlaps")
    @Operation(summary = "Listar superposiciones de horario-aula",
               description = "Devuelve los pares de eventos cuyos horarios se superponen en la misma aula, "
                       + "en el rango indicado. Mismo rango por defecto que /unassigned. "
                       + "Excluye ocurrencias ya pasadas salvo que 'includePast' sea true.")
    public ResponseEntity<Page<ClassroomOverlapDto>> findOverlaps(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/overlaps: from={}, to={}, includePast={}", from, to, includePast);
        return ResponseEntity.ok(allocationProblemService.findOverlaps(from, to, includePast, pageable));
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

    @GetMapping("/occurrences/{occurrenceId}/history")
    @Operation(summary = "Historial de auditoría de una ocurrencia",
               description = "Devuelve las revisiones de auditoría (Envers) de la ocurrencia en orden ascendente: "
                       + "cambios de estado (cuándo se canceló/suspendió y quién). El snapshot es null en revisiones DELETED.")
    public ResponseEntity<List<RevisionDto<OccurrenceHistorySnapshotDto>>> findOccurrenceHistory(
            @PathVariable Long occurrenceId) {
        log.debug("GET /v1/allocations/occurrences/{}/history", occurrenceId);
        return ResponseEntity.ok(auditHistoryService.findOccurrenceHistory(occurrenceId));
    }

    @GetMapping("/occurrences/{occurrenceId}/allocation-history")
    @Operation(summary = "Historial de asignaciones de una ocurrencia",
               description = "Devuelve las revisiones de auditoría (Envers) de la(s) asignación(es) de la ocurrencia "
                       + "en orden ascendente: qué aula tuvo en cada momento, origen (MANUAL/AUTOMATIC/IMPORTED) y "
                       + "quién la cambió. Se consulta por ocurrencia porque la asignación puede borrarse y recrearse. "
                       + "Lista vacía si la ocurrencia existe pero nunca tuvo asignación.")
    public ResponseEntity<List<RevisionDto<AllocationHistorySnapshotDto>>> findAllocationHistory(
            @PathVariable Long occurrenceId) {
        log.debug("GET /v1/allocations/occurrences/{}/allocation-history", occurrenceId);
        return ResponseEntity.ok(auditHistoryService.findAllocationHistory(occurrenceId));
    }

    @PostMapping("/occurrences/{occurrenceId}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
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

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
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

    @PutMapping("/batch")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Reasignar aulas en lote",
               description = "Cambia el aula de múltiples asignaciones en una sola operación atómica. Falla si cualquier ocurrencia ya ocurrió.")
    public ResponseEntity<List<AllocationResponseDto>> batchReassign(
            @Valid @RequestBody BatchReassignRequestDto dto) {
        log.debug("PUT /v1/allocations/batch: moves={}", dto.moves().size());
        List<AllocationResponseDto> response = allocationService.batchReallocate(dto);
        log.info("Reasignación en lote completa: moved={}", response.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/from-date")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
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
