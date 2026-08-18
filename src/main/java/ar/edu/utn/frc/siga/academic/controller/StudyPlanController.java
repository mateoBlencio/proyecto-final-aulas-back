package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/study-plans")
@RequiredArgsConstructor
@Tag(name = "Planes de estudio", description = "Consulta del catálogo de planes de estudio")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    @GetMapping
    @Operation(summary = "Listar planes de estudio")
    public ResponseEntity<List<StudyPlanResponseDto>> findAll() {
        log.debug("GET /v1/study-plans");
        return ResponseEntity.ok(studyPlanService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener plan de estudio por id")
    public ResponseEntity<StudyPlanResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/study-plans/{}", id);
        return ResponseEntity.ok(studyPlanService.findById(id));
    }
}
