package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Endpoints de eventos académicos (recurrentes/únicos): alta, consulta y sus ocurrencias. */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/events")
@RequiredArgsConstructor
@Tag(name = "Eventos Académicos", description = "Creación y gestión de eventos académicos y sus ocurrencias")
public class AcademicEventController {

    private final AcademicEventService academicEventService;

    /** Todos los eventos académicos registrados. */
    @GetMapping
    @Operation(summary = "Listar eventos académicos",
               description = "Devuelve todos los eventos académicos registrados.")
    public ResponseEntity<List<AcademicEventResponseDto>> findAll() {
        log.debug("GET /v1/events");
        List<AcademicEventResponseDto> events = academicEventService.findAll();
        log.info("Eventos listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    /** Eventos con occurrences SCHEDULED (pendientes de aula) entre las fechas indicadas, agrupados por evento. */
    @GetMapping("/unassigned")
    @Operation(summary = "Listar eventos con ocurrencias sin aula asignada",
               description = "Devuelve, agrupados por evento, las ocurrencias en estado SCHEDULED "
                       + "(pendientes de aula) entre las fechas indicadas. Excluye ocurrencias "
                       + "ASSIGNED, CANCELLED y SUSPENDED. Por defecto, desde hoy en adelante.")
    public ResponseEntity<List<AcademicEventResponseDto>> findUnassigned(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /v1/events/unassigned: from={}, to={}", from, to);
        List<AcademicEventResponseDto> events = academicEventService.findUnassignedEvents(from, to);
        log.info("Eventos sin asignar listados: count={}", events.size());
        return ResponseEntity.ok(events);
    }

    /** Datos de un evento académico existente por su ID. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener evento académico por ID",
               description = "Devuelve los datos de un evento académico existente.")
    public ResponseEntity<AcademicEventResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/events/{}", id);
        return ResponseEntity.ok(academicEventService.findById(id));
    }

    /** Todas las occurrences generadas para un evento académico. */
    @GetMapping("/{id}/occurrences")
    @Operation(summary = "Listar ocurrencias de un evento",
               description = "Devuelve todas las ocurrencias generadas para un evento académico.")
    public ResponseEntity<List<OccurrenceResponseDto>> findOccurrences(@PathVariable Long id) {
        log.debug("GET /v1/events/{}/occurrences", id);
        List<OccurrenceResponseDto> occurrences = academicEventService.findOccurrencesByEventId(id);
        log.info("Ocurrencias listadas: eventId={}, count={}", id, occurrences.size());
        return ResponseEntity.ok(occurrences);
    }

    /** Crea un evento recurrente semanal y genera todas sus occurrences. */
    @PostMapping("/recurring")
    @Operation(summary = "Crear evento recurrente",
               description = "Crea un evento recurrente semanal y genera todas sus ocurrencias.")
    public ResponseEntity<AcademicEventResponseDto> createRecurring(
            @Valid @RequestBody CreateRecurringEventRequestDto dto) {
        log.debug("POST /v1/events/recurring: subjectId={}, commissionId={}", dto.subjectId(), dto.commissionId());
        AcademicEventResponseDto response = academicEventService.createRecurringEvent(dto);
        log.info("Evento recurrente creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Crea un evento que ocurre una única vez y genera su única occurrence. */
    @PostMapping("/unique")
    @Operation(summary = "Crea un evento único",
               description = "Crea un evento que ocurre una única vez y genera una única ocurrencia.")
    public ResponseEntity<AcademicEventResponseDto> createUnique(
            @Valid @RequestBody CreateUniqueEventRequestDto dto) {
        log.debug("POST /v1/events/unique: date={}", dto.date());
        AcademicEventResponseDto response = academicEventService.createUniqueEvent(dto);
        log.info("Evento único creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
