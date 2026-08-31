package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.request.ClassroomTypeRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
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
@RequestMapping("${siga.api.base-path}/classroom-types")
@RequiredArgsConstructor
@Tag(name = "Tipos de aula", description = "ABM de tipos de aula")
@PreAuthorize("hasRole('SUBSECRETARIA')")
public class ClassroomTypeController {

    private final ClassroomTypeService classroomTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar tipos de aula",
               description = "Listado paginado. Por defecto devuelve solo los tipos activos; con "
                       + "includeDeactivated=true incluye también los desactivados. Pensado para poblar el "
                       + "combo de edición de aulas.")
    public ResponseEntity<Page<ClassroomTypeResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/classroom-types: includeDeactivated={}, page={}", includeDeactivated,
                pageable.getPageNumber());
        return ResponseEntity.ok(classroomTypeService.findAll(includeDeactivated, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Buscar tipo de aula por id", description = "404 si el tipo de aula no existe.")
    public ResponseEntity<ClassroomTypeResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/classroom-types/{}", id);
        return ResponseEntity.ok(classroomTypeService.findDtoById(id));
    }

    @PostMapping
    @Operation(summary = "Crear tipo de aula",
               description = "400 si ya existe un tipo de aula con esa descripción (incluye desactivados).")
    public ResponseEntity<ClassroomTypeResponseDto> create(@Valid @RequestBody ClassroomTypeRequestDto dto) {
        log.debug("POST /v1/classroom-types: description={}", dto.description());
        ClassroomTypeResponseDto response = classroomTypeService.create(dto);
        log.info("Tipo de aula creado vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar tipo de aula",
               description = "Actualiza la descripción. 400 si la descripción ya la usa otro tipo de aula "
                       + "(incluye desactivados); 404 si el tipo de aula no existe.")
    public ResponseEntity<ClassroomTypeResponseDto> update(@PathVariable Long id,
                                                           @Valid @RequestBody ClassroomTypeRequestDto dto) {
        log.debug("PUT /v1/classroom-types/{}", id);
        ClassroomTypeResponseDto response = classroomTypeService.update(id, dto);
        log.info("Tipo de aula actualizado vía controller: id={}", id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar tipo de aula",
               description = "Reactiva un tipo de aula previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el tipo de aula no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/classroom-types/{}/activation", id);
        classroomTypeService.activate(id);
        log.info("Tipo de aula activado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Desactivar tipo de aula",
               description = "Soft-delete idempotente. 204 si queda inactivo; 404 si el tipo de aula no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/classroom-types/{}/activation", id);
        classroomTypeService.deactivate(id);
        log.info("Tipo de aula desactivado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
