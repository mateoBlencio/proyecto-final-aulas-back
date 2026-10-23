package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
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
@RequestMapping("${siga.api.base-path}/specialties")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Consulta del catálogo de especialidades")
@PreAuthorize("hasAuthority('PERM_ACADEMIC_READ')")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @Operation(summary = "Listar especialidades",
               description = "Listado paginado con filtros opcionales por código y nombre (contiene, "
                       + "case-insensitive). Con hasSubjects=true solo devuelve especialidades con alguna materia "
                       + "vigente. Cada especialidad trae commissionCount: cantidad de comisiones con alguna materia-comisión "
                       + "activa de esa especialidad, con los mismos criterios que GET /commissions?specialtyCode= "
                       + "(commissionCourseCode y commissionAcademicPeriodId acotan el conteo). subjectCount: cantidad de "
                       + "materias de la especialidad, con los mismos criterios que GET /subjects?specialtyCode= "
                       + "(subjectCode, subjectName y studyPlanId acotan el conteo). Por defecto solo devuelve las activas; con includeDeactivated=true incluye también "
                       + "las desactivadas. q: búsqueda de texto libre sobre código, nombre y abreviatura (no altera los conteos).")
    public ResponseEntity<Page<SpecialtyResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer specialtyCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean hasSubjects,
            @RequestParam(required = false) String commissionCourseCode,
            @RequestParam(required = false) Long commissionAcademicPeriodId,
            @RequestParam(required = false) Integer subjectCode,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) Long studyPlanId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated,
            @RequestParam(required = false) String q) {
        log.debug("GET /v1/specialties?q={}&specialtyCode={}&name={}&hasSubjects={}&commissionCourseCode={}"
                        + "&commissionAcademicPeriodId={}&subjectCode={}&subjectName={}&studyPlanId={}"
                        + "&includeDeactivated={}",
                q, specialtyCode, name, hasSubjects, commissionCourseCode, commissionAcademicPeriodId, subjectCode,
                subjectName, studyPlanId, includeDeactivated);
        SpecialtyFilter filter = new SpecialtyFilter(specialtyCode, name, hasSubjects, commissionCourseCode,
                commissionAcademicPeriodId, subjectCode, subjectName, studyPlanId, q);
        return ResponseEntity.ok(specialtyService.findAll(filter, pageable, includeDeactivated));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener especialidad por id")
    public ResponseEntity<SpecialtyResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/specialties/{}", id);
        return ResponseEntity.ok(specialtyService.findById(id));
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Activar especialidad",
               description = "Reactiva una especialidad previamente desactivada (idempotente). "
                       + "204 si queda activa; 404 si la especialidad no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/specialties/{}/activation", id);
        specialtyService.activate(id);
        log.info("Especialidad activada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Desactivar especialidad",
               description = "Soft-delete idempotente. 204 si queda inactiva; 404 si la especialidad no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/specialties/{}/activation", id);
        specialtyService.deactivate(id);
        log.info("Especialidad desactivada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
