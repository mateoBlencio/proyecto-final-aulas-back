package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.StudyPlanFilter;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
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
@RequestMapping("${siga.api.base-path}/study-plans")
@RequiredArgsConstructor
@Tag(name = "Planes de estudio", description = "Consulta del catálogo de planes de estudio")
@PreAuthorize("hasAuthority('PERM_ACADEMIC_READ')")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @GetMapping
    @Operation(summary = "Listar planes de estudio",
               description = "Listado paginado con filtros opcionales por código de plan y de especialidad. "
                       + "Por defecto solo devuelve los activos; con includeDeactivated=true incluye "
                       + "también los desactivados.")
    public ResponseEntity<Page<StudyPlanResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer planCode,
            @RequestParam(required = false) Integer specialtyCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/study-plans?planCode={}&specialtyCode={}&includeDeactivated={}",
                planCode, specialtyCode, includeDeactivated);
        StudyPlanFilter filter = new StudyPlanFilter(planCode, specialtyCode);
        return ResponseEntity.ok(studyPlanService.findAll(filter, pageable, includeDeactivated));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener plan de estudio por id")
    public ResponseEntity<StudyPlanResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/study-plans/{}", id);
        return ResponseEntity.ok(studyPlanService.findById(id));
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Activar plan de estudio",
               description = "Reactiva un plan de estudio previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el plan no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/study-plans/{}/activation", id);
        studyPlanService.activate(id);
        log.info("Plan de estudio activado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Desactivar plan de estudio",
               description = "Soft-delete idempotente. 204 si queda inactivo; 404 si el plan no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/study-plans/{}/activation", id);
        studyPlanService.deactivate(id);
        log.info("Plan de estudio desactivado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
