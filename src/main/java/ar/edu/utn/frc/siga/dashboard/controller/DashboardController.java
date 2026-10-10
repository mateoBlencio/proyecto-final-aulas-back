package ar.edu.utn.frc.siga.dashboard.controller;

import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.service.DashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tablero", description = "Indicadores de asignación de aulas, solicitudes e informes mensuales")
public class DashboardController {

    private final DashboardQueryService queryService;

    @GetMapping("/week")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Semana del tablero",
               description = "Campus y edificios visibles para el usuario en la semana (lunes a sábado) que contiene "
                       + "weekOf (yyyy-MM-dd, por defecto la semana en curso).")
    public ResponseEntity<DashboardWeekResponseDto> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        log.debug("GET /v1/dashboard/week?weekOf={}", weekOf);
        return ResponseEntity.ok(queryService.week(weekOf));
    }
}
