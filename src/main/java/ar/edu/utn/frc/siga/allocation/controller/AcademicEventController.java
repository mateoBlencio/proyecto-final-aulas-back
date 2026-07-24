package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.AuditHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de eventos académicos (recurrentes/únicos): alta, consulta y sus ocurrencias. */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/events")
@RequiredArgsConstructor
@Tag(name = "Eventos Académicos", description = "Creación y gestión de eventos académicos y sus ocurrencias")
public class AcademicEventController {

    private final AcademicEventService academicEventService;
    private final AllocationService allocationService;
    private final AuditHistoryService auditHistoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar eventos académicos",
               description = "Devuelve todos los eventos académicos registrados.")
    public ResponseEntity<List<AcademicEventResponseDto>> findAll() {
        log.debug("GET /v1/events");
        List<AcademicEventResponseDto> events = academicEventService.findAll();
        log.info("Eventos listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Obtener evento académico por ID",
               description = "Devuelve los datos de un evento académico existente.")
    public ResponseEntity<AcademicEventResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/events/{}", id);
        return ResponseEntity.ok(academicEventService.findById(id));
    }

    @GetMapping("/{id}/occurrences")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar ocurrencias de un evento",
               description = "Devuelve todas las ocurrencias generadas para un evento académico.")
    public ResponseEntity<List<OccurrenceResponseDto>> findOccurrences(@PathVariable Long id) {
        log.debug("GET /v1/events/{}/occurrences", id);
        List<OccurrenceResponseDto> occurrences = academicEventService.findOccurrencesByEventId(id);
        log.info("Ocurrencias listadas: eventId={}, count={}", id, occurrences.size());
        return ResponseEntity.ok(occurrences);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Historial de auditoría de un evento",
               description = "Devuelve las revisiones de auditoría (Envers) del evento en orden ascendente: "
                       + "cambios de horario, inscriptos, comisión, alta y baja; con usuario y fecha de cada cambio. "
                       + "El snapshot es null en revisiones DELETED.")
    public ResponseEntity<List<RevisionDto<EventHistorySnapshotDto>>> findHistory(@PathVariable Long id) {
        log.debug("GET /v1/events/{}/history", id);
        return ResponseEntity.ok(auditHistoryService.findEventHistory(id));
    }

    @PostMapping("/recurring")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Crear evento recurrente",
               description = "Crea un evento recurrente semanal y genera todas sus ocurrencias.")
    public ResponseEntity<AcademicEventResponseDto> createRecurring(
            @Valid @RequestBody CreateRecurringEventRequestDto dto) {
        log.debug("POST /v1/events/recurring: subjectId={}, commissionId={}", dto.subjectId(), dto.commissionId());
        AcademicEventResponseDto response = academicEventService.createRecurringEvent(dto);
        log.info("Evento recurrente creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/unique")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Crea un evento único",
               description = "Crea un evento que ocurre una única vez, genera su única ocurrencia y le asigna "
                       + "el aula indicada en la misma operación (atómica): si el aula no está disponible o "
                       + "hay solapamiento, no se crea el evento.")
    public ResponseEntity<AcademicEventResponseDto> createUnique(
            @Valid @RequestBody CreateUniqueEventRequestDto dto) {
        log.debug("POST /v1/events/unique: date={}, classroomId={}", dto.date(), dto.classroomId());
        AcademicEventResponseDto response = academicEventService.createUniqueEvent(dto);
        log.info("Evento único creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/unique")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar eventos únicos",
               description = "Devuelve todos los eventos únicos (parciales, trabajos prácticos, mesas especiales, etc.).")
    public ResponseEntity<List<AcademicEventResponseDto>> findUniqueEvents() {
        log.debug("GET /v1/events/unique");
        List<AcademicEventResponseDto> events = academicEventService.findUniqueEvents();
        log.info("Eventos únicos listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @PutMapping("/unique/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Modificar un evento único",
               description = "Actualiza fecha, horario, cantidad de alumnos, aula y observaciones de un evento "
                       + "único existente, revalidando disponibilidad, solapamiento y capacidad antes de guardar.")
    public ResponseEntity<AcademicEventResponseDto> updateUnique(
            @PathVariable Long id, @Valid @RequestBody UpdateUniqueEventRequestDto dto) {
        log.debug("PUT /v1/events/unique/{}: classroomId={}", id, dto.classroomId());
        AcademicEventResponseDto response = academicEventService.updateUniqueEvent(id, dto);
        log.info("Evento único actualizado vía controller: id={}", id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/unique/{id}/cancel")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Cancelar un evento único",
               description = "Baja lógica: cancela la ocurrencia del evento sin borrarlo físicamente. "
                       + "Deja de bloquear el aula para nuevas asignaciones.")
    public ResponseEntity<Void> cancelUnique(@PathVariable Long id) {
        log.debug("POST /v1/events/unique/{}/cancel", id);
        academicEventService.cancelUniqueEvent(id);
        log.info("Evento único cancelado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{eventId}/classroom")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Reasignar aula de un evento",
               description = "Cambia el aula de todas las ocurrencias futuras de un evento recurrente. Las ocurrencias ya pasadas quedan intactas. Falla si el evento no es recurrente o si ya finalizó.")
    public ResponseEntity<List<AllocationResponseDto>> reassignEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody AllocateOccurrenceRequestDto dto) {
        log.debug("PUT /v1/events/{}/classroom: classroomId={}", eventId, dto.classroomId());
        List<AllocationResponseDto> response = allocationService.reassignEvent(eventId, dto);
        log.info("Evento reasignado: eventId={}, count={}", eventId, response.size());
        return ResponseEntity.ok(response);
    }
}
