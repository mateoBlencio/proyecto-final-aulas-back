package ar.edu.utn.frc.siga.events.controller;

import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.EventHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceHistorySnapshotDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.common.dto.response.RevisionDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.EventAuditHistoryService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
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

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/events")
@RequiredArgsConstructor
@Tag(name = "Eventos Académicos", description = "Creación y gestión de eventos académicos y sus ocurrencias")
public class AcademicEventController {

    private final AcademicEventService academicEventService;
    private final EventAuditHistoryService eventAuditHistoryService;
    private final OccurrenceService occurrenceService;

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

    @GetMapping("/{id:\\d+}")
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
        return ResponseEntity.ok(eventAuditHistoryService.findEventHistory(id));
    }

    @GetMapping("/occurrences/{occurrenceId}/history")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Historial de auditoría de una ocurrencia",
               description = "Devuelve las revisiones de auditoría (Envers) de la ocurrencia en orden ascendente: "
                       + "cambios de estado (cuándo se canceló/suspendió y quién). El snapshot es null en revisiones DELETED.")
    public ResponseEntity<List<RevisionDto<OccurrenceHistorySnapshotDto>>> findOccurrenceHistory(
            @PathVariable Long occurrenceId) {
        log.debug("GET /v1/events/occurrences/{}/history", occurrenceId);
        return ResponseEntity.ok(eventAuditHistoryService.findOccurrenceHistory(occurrenceId));
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

    @GetMapping("/unique")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar eventos únicos",
               description = "Devuelve todos los eventos únicos (parciales, trabajos prácticos, mesas especiales, etc.), "
                       + "sin datos de aula. Para la vista con aula/estado/sobrecupo ver GET /v1/allocations/events/unique.")
    public ResponseEntity<List<AcademicEventResponseDto>> findUniqueEvents() {
        log.debug("GET /v1/events/unique");
        List<AcademicEventResponseDto> events = academicEventService.findUniqueEvents();
        log.info("Eventos únicos listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    @PostMapping("/unique")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Crear evento único",
               description = "Crea un evento que ocurre una única vez y genera su única ocurrencia, sin asignarle "
                       + "aula: la ocurrencia queda en NEEDS_ROOM. Para asignarle un aula, llamar por separado a "
                       + "POST /v1/allocations con la ocurrencia generada.")
    public ResponseEntity<AcademicEventResponseDto> createUnique(
            @Valid @RequestBody CreateUniqueEventRequestDto dto) {
        log.debug("POST /v1/events/unique: eventType={}, date={}", dto.eventType(), dto.date());
        AcademicEventResponseDto response = academicEventService.createUniqueEvent(dto);
        log.info("Evento único creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/unique/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Modificar un evento único",
               description = "Actualiza fecha, horario, cantidad de alumnos y descripción de un evento único "
                       + "existente, sin tocar su aula asignada. Para reasignar el aula, llamar por separado a "
                       + "PUT /v1/allocations.")
    public ResponseEntity<AcademicEventResponseDto> updateUnique(
            @PathVariable Long id, @Valid @RequestBody UpdateUniqueEventRequestDto dto) {
        log.debug("PUT /v1/events/unique/{}", id);
        AcademicEventResponseDto response = academicEventService.updateUniqueEvent(id, dto);
        log.info("Evento único actualizado vía controller: id={}", id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/occurrences/{occurrenceId}/release")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Liberar el aula de una ocurrencia",
               description = "Marca la ocurrencia como ROOM_RELEASED (libera el aula a propósito, "
                       + "reasignable en cualquier momento). Rechaza ocurrencias ya pasadas.")
    public ResponseEntity<Void> release(@PathVariable Long occurrenceId) {
        log.debug("POST /v1/events/occurrences/{}/release", occurrenceId);
        occurrenceService.release(occurrenceId);
        log.info("Ocurrencia liberada: occurrenceId={}", occurrenceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/occurrences/{occurrenceId}/request-room")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Volver a pedir aula para una ocurrencia",
               description = "Vuelve a marcar la ocurrencia como necesitada de aula (NEEDS_ROOM). No implica "
                       + "que se reactive nada: la ocurrencia nunca dejó de dictarse. Rechaza ocurrencias ya pasadas.")
    public ResponseEntity<Void> requestRoom(@PathVariable Long occurrenceId) {
        log.debug("POST /v1/events/occurrences/{}/request-room", occurrenceId);
        occurrenceService.requestRoom(occurrenceId);
        log.info("Ocurrencia vuelta a marcar como NEEDS_ROOM: occurrenceId={}", occurrenceId);
        return ResponseEntity.noContent().build();
    }
}
