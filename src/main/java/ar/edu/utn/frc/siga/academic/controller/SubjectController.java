package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
               description = "Listado paginado con filtros opcionales por código, nombre (contiene, "
                       + "case-insensitive), código de especialidad y plan de estudio. "
                       + "Por defecto solo devuelve las activas; con includeDeactivated=true incluye "
                       + "también las desactivadas.")
    public ResponseEntity<Page<SubjectResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer specialtyCode,
            @RequestParam(required = false) Long studyPlanId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/subjects?code={}&name={}&specialtyCode={}&studyPlanId={}&includeDeactivated={}",
                code, name, specialtyCode, studyPlanId, includeDeactivated);
        SubjectFilter filter = new SubjectFilter(code, name, specialtyCode, studyPlanId);
        return ResponseEntity.ok(subjectService.findAll(filter, pageable, includeDeactivated));
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
