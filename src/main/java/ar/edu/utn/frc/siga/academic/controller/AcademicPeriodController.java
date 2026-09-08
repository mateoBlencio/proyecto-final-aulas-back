package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.AcademicPeriodFilter;
import ar.edu.utn.frc.siga.academic.dto.request.UpdateAcademicPeriodRequestDto;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/academic-periods")
@RequiredArgsConstructor
@Tag(name = "Períodos académicos", description = "Consulta de períodos académicos")
@PreAuthorize("hasAuthority('PERM_ACADEMIC_READ')")
public class AcademicPeriodController {

    private final AcademicPeriodService academicPeriodService;

    @GetMapping
    @Operation(summary = "Listar períodos académicos",
               description = "Listado paginado con filtros opcionales por año y cuatrimestre. "
                       + "Por defecto solo devuelve los activos; con includeDeactivated=true incluye "
                       + "también los desactivados.")
    public ResponseEntity<Page<AcademicPeriodResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/academic-periods?year={}&semester={}&includeDeactivated={}",
                year, semester, includeDeactivated);
        AcademicPeriodFilter filter = new AcademicPeriodFilter(year, semester);
        return ResponseEntity.ok(academicPeriodService.findAll(filter, pageable, includeDeactivated));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener período académico por id")
    public ResponseEntity<AcademicPeriodResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/academic-periods/{}", id);
        return ResponseEntity.ok(academicPeriodService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Modificar un período académico",
               description = "Ajusta fecha de fin y, sólo para el período ANUAL, el receso invernal "
                       + "(inicio/fin). Al definir el receso en el ANUAL sincroniza el fin del 1.º "
                       + "cuatrimestre y el inicio del 2.º. Publica AcademicPeriodChanged: recalcula las "
                       + "ocurrencias futuras no asignadas de los eventos afectados. 404 si no existe; "
                       + "422 si la fecha de inicio ya ocurrió, el orden es inconsistente o se pide "
                       + "receso en un período no ANUAL.")
    public ResponseEntity<AcademicPeriodResponseDto> update(
            @PathVariable Long id, @Valid @RequestBody UpdateAcademicPeriodRequestDto dto) {
        log.debug("PUT /v1/academic-periods/{}: endDate={}, recessStart={}, recessEnd={}",
                id, dto.endDate(), dto.recessStart(), dto.recessEnd());
        AcademicPeriodResponseDto updated = academicPeriodService.update(id, dto);
        log.info("Período académico modificado vía controller: id={}", id);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Activar período académico",
               description = "Reactiva un período académico previamente desactivado (idempotente). "
                       + "204 si queda activo; 404 si el período no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/academic-periods/{}/activation", id);
        academicPeriodService.activate(id);
        log.info("Período académico activado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Desactivar período académico",
               description = "Soft-delete idempotente. 204 si queda inactivo; 404 si el período no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/academic-periods/{}/activation", id);
        academicPeriodService.deactivate(id);
        log.info("Período académico desactivado vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
