package ar.edu.utn.frc.siga.dashboard.controller;

import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestStatsResponseDto;
import ar.edu.utn.frc.siga.dashboard.service.DashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/dashboard")
@RequiredArgsConstructor
@Validated
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

    @GetMapping("/badges")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Contadores del menú",
               description = "Solicitudes que esperan acción del usuario, solicitudes por vencer en 48 horas, "
                       + "clases sin aula y conflictos en los próximos 7 días. Liviano: se puede consultar cada minuto.")
    public ResponseEntity<DashboardBadgesResponseDto> badges() {
        log.debug("GET /v1/dashboard/badges");
        return ResponseEntity.ok(queryService.badges());
    }

    @GetMapping("/room-requests")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Solicitudes de aula por año",
               description = "Ítems resueltos, pendientes y vencidos sin respuesta por año y por edificio. "
                       + "years entre 1 y 10 (por defecto 5); solo se devuelven los años con algún conteo.")
    public ResponseEntity<RoomRequestStatsResponseDto> roomRequests(
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int years) {
        log.debug("GET /v1/dashboard/room-requests?years={}", years);
        return ResponseEntity.ok(queryService.roomRequests(years));
    }
}
