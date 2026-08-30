package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/buildings")
@RequiredArgsConstructor
@Tag(name = "Edificios", description = "Consulta de edificios disponibles")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    @Operation(summary = "Listar edificios",
               description = "Por defecto devuelve solo los edificios activos. Con includeDeactivated=true "
                       + "devuelve todos; útil para la pantalla de administración donde se activan/desactivan "
                       + "edificios.")
    public ResponseEntity<List<BuildingResponseDto>> findAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/buildings: includeDeactivated={}", includeDeactivated);
        List<BuildingResponseDto> buildings = buildingService.findAll(includeDeactivated);
        log.info("Edificios listados: count={}", buildings.size());
        return ResponseEntity.ok(buildings);
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar edificio",
               description = "Reactiva un edificio previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el edificio no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/buildings/{}/activation", id);
        buildingService.activate(id);
        log.info("Edificio activado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Desactivar edificio",
               description = "Soft-delete idempotente. Pensado para ocultar edificios que sincroniza SysAcad "
                       + "pero que la facultad no usa. 204 si queda inactivo; 404 si el edificio no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/buildings/{}/activation", id);
        buildingService.deactivate(id);
        log.info("Edificio desactivado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar/desactivar edificios en lote",
               description = "Aplica el cambio de estado activo a varios edificios en una única transacción: si "
                       + "alguno falla (ej. 404), no se aplica ninguno. Pensado para desactivar de una los "
                       + "edificios que sincroniza SysAcad pero que la facultad no usa.")
    public ResponseEntity<List<BuildingResponseDto>> setActiveBatch(
            @Valid @RequestBody BuildingActiveBatchRequestDto dto) {
        log.debug("PATCH /v1/buildings/activation (batch): count={}", dto.buildings().size());
        List<BuildingResponseDto> response = buildingService.setActiveBatch(dto.buildings());
        log.info("Batch de edificios actualizado vía controller: count={}", response.size());
        return ResponseEntity.ok(response);
    }
}
