package ar.edu.utn.frc.siga.academic.controller;

import ar.edu.utn.frc.siga.academic.dto.CommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
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
@RequestMapping("${siga.api.base-path}/commissions")
@RequiredArgsConstructor
@Tag(name = "Comisiones", description = "Consulta de comisiones")
@PreAuthorize("hasAuthority('PERM_ACADEMIC_READ')")
public class CommissionController {

    private final CommissionService commissionService;

    @GetMapping
    @Operation(summary = "Listar comisiones",
               description = "Listado paginado con filtros opcionales por código de curso (contiene, "
                       + "case-insensitive) y período académico. Por defecto solo devuelve las activas; "
                       + "con includeDeactivated=true incluye también las desactivadas.")
    public ResponseEntity<Page<CommissionResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) Long academicPeriodId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeactivated) {
        log.debug("GET /v1/commissions?courseCode={}&academicPeriodId={}&includeDeactivated={}",
                courseCode, academicPeriodId, includeDeactivated);
        CommissionFilter filter = new CommissionFilter(courseCode, academicPeriodId);
        return ResponseEntity.ok(commissionService.findAll(filter, pageable, includeDeactivated));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener comisión por id")
    public ResponseEntity<CommissionResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/commissions/{}", id);
        return ResponseEntity.ok(commissionService.findById(id));
    }

    @PutMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Activar comisión",
               description = "Reactiva una comisión previamente desactivada (idempotente). "
                       + "204 si queda activa; 404 si la comisión no existe.")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.debug("PUT /v1/commissions/{}/activation", id);
        commissionService.activate(id);
        log.info("Comisión activada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('PERM_ACADEMIC_ACTIVATE')")
    @Operation(summary = "Desactivar comisión",
               description = "Soft-delete idempotente. 204 si queda inactiva; 404 si la comisión no existe.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.debug("DELETE /v1/commissions/{}/activation", id);
        commissionService.deactivate(id);
        log.info("Comisión desactivada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
