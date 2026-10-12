package ar.edu.utn.frc.siga.roomrequest.direct.controller;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.CreateDirectEventRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventDraftDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionRequestDto;
import ar.edu.utn.frc.siga.roomrequest.direct.dto.DirectEventSuggestionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.service.DirectEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/events/direct")
@RequiredArgsConstructor
@Tag(name = "Alta directa de eventos",
     description = "Alta de un evento con aula ya asignada, sin pasar por una solicitud")
public class DirectEventController {

    private final DirectEventService directEventService;

    @PostMapping("/available-classrooms")
    @PreAuthorize("hasAuthority('PERM_EVENT_DIRECT_CREATE')")
    @Operation(summary = "Aulas candidatas para un borrador de evento",
               description = "Devuelve las aulas del alcance del usuario que cumplen los requisitos del borrador, "
                       + "con available=false las que están ocupadas en esa fecha y horario. No persiste nada.")
    public ResponseEntity<List<AllowedClassroomDto>> availableClassrooms(
            @Valid @RequestBody DirectEventDraftDto draft, Principal principal) {
        log.debug("POST /v1/events/direct/available-classrooms: type={}, date={}", draft.type(), draft.date());
        List<AllowedClassroomDto> response = directEventService.findAvailableClassrooms(draft, principal.getName());
        log.info("Aulas candidatas para evento directo listadas vía controller: total={}", response.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/suggestion")
    @PreAuthorize("hasAuthority('PERM_EVENT_DIRECT_CREATE')")
    @Operation(summary = "Sugerir aula(s) para un borrador de evento",
               description = "Corre el solver con el borrador y devuelve las aulas sugeridas con su suggestionId, "
                       + "sin persistir nada. status=PARTIAL si el solver cubre menos aulas que classroomCount; "
                       + "NO_ROOM_AVAILABLE (sin suggestionId) si ninguna cumple las reglas hard.")
    public ResponseEntity<DirectEventSuggestionResponseDto> suggest(
            @Valid @RequestBody DirectEventSuggestionRequestDto request, Principal principal) {
        log.debug("POST /v1/events/direct/suggestion: type={}", request.draft().type());
        DirectEventSuggestionResponseDto response = directEventService.suggest(request, principal.getName());
        log.info("Sugerencia de aula para evento directo vía controller: status={}", response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EVENT_DIRECT_CREATE')")
    @Operation(summary = "Crear un evento con aula asignada",
               description = "Crea el evento único y asigna las aulas en una sola transacción. Con suggestionId "
                       + "(de /suggestion) las aulas deben ser exactamente las sugeridas y la asignación queda como "
                       + "automática; 410 si la sugerencia expiró o no coincide. 409 si un aula está ocupada.")
    public ResponseEntity<List<AllocationResponseDto>> create(
            @Valid @RequestBody CreateDirectEventRequestDto request, Principal principal) {
        log.debug("POST /v1/events/direct: type={}, aulas={}", request.draft().type(), request.classroomIds());
        List<AllocationResponseDto> response = directEventService.create(request, principal.getName());
        log.info("Evento directo creado vía controller: asignaciones={}", response.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
