package ar.edu.utn.frc.siga.dashboard.controller;

import ar.edu.utn.frc.siga.dashboard.dto.request.SnapshotRegenerationRequestDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.AllocationTrendResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardPeriodResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardReportResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestStatsResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import ar.edu.utn.frc.siga.dashboard.service.DashboardQueryService;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.security.Principal;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final DashboardSnapshotService snapshotService;

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

    @GetMapping("/period")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Tablero de un período",
               description = "Cuatrimestre en curso (TERM, por defecto), año académico (ACADEMIC_YEAR) o mes en curso "
                       + "(CURRENT_MONTH), desde el inicio del período hasta hoy. Fuera de cuatrimestre devuelve "
                       + "completo el último que empezó. 400 si type no es válido.")
    public ResponseEntity<DashboardPeriodResponseDto> period(
            @RequestParam(defaultValue = "TERM") DashboardPeriodType type) {
        log.debug("GET /v1/dashboard/period?type={}", type);
        return ResponseEntity.ok(queryService.period(type));
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

    @GetMapping("/allocation-trend")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Tendencia mensual de asignaciones",
               description = "Serie de meses cerrados (snapshots), mes en curso parcial calculado en vivo, "
                       + "línea de base y último mes cerrado.")
    public ResponseEntity<AllocationTrendResponseDto> allocationTrend() {
        log.debug("GET /v1/dashboard/allocation-trend");
        return ResponseEntity.ok(queryService.allocationTrend());
    }

    @GetMapping("/reports/{month}")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_READ')")
    @Operation(summary = "Informe de un mes cerrado",
               description = "Informe leído del snapshot del mes (yyyy-MM): campus, edificios, solicitudes del mes, "
                       + "parámetros y versión de reglas. 404 si el mes no tiene snapshot.")
    public ResponseEntity<DashboardReportResponseDto> report(@PathVariable YearMonth month) {
        log.debug("GET /v1/dashboard/reports/{}", month);
        return ResponseEntity.ok(queryService.report(month));
    }

    @PostMapping("/reports/{month}/regeneration")
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_SNAPSHOT_REGENERATE')")
    @Operation(summary = "Regenerar el informe de un mes cerrado",
               description = "Solo Subsecretaría. Marca las filas vigentes del mes como no vigentes (se conservan) y "
                       + "genera nuevas con los datos actuales. El motivo es obligatorio y la operación queda auditada. "
                       + "422 si el mes no está cerrado, 404 si no tiene snapshot.")
    public ResponseEntity<DashboardReportResponseDto> regenerate(
            @PathVariable YearMonth month, @Valid @RequestBody SnapshotRegenerationRequestDto dto, Principal principal) {
        log.debug("POST /v1/dashboard/reports/{}/regeneration", month);
        return ResponseEntity.ok(snapshotService.regenerate(month, dto.reason(), principal.getName()));
    }
}
