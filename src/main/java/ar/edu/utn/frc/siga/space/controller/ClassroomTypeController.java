package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomTypeResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/classroom-types")
@RequiredArgsConstructor
@Tag(name = "Tipos de aula", description = "Activación de tipos de aula")
@PreAuthorize("hasRole('SUBSECRETARIA')")
public class ClassroomTypeController {

    private final ClassroomTypeService classroomTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar tipos de aula",
               description = "Por defecto devuelve solo los tipos activos; con includeDeactivated=true incluye "
                       + "también los desactivados. Pensado para poblar el combo de edición de aulas.")
    public ResponseEntity<List<ClassroomTypeResponseDto>> findAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/classroom-types: includeDeactivated={}", includeDeactivated);
        return ResponseEntity.ok(classroomTypeService.findAll(includeDeactivated));
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
