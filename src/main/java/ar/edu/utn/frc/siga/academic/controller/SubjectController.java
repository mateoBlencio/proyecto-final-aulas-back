package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/subjects")
@RequiredArgsConstructor
@Tag(name = "Materias", description = "Consulta del catálogo de materias")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    @Operation(summary = "Listar materias",
               description = "Sin parámetros devuelve el catálogo completo. Con specialtyCode, "
                       + "filtra las materias de todos los planes de esa especialidad. "
                       + "Por defecto solo devuelve las activas; con includeDeactivated=true incluye "
                       + "también las desactivadas.")
    public ResponseEntity<List<SubjectResponseDto>> findAll(
            @RequestParam(required = false) Integer specialtyCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/subjects?specialtyCode={}&includeDeactivated={}", specialtyCode, includeDeactivated);
        List<SubjectResponseDto> subjects = specialtyCode != null
                ? subjectService.findBySpecialtyCode(specialtyCode, includeDeactivated)
                : subjectService.findAll(includeDeactivated);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener materia por id")
    public ResponseEntity<SubjectResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/subjects/{}", id);
        return ResponseEntity.ok(subjectService.findById(id));
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar materia",
               description = "Reactiva una materia previamente desactivada (idempotente). "
                       + "204 si queda activa; 404 si la materia no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/subjects/{}/activation", id);
        subjectService.activate(id);
        log.info("Materia activada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Desactivar materia",
               description = "Soft-delete idempotente. 204 si queda inactiva; 404 si la materia no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/subjects/{}/activation", id);
        subjectService.deactivate(id);
        log.info("Materia desactivada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
