package ar.edu.utn.frc.siga.allocation.controller;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
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

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/allocations")
@RequiredArgsConstructor
@Tag(name = "Asignación automática", description = "Preview de asignación óptima de aulas vía solver")
public class AutoAllocationController {

    private final AutoAllocationService autoAllocationService;

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

    @GetMapping("/auto-preview/{previewId}")
    @Operation(summary = "Recuperar una preview generada",
               description = "Devuelve una preview previamente generada. 410 si expiró o no existe.")
    public ResponseEntity<AutoPreviewResponseDto> getPreview(@PathVariable String previewId) {
        log.debug("GET /v1/allocations/auto-preview/{}", previewId);
        return ResponseEntity.ok(autoAllocationService.getPreview(previewId));
    }
}
