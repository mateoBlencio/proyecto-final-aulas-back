package ar.edu.utn.frc.siga.audit.controller;

import ar.edu.utn.frc.siga.audit.RevisionKind;
import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUBSECRETARIA')")
@Tag(name = "Auditoría", description = "Registro unificado de revisiones de todas las entidades auditadas")
public class AuditRegistryController {

    private final AuditRegistryService auditRegistryService;

    @GetMapping
    @Operation(summary = "Listar el registro de auditoría",
               description = "Log paginado ordenado por revisión descendente. Cada entrada es una operación "
                       + "de negocio en lote (type=OPERATION, con recordCount y entityTypes; el detalle se pide "
                       + "en /audit/operations/{operationId}) o un cambio individual suelto (type=CHANGE, con "
                       + "kind, entityType y recordId). Todas traen 'description' de lo que se cambió. Filtros "
                       + "opcionales por rango de fechas, usuario, tipo de entidad y tipo de cambio. 400 si "
                       + "'entityType' no es un tipo conocido o si 'to' es anterior a 'from'.")
    public ResponseEntity<Page<AuditLogEntryDto>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) RevisionKind kind,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit: from={}, to={}, user={}, entityType={}, kind={}", from, to, user, entityType, kind);
        AuditLogFilter filter = new AuditLogFilter(from, to, user, entityType, kind);
        Page<AuditLogEntryDto> page = auditRegistryService.findAll(filter, pageable);
        log.info("Registro de auditoría consultado: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/operations/{operationId}")
    @Operation(summary = "Detalle de una operación en lote",
               description = "Cambios individuales (type=CHANGE) que componen la operación, paginados y "
                       + "ordenados por revisión descendente. Página vacía si el operationId no existe.")
    public ResponseEntity<Page<AuditLogEntryDto>> findOperationItems(
            @PathVariable String operationId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit/operations/{}", operationId);
        return ResponseEntity.ok(auditRegistryService.findOperationItems(operationId, pageable));
    }
}
