package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
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
@RequestMapping("${siga.api.base-path}/specialties")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Consulta del catálogo de especialidades")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @Operation(summary = "Listar especialidades")
    public ResponseEntity<List<SpecialtyResponseDto>> findAll() {
        log.debug("GET /v1/specialties");
        return ResponseEntity.ok(specialtyService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener especialidad por id")
    public ResponseEntity<SpecialtyResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/specialties/{}", id);
        return ResponseEntity.ok(specialtyService.findById(id));
    }
}
