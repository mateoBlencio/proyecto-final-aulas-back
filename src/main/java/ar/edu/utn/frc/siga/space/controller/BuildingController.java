package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@PreAuthorize("hasAuthority('PERM_BUILDING_READ')")
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    @Operation(summary = "Listar edificios",
               description = "Listado paginado con filtro opcional por nombre (contiene, case-insensitive). "
                       + "Por defecto devuelve solo los edificios activos. Con includeDeactivated=true "
                       + "devuelve todos; útil para la pantalla de administración donde se activan/desactivan "
                       + "edificios.")
    public ResponseEntity<Page<BuildingResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/buildings: name={}, pageable={}, includeDeactivated={}",
                name, pageable, includeDeactivated);
        Page<BuildingResponseDto> buildings = buildingService.findAll(
                new BuildingFilter(name), pageable, includeDeactivated);
        log.info("Edificios listados: total={}", buildings.getTotalElements());
        return ResponseEntity.ok(buildings);
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_BUILDING_ACTIVATE')")
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
    @PreAuthorize("hasAuthority('PERM_BUILDING_ACTIVATE')")
    @Operation(summary = "Desactivar edificio",
               description = "Soft-delete idempotente. Pensado para ocultar edificios que sincroniza SysAcad "
                       + "pero que la facultad no usa. Libera las clases futuras asignadas a sus aulas "
                       + "(motivo CLASSROOM_DEACTIVATED) y fusiona las que tienen espejo en otra aula. "
                       + "200 con el impacto (clases liberadas y fusionadas, pedidos de aula afectados); "
                       + "404 si el edificio no existe.")
    public ResponseEntity<DeactivationImpactDto> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/buildings/{}/activation", id);
        DeactivationImpactDto impact = buildingService.deactivate(id);
        log.info("Edificio desactivado vía controller: id={}", id);
        return ResponseEntity.ok(impact);
    }

    @GetMapping("/{id}/deactivation-impact")
    @PreAuthorize("hasAuthority('PERM_BUILDING_ACTIVATE')")
    @Operation(summary = "Ver impacto de desactivar edificio",
               description = "Calcula qué pasaría al desactivar el edificio sin aplicar nada: clases futuras que "
                       + "quedarían sin aula, clases que se fusionarían y pedidos de aula que piden sus aulas. "
                       + "404 si el edificio no existe.")
    public ResponseEntity<DeactivationImpactDto> deactivationImpact(@PathVariable Long id) {
        log.debug("GET /v1/buildings/{}/deactivation-impact", id);
        return ResponseEntity.ok(buildingService.deactivationImpact(id));
    }

    @PatchMapping("/activation")
    @PreAuthorize("hasAuthority('PERM_BUILDING_ACTIVATE')")
    @Operation(summary = "Activar/desactivar edificios en lote",
               description = "Aplica el cambio de estado activo a varios edificios en una única transacción: si "
                       + "alguno falla (ej. 404), no se aplica ninguno. Pensado para desactivar de una los "
                       + "edificios que sincroniza SysAcad pero que la facultad no usa. Al desactivar, libera las "
                       + "clases futuras de sus aulas igual que DELETE /{id}/activation.")
    public ResponseEntity<List<BuildingResponseDto>> setActiveBatch(
            @Valid @RequestBody BuildingActiveBatchRequestDto dto) {
        log.debug("PATCH /v1/buildings/activation (batch): count={}", dto.buildings().size());
        List<BuildingResponseDto> response = buildingService.setActiveBatch(dto.buildings());
        log.info("Batch de edificios actualizado vía controller: count={}", response.size());
        return ResponseEntity.ok(response);
    }
}
