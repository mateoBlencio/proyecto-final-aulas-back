package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.DeallocationBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ReleaseBatchRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationImpactResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventAllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationCommandMapper;
import ar.edu.utn.frc.siga.allocation.mapper.EventAllocationComposer;
import ar.edu.utn.frc.siga.audit.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationAuditHistoryService;
import ar.edu.utn.frc.siga.allocation.service.AllocationConflictService;
import ar.edu.utn.frc.siga.allocation.service.ReleasedOccurrenceQueryService;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceItemDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationImpactService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
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

import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Asignación manual de aulas para ocurrencias específicas")
@PreAuthorize("hasAuthority('PERM_ALLOCATION_READ')")
public class AllocationController {

    private final AllocationService allocationService;
    private final AllocationAuditHistoryService allocationAuditHistoryService;
    private final AllocationConflictService allocationConflictService;
    private final AllocationImpactService allocationImpactService;
    private final AllocationCommandMapper commandMapper;
    private final EventAllocationComposer eventAllocationComposer;
    private final AcademicEventService academicEventService;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ReleasedOccurrenceQueryService releasedOccurrenceQueryService;

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
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ);
        List<UniqueEventAllocationResponseDto> events = eventAllocationComposer.composeAll(academicEventService.findUniqueEvents())
                .stream()
                .filter(e -> e.classroom() == null || scope.allows(e.classroom().buildingId()))
                .toList();
        log.info("Eventos únicos con aula listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PERM_ALLOCATION_HISTORY_READ')")
    @Operation(summary = "Historial de asignaciones de un evento",
               description = "Devuelve las revisiones de auditoría de la(s) asignación(es) de todas las ocurrencias"
                       + " del evento. Devuelve 404 si el evento no existe y una lista vacía si existe pero nunca fue asignada.")
    public ResponseEntity<List<RevisionDto<AllocationHistorySnapshotDto>>> findAllocationHistory(
            @RequestParam Long eventId) {
        log.debug("GET /v1/allocations/history: eventId={}", eventId);
        return ResponseEntity.ok(allocationAuditHistoryService.findAllocationHistory(eventId));
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasAuthority('PERM_CONFLICT_READ')")
    @Operation(summary = "Listar conflictos de asignación",
               description = "Devuelve, mezclados y paginados, los conflictos pedidos en 'types' (todos si se omite): "
                       + "eventos sin aula (UNALLOCATED), aulas con sobrecupo (OVERCROWDED), superposiciones de "
                       + "horario-aula (OVERLAP) y asignaciones en aulas que no habilitan la materia (NOT_PERMITTED). "
                       + "Mismos defaults de rango que antes: 'from' hoy, 'to' fin del período académico activo "
                       + "(o +6 meses). Excluye ocurrencias ya pasadas salvo 'includePast=true'. "
                       + "Solo para UNALLOCATED: 'q' (contiene, sin mayúsculas ni tildes, sobre materia, código de "
                       + "comisión y especialidad), 'dayOfWeek' (día que dicta el evento; en únicos, el día de su "
                       + "fecha) y 'specialty' (nombre exacto de la especialidad) filtran antes de paginar. "
                       + "'sort=campo,asc|desc' acepta subject, dayOfWeek, startTime, enrolled y commissionCode "
                       + "(otra propiedad da 400); sin sort, orden por id del evento.")
    public ResponseEntity<Page<AllocationConflictDto>> findConflicts(
            @RequestParam(required = false) Set<ConflictType> types,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) String specialty,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/conflicts: types={}, from={}, to={}, includePast={}, q={}, dayOfWeek={}, specialty={}",
                types, from, to, includePast, q, dayOfWeek, specialty);
        return ResponseEntity.ok(allocationConflictService.findConflicts(
                types, from, to, includePast, q, dayOfWeek, specialty, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Asignar aulas en lote",
               description = "Asigna aula a cada item del lote. Cada item apunta de una de tres formas: "
                       + "'occurrenceIds' (fechas puntuales), 'eventId' solo (todas las ocurrencias futuras "
                       + "del evento) o 'eventId' + 'from' [+ 'to'] (rango de fechas, ambos bordes inclusive). "
                       + "409 si alguna occurrence del lote ya tiene asignación, si algún aula no existe/no está "
                       + "disponible, o si hay solapamiento de horario con otra asignación. Atómico.")
    public ResponseEntity<List<AllocationResponseDto>> allocate(@Valid @RequestBody AllocationBatchRequestDto dto) {
        log.debug("POST /v1/allocations: items={}", dto.items().size());
        List<AllocationResponseDto> response = allocationService.allocate(commandMapper.toManualCommand(dto));
        log.info("Asignación en lote completa: allocated={}", response.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Reasignar aulas en lote",
               description = "Cambia el aula de cada item del lote (upsert: crea la asignación si no existía). "
                       + "Además de las formas puntual ('occurrenceIds') y evento completo ('eventId'), acepta "
                       + "mover un evento por rango de fechas: 'eventId' + 'from' + 'to' es un cambio TEMPORAL "
                       + "(las ocurrencias de afuera del rango conservan su aula, así que la clase 'vuelve' sola), "
                       + "y 'eventId' + 'from' sin 'to' es un cambio PERMANENTE desde esa fecha hasta que termine "
                       + "el dictado. Ambos bordes son inclusive y 'from' no puede ser anterior a hoy (400). "
                       + "409 si algún aula no existe/no está disponible, si hay solapamiento de horario con otra "
                       + "asignación, o si una ocurrencia del rango ya ocurrió. En el 409 y en el 400 por "
                       + "superposición dentro del margen, 'classroomIds' lista todas las aulas en conflicto y "
                       + "'detail' explica el motivo. Atómico: si una sola fecha del "
                       + "rango falla, no se aplica ningún cambio.")
    public ResponseEntity<List<AllocationResponseDto>> reallocate(@Valid @RequestBody AllocationBatchRequestDto dto) {
        log.debug("PUT /v1/allocations: items={}", dto.items().size());
        List<AllocationResponseDto> response = allocationService.reallocate(commandMapper.toManualCommand(dto));
        log.info("Reasignación en lote completa: allocated={}", response.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/impact")
    @PreAuthorize("hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Ver el impacto de un pedido sin aplicarlo",
               description = "Recibe el mismo body que POST/PUT y responde qué pasaría si se aplicara, sin "
                       + "escribir nada. Devuelve cuántas clases toca el pedido y los choques como DATO y no como error, "
                       + "cada uno con el evento que ocupa el aula y las aulas libres en esa fecha y franja para "
                       + "poder destrabarlo (las aulas sugeridas se filtran por permiso: no se propone un aula que "
                       + "no habilita la materia del evento). Siempre 200, incluso con conflictos. Sigue devolviendo 400 si el "
                       + "pedido está mal formado y 409 si pide un aula inexistente o una ocurrencia ya pasada: "
                       + "eso no es un resultado a mostrar, es un pedido que no se puede ni evaluar.")
    public ResponseEntity<AllocationImpactResponseDto> impact(@Valid @RequestBody AllocationBatchRequestDto dto) {
        log.debug("POST /v1/allocations/impact: items={}", dto.items().size());
        AllocationImpactResponseDto impact = allocationImpactService.analyze(commandMapper.toManualCommand(dto));
        log.info("Impacto analizado: total={}, movibles={}, bloqueadas={}",
                impact.totalClasses(), impact.movableClasses(), impact.blockedClasses());
        return ResponseEntity.ok(impact);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Desasignar aulas en lote",
               description = "Libera el aula de cada item del lote (borra la asignación). "
                       + "409 si alguna ocurrencia del lote ya pasó. Atómico.")
    public ResponseEntity<List<DeallocatedOccurrenceDto>> deallocate(@Valid @RequestBody DeallocationBatchRequestDto dto) {
        log.debug("DELETE /v1/allocations: items={}", dto.items().size());
        List<DeallocatedOccurrenceDto> response = allocationService.deallocate(commandMapper.toDeallocationCommand(dto));
        log.info("Desasignación en lote completa: freed={}", response.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/releases")
    @PreAuthorize("hasAuthority('PERM_OCCURRENCE_RELEASE')")
    @Operation(summary = "Liberar ocurrencias con motivo",
               description = "Marca las ocurrencias del lote como ROOM_RELEASED con motivo (VIRTUAL_CLASS, "
                       + "CANCELLED u OTHER, este último con nota obligatoria) y borra sus asignaciones. Cada item "
                       + "apunta con 'occurrenceIds', 'eventId' (ocurrencias futuras del evento) o 'eventId' + "
                       + "'from' [+ 'to']. Arrastra las ocurrencias espejo con el mismo motivo. 400 si falta el "
                       + "motivo, es MIRROR_RETIRED u OTHER sin nota; 403 sin alcance sobre el edificio del aula; "
                       + "409 si alguna ocurrencia ya pasó. Atómico.")
    public ResponseEntity<List<ReleasedOccurrenceDto>> release(@Valid @RequestBody ReleaseBatchRequestDto dto) {
        log.debug("POST /v1/allocations/releases: items={}, reason={}", dto.items().size(), dto.reason());
        List<ReleasedOccurrenceDto> response = allocationService.release(commandMapper.toReleaseCommand(dto));
        log.info("Liberación con motivo completa: released={}", response.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/releases")
    @PreAuthorize("hasAuthority('PERM_CONFLICT_READ')")
    @Operation(summary = "Listar ocurrencias liberadas",
               description = "Devuelve, paginadas, las ocurrencias en estado ROOM_RELEASED con su evento, motivo, "
                       + "nota y aula anterior. Filtros opcionales: 'reason', rango 'from'/'to' (mismos defaults "
                       + "que /conflicts: 'from' hoy, 'to' fin del período académico activo o +6 meses), "
                       + "'q' (contiene, sin mayúsculas ni tildes, sobre materia, código de comisión y "
                       + "especialidad), 'dayOfWeek' (día de la fecha de la ocurrencia) y 'specialty' (nombre "
                       + "exacto). Una fila por evento, motivo, nota y aula anterior, con la lista 'dates' "
                       + "(occurrenceId + fecha) de sus ocurrencias liberadas. 'sort=campo,asc|desc' acepta "
                       + "subject, dayOfWeek, startTime, enrolled y commissionCode (otra propiedad da 400); sin "
                       + "sort, orden por id del evento. Si el usuario tiene alcance "
                       + "por edificio, solo ve las liberadas cuya aula anterior está en su alcance (las "
                       + "que nunca tuvieron aula se ven siempre).")
    public ResponseEntity<Page<ReleasedOccurrenceItemDto>> findReleased(
            @RequestParam(required = false) ReleaseReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DayOfWeek dayOfWeek,
            @RequestParam(required = false) String specialty,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/allocations/releases: reason={}, from={}, to={}, q={}, dayOfWeek={}, specialty={}",
                reason, from, to, q, dayOfWeek, specialty);
        return ResponseEntity.ok(releasedOccurrenceQueryService.findReleased(
                reason, from, to, q, dayOfWeek, specialty, pageable));
    }

    @DeleteMapping("/releases")
    @PreAuthorize("hasAuthority('PERM_OCCURRENCE_REQUEST_ROOM')")
    @Operation(summary = "Revertir la liberación de ocurrencias",
               description = "Devuelve las ocurrencias liberadas del lote a NEEDS_ROOM y limpia motivo y nota. "
                       + "No asigna aula ni recrea espejos. Responde con los ids revertidos. 403 sin alcance "
                       + "sobre el edificio del aula (actual o anterior); 409 si alguna ocurrencia ya pasó. Atómico.")
    public ResponseEntity<List<Long>> cancelRelease(@Valid @RequestBody DeallocationBatchRequestDto dto) {
        log.debug("DELETE /v1/allocations/releases: items={}", dto.items().size());
        List<Long> response = allocationService.cancelRelease(commandMapper.toDeallocationCommand(dto));
        log.info("Liberación revertida: reverted={}", response.size());
        return ResponseEntity.ok(response);
    }
}
