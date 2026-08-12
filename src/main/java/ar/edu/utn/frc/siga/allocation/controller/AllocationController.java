package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationCommandMapper;
import ar.edu.utn.frc.siga.allocation.mapper.EventAllocationComposer;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationAuditHistoryService;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.ConflictType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Set;

/**
 * Endpoints de asignación manual de aulas: tres verbos en lote (asignar/reasignar/desasignar),
 * todos atómicos, source MANUAL siempre. Direccionar por occurrences puntuales o por evento
 * completo es un detalle del item, no del endpoint (ver {@code AllocationTarget}). También
 * expone la vista de eventos únicos ya compuesta con aula/estado/sobrecupo (lectura), que
 * combina datos de {@code events} y {@code allocation}.
 */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Asignación manual de aulas para ocurrencias específicas")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class AllocationController {

    private final AllocationService allocationService;
    private final AllocationAuditHistoryService allocationAuditHistoryService;
    private final AllocationConflictService allocationConflictService;
    private final AllocationCommandMapper commandMapper;
    private final EventAllocationComposer eventAllocationComposer;
    private final AcademicEventService academicEventService;

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

    @GetMapping("/events/unique")
    @Operation(summary = "Listar eventos únicos con aula",
               description = "Devuelve todos los eventos únicos con su aula asignada, estado y sobrecupo. "
                       + "Para la vista sin aula ver GET /v1/events/unique.")
    public ResponseEntity<List<UniqueEventAllocationResponseDto>> findUniqueEvents() {
        log.debug("GET /v1/allocations/events/unique");
        List<UniqueEventAllocationResponseDto> events = eventAllocationComposer.composeAll(academicEventService.findUniqueEvents());
        log.info("Eventos únicos con aula listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/history")
    @Operation(summary = "Historial de asignaciones de un evento",
               description = "Devuelve las revisiones de auditoría (Envers) de la(s) asignación(es) de TODAS las "
                       + "occurrences del evento, fusionadas en una sola línea de tiempo ascendente: qué aula tuvo "
                       + "cada occurrence en cada momento, origen (MANUAL/AUTOMATIC/IMPORTED) y quién la cambió. "
                       + "404 si el evento no existe. Lista vacía si existe pero nunca tuvo asignación.")
    public ResponseEntity<List<RevisionDto<AllocationHistorySnapshotDto>>> findAllocationHistory(
            @RequestParam Long eventId) {
        log.debug("GET /v1/allocations/history: eventId={}", eventId);
        return ResponseEntity.ok(allocationAuditHistoryService.findAllocationHistory(eventId));
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Listar conflictos de asignación",
               description = "Devuelve, mezclados y paginados, los conflictos pedidos en 'types' (los tres si se omite): "
                       + "eventos sin aula, aulas con sobrecupo, superposiciones de horario-aula. Mismos defaults de "
                       + "rango que antes: 'from' hoy, 'to' fin del período académico activo (o +6 meses). Excluye "
                       + "ocurrencias ya pasadas salvo 'includePast=true'.")
    public ResponseEntity<Page<AllocationConflictDto>> findConflicts(
            @RequestParam(required = false) Set<ConflictType> types,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/conflicts: types={}, from={}, to={}, includePast={}", types, from, to, includePast);
        return ResponseEntity.ok(allocationConflictService.findConflicts(types, from, to, includePast, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Asignar aulas en lote",
               description = "Asigna aula a cada item del lote (occurrences puntuales o evento completo). "
                       + "409 si alguna occurrence del lote ya tiene asignación. Atómico.")
    public ResponseEntity<List<AllocationResponseDto>> allocate(@Valid @RequestBody AllocationBatchRequestDto dto) {
        log.debug("POST /v1/allocations: items={}", dto.items().size());
        List<AllocationResponseDto> response = allocationService.allocate(commandMapper.toManualCommand(dto));
        log.info("Asignación en lote completa: allocated={}", response.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Reasignar aulas en lote",
               description = "Cambia el aula de cada item del lote (upsert: crea la asignación si no existía). Atómico.")
    public ResponseEntity<List<AllocationResponseDto>> reallocate(@Valid @RequestBody AllocationBatchRequestDto dto) {
        log.debug("PUT /v1/allocations: items={}", dto.items().size());
        List<AllocationResponseDto> response = allocationService.reallocate(commandMapper.toManualCommand(dto));
        log.info("Reasignación en lote completa: allocated={}", response.size());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Desasignar aulas en lote",
               description = "Libera el aula de cada item del lote (borra la asignación). Atómico.")
    public ResponseEntity<List<DeallocatedOccurrenceDto>> deallocate(@Valid @RequestBody DeallocationBatchRequestDto dto) {
        log.debug("DELETE /v1/allocations: items={}", dto.items().size());
        List<DeallocatedOccurrenceDto> response = allocationService.deallocate(commandMapper.toDeallocationCommand(dto));
        log.info("Desasignación en lote completa: freed={}", response.size());
        return ResponseEntity.ok(response);
    }
}
