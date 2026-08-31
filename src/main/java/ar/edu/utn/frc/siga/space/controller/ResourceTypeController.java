package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.request.ResourceTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ResourceTypeResponseDto;
import ar.edu.utn.frc.siga.space.service.ResourceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/resource-types")
@RequiredArgsConstructor
@Tag(name = "Tipos de recurso", description = "ABM de tipos de recurso de aula")
@PreAuthorize("hasRole('SUBSECRETARIA')")
public class ResourceTypeController {

    private final ResourceTypeService resourceTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar tipos de recurso",
               description = "Listado paginado. Por defecto devuelve solo los tipos activos; con "
                       + "includeDeactivated=true incluye también los desactivados. Pensado para poblar el "
                       + "selector de recursos del aula.")
    public ResponseEntity<Page<ResourceTypeResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/resource-types: includeDeactivated={}, page={}", includeDeactivated,
                pageable.getPageNumber());
        return ResponseEntity.ok(resourceTypeService.findAll(includeDeactivated, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Buscar tipo de recurso por id", description = "404 si el tipo de recurso no existe.")
    public ResponseEntity<ResourceTypeResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/resource-types/{}", id);
        return ResponseEntity.ok(resourceTypeService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Crear tipo de recurso",
               description = "400 si ya existe un tipo de recurso con ese nombre (incluye desactivados).")
    public ResponseEntity<ResourceTypeResponseDto> create(@Valid @RequestBody ResourceTypeRequestDto dto) {
        log.debug("POST /v1/resource-types: name={}", dto.name());
        ResourceTypeResponseDto response = resourceTypeService.create(dto);
        log.info("Tipo de recurso creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar tipo de recurso",
               description = "Actualiza nombre y tipo de valor. 400 si el nombre ya lo usa otro tipo de recurso "
                       + "(incluye desactivados); 404 si el tipo de recurso no existe.")
    public ResponseEntity<ResourceTypeResponseDto> update(@PathVariable Long id,
                                                          @Valid @RequestBody ResourceTypeRequestDto dto) {
        log.debug("PUT /v1/resource-types/{}", id);
        ResourceTypeResponseDto response = resourceTypeService.update(id, dto);
        log.info("Tipo de recurso actualizado vía controller: id={}", id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/activation")
    @Operation(summary = "Activar tipo de recurso",
               description = "Reactiva un tipo de recurso previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el tipo de recurso no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/resource-types/{}/activation", id);
        resourceTypeService.activate(id);
        log.info("Tipo de recurso activado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @Operation(summary = "Desactivar tipo de recurso",
               description = "Soft-delete idempotente. 204 si queda inactivo; 404 si el tipo de recurso no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/resource-types/{}/activation", id);
        resourceTypeService.deactivate(id);
        log.info("Tipo de recurso desactivado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
