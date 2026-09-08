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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/specialties")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Consulta del catálogo de especialidades")
@PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @Operation(summary = "Listar especialidades",
               description = "Listado paginado con filtros opcionales por código y nombre (contiene, "
                       + "case-insensitive).")
    public ResponseEntity<Page<SpecialtyResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer specialtyCode,
            @RequestParam(required = false) String name) {
        log.debug("GET /v1/specialties?specialtyCode={}&name={}", specialtyCode, name);
        return ResponseEntity.ok(specialtyService.findAll(new SpecialtyFilter(specialtyCode, name), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener especialidad por id")
    public ResponseEntity<SpecialtyResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/specialties/{}", id);
        return ResponseEntity.ok(specialtyService.findById(id));
    }
}
