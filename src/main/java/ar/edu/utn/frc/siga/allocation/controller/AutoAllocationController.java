package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AutoAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints del flujo de asignación automática (preview interactivo, validate-move y confirm) orquestado sobre el solver. */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignación automática", description = "Preview de asignación óptima de aulas vía solver")
public class AutoAllocationController {

    private final AutoAllocationService autoAllocationService;

    /** Corre el solver y genera una preview (no persiste asignaciones). */
    @PostMapping("/auto-preview")
    @Operation(summary = "Generar preview de asignación automática",
               description = "Corre el solver con las aulas disponibles y la ocupación existente; devuelve una preview con su previewId, sin persistir asignaciones.")
    public ResponseEntity<AutoPreviewResponseDto> autoPreview(@Valid @RequestBody AutoPreviewRequestDto request) {
        log.debug("POST /v1/allocations/auto-preview: eventIds={}", request.eventIds());
        AutoPreviewResponseDto preview = autoAllocationService.autoPreview(request);
        log.info("Auto-preview generado: previewId={}, allocations={}, unresolved={}",
                preview.previewId(), preview.allocations().size(), preview.unresolved().size());
        return ResponseEntity.ok(preview);
    }

    /** Recupera una preview ya generada, recompuesta contra el estado actual de la BD. */
    @GetMapping("/auto-preview/{previewId}")
    @Operation(summary = "Recuperar una preview generada",
               description = "Devuelve una preview previamente generada. 410 si expiró o no existe.")
    public ResponseEntity<AutoPreviewResponseDto> getPreview(@PathVariable String previewId) {
        log.debug("GET /v1/allocations/auto-preview/{}", previewId);
        return ResponseEntity.ok(autoAllocationService.getPreview(previewId));
    }

    /** Valida si arrastrar un evento del preview a otra aula genera una superposición nueva. */
    @PostMapping("/auto-preview/{previewId}/validate-move")
    @Operation(summary = "Validar movimiento de aula sobre el preview",
               description = "Valida si mover el bloque de un evento a otra aula genera una superposición nueva, "
                       + "contra lo firme de BD y contra el resto de la propuesta ajustada. Responde 200 siempre "
                       + "que el request sea coherente con el preview: el conflicto va en el body (valid=false), "
                       + "nunca como 409. 410 si el preview expiró; 409 si el evento no pertenece al preview.")
    public ResponseEntity<ValidateMoveResponseDto> validateMove(
            @PathVariable String previewId, @Valid @RequestBody ValidateMoveRequestDto request) {
        log.debug("POST /v1/allocations/auto-preview/{}/validate-move: eventId={}, classroomId={}",
                previewId, request.eventId(), request.classroomId());
        ValidateMoveResponseDto response = autoAllocationService.validateMove(previewId, request);
        log.info("Validación de movimiento: previewId={}, eventId={}, valid={}, conflicts={}",
                previewId, request.eventId(), response.valid(), response.conflicts().size());
        return ResponseEntity.ok(response);
    }

    /** Aplica de forma atómica la propuesta final del preview (source AUTOMATIC), re-validando contra la BD actual. */
    @PostMapping("/auto-preview/{previewId}/confirm")
    @Operation(summary = "Confirmar el preview de asignación automática",
               description = "Persiste de forma atómica la propuesta final ajustada por el usuario, re-validando "
                       + "todo contra la BD actual antes de escribir. Eventos sin aula propuesta quedan en "
                       + "skippedEventIds (revisión manual). Invalida el preview: un re-confirm da 410.")
    public ResponseEntity<ConfirmAutoPreviewResponseDto> confirm(
            @PathVariable String previewId, @Valid @RequestBody ConfirmAutoPreviewRequestDto request) {
        log.debug("POST /v1/allocations/auto-preview/{}/confirm: allocations={}", previewId, request.allocations().size());
        ConfirmAutoPreviewResponseDto response = autoAllocationService.confirm(previewId, request);
        log.info("Confirm de preview: previewId={}, applied={}, skipped={}",
                previewId, response.applied().size(), response.skippedEventIds().size());
        return ResponseEntity.ok(response);
    }
}
