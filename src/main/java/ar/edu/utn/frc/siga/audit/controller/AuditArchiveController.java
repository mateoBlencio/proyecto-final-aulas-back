package ar.edu.utn.frc.siga.audit.controller;

import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveResultDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchivedCycleDto;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveExportService;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/audit/archive")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Registro unificado de revisiones de todas las entidades auditadas")
public class AuditArchiveController {

    private final AuditArchiveService archiveService;
    private final AuditArchiveExportService exportService;

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

    @GetMapping("/cycles")
    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    @Operation(summary = "Listar los ciclos lectivos archivados",
               description = "Un ciclo por cada archivado completo, del más reciente al más antiguo. 'cycle' es el ciclo "
                       + "lectivo cuya auditoría quedó en el schema 'archivo'; 'from' y 'to' son el rango de fechas de "
                       + "revisión que cubre (from nulo en el primero) y 'occurrenceRows' y 'allocationRows' las filas "
                       + "de cada tabla en ese rango.")
    public List<AuditArchivedCycleDto> findArchivedCycles() {
        log.debug("GET /v1/audit/archive/cycles");
        return exportService.findArchivedCycles();
    }

    @GetMapping("/cycles/{cycle}/export")
    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    @Operation(summary = "Descargar la auditoría archivada de un ciclo lectivo",
               description = "ZIP con asignaciones.csv y ocurrencias.csv. Cada fila es una revisión: fecha_revision, "
                       + "usuario, tipo_actor, descripcion, operacion_id y las columnas de la tabla de auditoría "
                       + "(revtype sale como CREATED, MODIFIED o DELETED). CSV UTF-8 con BOM, separador ';' y comillas "
                       + "según RFC 4180; las celdas de texto que empiezan con =, +, -, @, tab o CR llevan un prefijo "
                       + "'. 404 si el ciclo no está archivado.",
               responses = {
                       @ApiResponse(responseCode = "200", description = "Archivo ZIP",
                               content = @Content(mediaType = "application/zip")),
                       @ApiResponse(responseCode = "404", description = "El ciclo no está archivado")})
    public void exportCycle(@PathVariable int cycle, HttpServletResponse response) {
        log.debug("GET /v1/audit/archive/cycles/{}/export", cycle);
        try {
            exportService.exportCycle(cycle, () -> {
                response.setContentType("application/zip");
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"auditoria-archivada-ciclo-" + cycle + ".zip\"");
                try {
                    return response.getOutputStream();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            log.debug("Descarga de auditoría archivada interrumpida", e);
        }
    }
}
