package ar.edu.utn.frc.siga.preview.controller;

import ar.edu.utn.frc.siga.preview.dto.request.ConfirmPreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewRequestDto;
import ar.edu.utn.frc.siga.preview.dto.response.ConfirmPreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.service.PreviewService;
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

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/previews")
@RequiredArgsConstructor
@Tag(name = "Asignación automática", description = "Preview de asignación óptima de aulas vía solver")
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping
    @Operation(summary = "Generar preview de asignación automática",
               description = "Corre el solver con las aulas disponibles y la ocupación existente; devuelve una preview con su previewId, sin persistir asignaciones.")
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
                       + "skippedEventIds (revisión manual). Invalida el preview: un re-confirm da 410.")
    public ResponseEntity<ConfirmPreviewResponseDto> confirm(
            @PathVariable String previewId, @Valid @RequestBody ConfirmPreviewRequestDto request) {
        log.debug("POST /v1/previews/{}/confirm: allocations={}", previewId, request.allocations().size());
        ConfirmPreviewResponseDto response = previewService.confirm(previewId, request);
        log.info("Confirm de preview: previewId={}, applied={}, skipped={}",
                previewId, response.applied().size(), response.skippedEventIds().size());
        return ResponseEntity.ok(response);
    }
}
