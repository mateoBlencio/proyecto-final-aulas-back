package ar.edu.utn.frc.siga.sysacad.controller;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/sysacad/sync")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
@Tag(name = "Sincronización con SysAcad", description = "Disparo manual y estado del sync de catálogos")
public class SysacadSyncController {

    private final SysacadSyncOrchestrator orchestrator;
    private final SysacadSyncStateService syncStateService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SYSACAD_SYNC')")
    @Operation(summary = "Resincronizar todas las vistas de SysAcad",
               description = "Dispara el resync de Edificios, Aulas, Especialidades y Comisiones en el orden de "
                       + "FK. Espera a que termine (incluyendo reintentos ante errores transitorios) y devuelve "
                       + "el estado resultante, con el error de cada vista si lo hubo. Responde 200 si las 4 "
                       + "vistas sincronizaron bien, 207 si al menos una falló por datos, 503 si no se pudo "
                       + "establecer conexión con SysAcad (red/VPN caída). Si ya hay un resync en curso, el "
                       + "disparo se ignora y devuelve 200 con el estado previo.")
    public ResponseEntity<List<SysacadSyncStateDto>> resyncAll() {
        log.info("POST /v1/sysacad/sync: resync manual de todas las vistas");
        for (SysacadView view : SysacadView.values()) {
            syncStateService.ensureExists(view);
        }
        SysacadResyncOutcome outcome = orchestrator.resyncAll();
        HttpStatus status = switch (outcome) {
            case SUCCESS -> HttpStatus.OK;
            case PARTIAL_FAILURE -> HttpStatus.MULTI_STATUS;
            case CONNECTIVITY_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(syncStateService.findAll());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SYSACAD_READ')")
    @Operation(summary = "Consultar el estado del sync",
               description = "Devuelve, por vista, el último sync exitoso, las filas afectadas y el último error.")
    public ResponseEntity<List<SysacadSyncStateDto>> findState() {
        log.debug("GET /v1/sysacad/sync");
        return ResponseEntity.ok(syncStateService.findAll());
    }
}
