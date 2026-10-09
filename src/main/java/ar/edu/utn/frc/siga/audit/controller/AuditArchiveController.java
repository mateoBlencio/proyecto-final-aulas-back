package ar.edu.utn.frc.siga.audit.controller;

import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/audit/archive")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Registro unificado de revisiones de todas las entidades auditadas")
public class AuditArchiveController {

    private final AuditArchiveService archiveService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_AUDIT_ARCHIVE')")
    @Operation(summary = "Archivar la auditoría antigua",
               description = "Mueve al schema 'archivo' las revisiones de ocurrencias y asignaciones anteriores al "
                       + "inicio del ciclo lectivo previo al actual y las borra de las tablas activas. Un ciclo "
                       + "va del inicio de su año académico (marzo) al del siguiente. Espera a que termine y "
                       + "devuelve las filas movidas. Responde 200 también cuando no hay nada que archivar "
                       + "(NOTHING_TO_ARCHIVE), faltan períodos académicos (NO_PERIODS), las fechas de los períodos "
                       + "dan un corte inválido (INVALID_PERIODS) o otra ejecución tenía el lock "
                       + "(STOPPED_BY_CONCURRENT_RUN). Si un lote falla responde error y la corrida queda en estado "
                       + "FAILED. La request es síncrona: la primera corrida sobre una base con historial grande "
                       + "conviene lanzarla con el cron, porque puede tardar y un proxy puede cortar la request. "
                       + "Funciona aunque el cron esté apagado.")
    public AuditArchiveResultDto archive() {
        log.info("POST /v1/audit/archive: archivado manual de auditoría");
        return archiveService.archive(LocalDate.now());
    }
}
