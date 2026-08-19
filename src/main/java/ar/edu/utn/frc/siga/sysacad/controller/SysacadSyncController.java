package ar.edu.utn.frc.siga.sysacad.controller;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.internal.SysacadSyncOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@PreAuthorize("hasRole('SUBSECRETARIA')")
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
@Tag(name = "Sincronización con SysAcad", description = "Disparo manual y estado del sync de catálogos")
public class SysacadSyncController {

    private final SysacadSyncOrchestrator orchestrator;
    private final SysacadSyncStateService syncStateService;

    @PostMapping
    @Operation(summary = "Resincronizar todas las vistas de SysAcad",
               description = "Dispara el resync de Edificios, Aulas, Especialidades y Comisiones en el orden de "
                       + "FK. Corre asíncrono: responde 202 de inmediato con el estado previo del sync, sin "
                       + "esperar a SysAcad. Si ya hay un resync en curso, el disparo se ignora.")
    public ResponseEntity<List<SysacadSyncStateDto>> resyncAll() {
        log.info("POST /v1/sysacad/sync: resync manual de todas las vistas");
        orchestrator.resyncAll();
        return ResponseEntity.accepted().body(syncStateService.findAll());
    }

    @GetMapping
    @Operation(summary = "Consultar el estado del sync",
               description = "Devuelve, por vista, el último sync exitoso, las filas afectadas y el último error.")
    public ResponseEntity<List<SysacadSyncStateDto>> findState() {
        log.debug("GET /v1/sysacad/sync");
        return ResponseEntity.ok(syncStateService.findAll());
    }
}
