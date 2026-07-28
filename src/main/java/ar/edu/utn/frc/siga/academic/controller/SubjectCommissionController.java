package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de la relación materia-comisión (inscriptos por materia dictada en una comisión).
 */
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
                       + "filtra las comisiones vinculadas a esa materia.")
    public ResponseEntity<List<SubjectCommissionResponseDto>> findAll(
            @RequestParam(required = false) Long subjectId) {
        log.debug("GET /v1/subject-commissions?subjectId={}", subjectId);
        List<SubjectCommissionResponseDto> result = subjectId != null
                ? subjectCommissionService.findBySubjectId(subjectId)
                : subjectCommissionService.findAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener materia-comisión por id")
    public ResponseEntity<SubjectCommissionResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/subject-commissions/{}", id);
        return ResponseEntity.ok(subjectCommissionService.findById(id));
    }
}
