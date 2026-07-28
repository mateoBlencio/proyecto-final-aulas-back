package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
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
 * Consulta de materias (catálogo cargado por fuera de la app, ver {@code excelimport}).
 */
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
                       + "filtra las materias de todos los planes de esa especialidad.")
    public ResponseEntity<List<SubjectResponseDto>> findAll(
            @RequestParam(required = false) Integer specialtyCode) {
        log.debug("GET /v1/subjects?specialtyCode={}", specialtyCode);
        List<SubjectResponseDto> subjects = specialtyCode != null
                ? subjectService.findBySpecialtyCode(specialtyCode)
                : subjectService.findAll();
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener materia por id")
    public ResponseEntity<SubjectResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/subjects/{}", id);
        return ResponseEntity.ok(subjectService.findById(id));
    }
}
