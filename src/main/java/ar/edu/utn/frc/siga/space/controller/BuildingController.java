package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchRequestDto;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
               description = "Por defecto devuelve solo los edificios activos (según el setting "
                       + "space.filterInactiveBuildings). Con includeInactive=true devuelve todos, "
                       + "independientemente del setting; útil para la pantalla de administración donde se "
                       + "activan/desactivan edificios.")
    public ResponseEntity<List<BuildingResponseDto>> findAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        log.debug("GET /v1/buildings: includeInactive={}", includeInactive);
        List<BuildingResponseDto> buildings = buildingService.findAll(includeInactive);
        log.info("Edificios listados: count={}", buildings.size());
        return ResponseEntity.ok(buildings);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar/desactivar edificio",
               description = "Cambia el estado activo del edificio. Pensado para ocultar edificios que sincroniza "
                       + "SysAcad pero que la facultad no usa realmente. 404 si el edificio no existe.")
    public ResponseEntity<BuildingResponseDto> setActive(@PathVariable Long id,
                                                          @Valid @RequestBody BuildingActiveRequestDto dto) {
        log.debug("PATCH /v1/buildings/{}/active: active={}", id, dto.active());
        BuildingResponseDto response = buildingService.setActive(id, dto.active());
        log.info("Edificio actualizado vía controller: id={}, active={}", id, dto.active());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/active")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar/desactivar edificios en lote",
               description = "Aplica el cambio de estado activo a varios edificios en una única transacción: si "
                       + "alguno falla (ej. 404), no se aplica ninguno. Pensado para desactivar de una los "
                       + "edificios que sincroniza SysAcad pero que la facultad no usa.")
    public ResponseEntity<List<BuildingResponseDto>> setActiveBatch(
            @Valid @RequestBody BuildingActiveBatchRequestDto dto) {
        log.debug("PATCH /v1/buildings/active (batch): count={}", dto.buildings().size());
        List<BuildingResponseDto> response = buildingService.setActiveBatch(dto.buildings());
        log.info("Batch de edificios actualizado vía controller: count={}", response.size());
        return ResponseEntity.ok(response);
    }
}
