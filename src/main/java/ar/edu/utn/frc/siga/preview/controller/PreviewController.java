package ar.edu.utn.frc.siga.preview.controller;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.ReallocationSuggestionRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.ReallocationSuggestionResponseDto;
import ar.edu.utn.frc.siga.preview.service.PreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/previews")
@RequiredArgsConstructor
@Tag(name = "Asignación automática", description = "Preview de asignación óptima de aulas vía solver")
@PreAuthorize("hasAuthority('PERM_PREVIEW_RUN')")
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping
    @Operation(summary = "Generar preview de asignación automática",
               description = "Corre el solver con las aulas disponibles y la ocupación existente; devuelve una "
                       + "preview con su previewId, sin persistir asignaciones. 400 si no se indica eventIds ni "
                       + "selectAll, o si se indican ambos.")
    public ResponseEntity<PreviewResponseDto> autoPreview(@Valid @RequestBody PreviewRequestDto request) {
        log.debug("POST /v1/previews: eventIds={}, selectAll={}, excludedIds={}",
                request.eventIds(), request.selectAll(), request.excludedIds());
        PreviewResponseDto preview = previewService.autoPreview(request);
        log.info("Auto-preview generado: previewId={}, allocations={}, unresolved={}",
                preview.previewId(), preview.allocations().size(), preview.unresolved().size());
        return ResponseEntity.ok(preview);
    }

    @GetMapping("/{previewId}")
    @Operation(summary = "Recuperar una preview generada",
               description = "Devuelve una preview previamente generada. 410 si expiró o no existe.")
    public ResponseEntity<PreviewResponseDto> getPreview(@PathVariable String previewId) {
        log.debug("GET /v1/previews/{}", previewId);
        return ResponseEntity.ok(previewService.getPreview(previewId));
    }

    @PostMapping("/{previewId}/confirm")
    @Operation(summary = "Confirmar el preview de asignación automática",
               description = "Persiste de forma atómica la propuesta final ajustada por el usuario, re-validando "
                       + "todo contra la BD actual antes de escribir. Eventos sin aula propuesta quedan en "
                       + "skippedEventIds (revisión manual). 410 si el preview expiró o no existe (incluye "
                       + "re-confirm). 409 si la propuesta tiene eventos duplicados o ajenos al preview, si algún "
                       + "aula no existe/no está disponible, o si hay solapamiento de horario con otra asignación.")
    public ResponseEntity<ConfirmPreviewResponseDto> confirm(
            @PathVariable String previewId, @Valid @RequestBody ConfirmPreviewRequestDto request) {
        log.debug("POST /v1/previews/{}/confirm: allocations={}", previewId, request.allocations().size());
        ConfirmPreviewResponseDto response = previewService.confirm(previewId, request);
        log.info("Confirm de preview: previewId={}, applied={}, skipped={}",
                previewId, response.applied().size(), response.skippedEventIds().size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reallocation-suggestion")
    @PreAuthorize("hasAuthority('PERM_PREVIEW_RUN') and hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Sugerir aula para una reasignación",
               description = "Corre el solver para el target indicado (occurrenceIds, o eventId con from y "
                       + "opcionalmente to) y devuelve un aula sugerida con su suggestionId, sin persistir "
                       + "asignaciones. status=NO_ROOM_AVAILABLE (sin suggestionId) si ninguna aula del alcance "
                       + "del usuario cumple las reglas hard en todas las fechas.")
    public ResponseEntity<ReallocationSuggestionResponseDto> suggestReallocation(
            @Valid @RequestBody ReallocationSuggestionRequestDto request) {
        log.debug("POST /v1/previews/reallocation-suggestion: eventId={}, occurrenceIds={}",
                request.eventId(), request.occurrenceIds());
        ReallocationSuggestionResponseDto response = previewService.suggestReallocation(request);
        log.info("Sugerencia de reasignación: suggestionId={}, status={}", response.suggestionId(), response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reallocation-suggestion/{suggestionId}/confirm")
    @PreAuthorize("hasAuthority('PERM_PREVIEW_RUN') and hasAuthority('PERM_ALLOCATION_WRITE')")
    @Operation(summary = "Confirmar la sugerencia de reasignación",
               description = "Aplica, con source=AUTOMATIC, el aula sugerida a las ocurrencias que resolvió la "
                       + "sugerencia, revalidando todo contra la base. 410 si la sugerencia expiró, no existe o "
                       + "ya se usó (se consume al primer intento de confirm, aunque falle).")
    public ResponseEntity<List<AllocationResponseDto>> confirmReallocationSuggestion(
            @PathVariable String suggestionId) {
        log.debug("POST /v1/previews/reallocation-suggestion/{}/confirm", suggestionId);
        List<AllocationResponseDto> response = previewService.confirmReallocationSuggestion(suggestionId);
        log.info("Confirm de sugerencia de reasignación: suggestionId={}, applied={}", suggestionId, response.size());
        return ResponseEntity.ok(response);
    }
}
