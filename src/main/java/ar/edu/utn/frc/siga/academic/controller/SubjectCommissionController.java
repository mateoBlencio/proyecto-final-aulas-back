package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
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
@RequestMapping("${siga.api.base-path}/subject-commissions")
@RequiredArgsConstructor
@Tag(name = "Materia-Comisión", description = "Consulta de materias dictadas por comisión")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class SubjectCommissionController {

    private final SubjectCommissionService subjectCommissionService;

    @GetMapping
    @Operation(summary = "Listar materia-comisión",
               description = "Listado paginado con filtros opcionales por materia y comisión. "
                       + "Por defecto solo devuelve los vínculos activos; con includeDeactivated=true "
                       + "incluye también los desactivados.")
    public ResponseEntity<Page<SubjectCommissionResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id.subjectId", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long commissionId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/subject-commissions?subjectId={}&commissionId={}&includeDeactivated={}",
                subjectId, commissionId, includeDeactivated);
        SubjectCommissionFilter filter = new SubjectCommissionFilter(subjectId, commissionId);
        return ResponseEntity.ok(subjectCommissionService.findAll(filter, pageable, includeDeactivated));
    }

    @PutMapping("/{subjectId}/{commissionId}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Activar materia-comisión",
               description = "Reactiva un vínculo materia-comisión previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el vínculo no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long subjectId, @PathVariable Long commissionId) {
        log.debug("PUT /v1/subject-commissions/{}/{}/activation", subjectId, commissionId);
        subjectCommissionService.activate(new SubjectCommissionId(subjectId, commissionId));
        log.info("Materia-comisión activada vía controller: subjectId={}, commissionId={}", subjectId, commissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{subjectId}/{commissionId}/activation")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Desactivar materia-comisión",
               description = "Soft-delete idempotente. 204 si queda inactivo; 404 si el vínculo no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long subjectId, @PathVariable Long commissionId) {
        log.debug("DELETE /v1/subject-commissions/{}/{}/activation", subjectId, commissionId);
        subjectCommissionService.deactivate(new SubjectCommissionId(subjectId, commissionId));
        log.info("Materia-comisión desactivada vía controller: subjectId={}, commissionId={}", subjectId, commissionId);
        return ResponseEntity.noContent().build();
    }
}
