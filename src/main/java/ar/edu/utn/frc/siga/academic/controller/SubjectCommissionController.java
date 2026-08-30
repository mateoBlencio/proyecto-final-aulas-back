package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
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
@RequestMapping("${siga.api.base-path}/subject-commissions")
@RequiredArgsConstructor
@Tag(name = "Materia-Comisión", description = "Consulta de materias dictadas por comisión")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class SubjectCommissionController {

    private final SubjectCommissionService subjectCommissionService;

    @GetMapping
    @Operation(summary = "Listar materia-comisión",
               description = "Sin parámetros devuelve el catálogo completo. Con subjectId, "
                       + "filtra las comisiones vinculadas a esa materia. "
                       + "Por defecto solo devuelve los vínculos activos; con includeDeactivated=true "
                       + "incluye también los desactivados.")
    public ResponseEntity<List<SubjectCommissionResponseDto>> findAll(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/subject-commissions?subjectId={}&includeDeactivated={}", subjectId, includeDeactivated);
        List<SubjectCommissionResponseDto> result = subjectId != null
                ? subjectCommissionService.findBySubjectId(subjectId, includeDeactivated)
                : subjectCommissionService.findAll(includeDeactivated);
        return ResponseEntity.ok(result);
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
