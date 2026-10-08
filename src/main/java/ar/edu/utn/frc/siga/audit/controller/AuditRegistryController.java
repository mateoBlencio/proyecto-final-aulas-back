package ar.edu.utn.frc.siga.audit.controller;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/audit")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
@Tag(name = "Auditoría", description = "Registro unificado de revisiones de todas las entidades auditadas")
public class AuditRegistryController {

    private final AuditRegistryService auditRegistryService;

    @GetMapping
    @Operation(summary = "Listar el registro de auditoría",
               description = "Log paginado ordenado por revisión descendente. Cada entrada es: una operación "
                       + "de negocio (type=OPERATION, agrupa todas las revisiones con el mismo operationId; "
                       + "detalle en /audit/operations/{operationId}), una transacción sin operación que tocó "
                       + "más de un registro (type=TRANSACTION, una revisión de Envers; detalle en "
                       + "/audit/revisions/{revision}) o un cambio individual suelto (type=CHANGE, con kind, "
                       + "entityType y recordId). OPERATION y TRANSACTION traen recordCount y entityTypes, y "
                       + "kind solo si todas sus filas comparten el tipo de cambio. Todas traen 'description'. "
                       + "Filtros opcionales: 'from', 'to' y 'user' filtran revisiones; 'entityType' y 'kind' "
                       + "filtran filas antes de agrupar, así que una entrada aparece si le queda al menos una "
                       + "fila, y recordCount y entityTypes cuentan solo las filas que pasan el filtro (una "
                       + "revisión con varias filas puede pasar de TRANSACTION a CHANGE al filtrar). 400 si "
                       + "'entityType' no es un tipo conocido o si se envían las dos fechas y 'to' es anterior "
                       + "a 'from'. 'actor' (HUMAN o SYSTEM) filtra por quién hizo el cambio: una persona o un "
                       + "proceso del sistema; 400 si no es uno de esos valores. 'q' busca texto (sin distinguir "
                       + "mayúsculas) en la descripción guardada de la operación, que solo existe para "
                       + "type=OPERATION: un CHANGE o TRANSACTION sin operación nunca coincide con 'q'.")
    public ResponseEntity<Page<AuditLogEntryDto>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) RevisionKind kind,
            @RequestParam(required = false) ActorType actor,
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit: from={}, to={}, user={}, entityType={}, kind={}", from, to, user, entityType, kind);
        Page<AuditLogEntryDto> page = auditRegistryService.findAll(
                new AuditLogFilter(from, to, user, entityType, kind, actor, q), pageable);
        log.info("Registro de auditoría consultado: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/operations/{operationId}")
    @Operation(summary = "Detalle de una operación en lote",
               description = "Cambios individuales (type=CHANGE) que componen la operación, con el diff por campo "
                       + "en 'changes', paginados y "
                       + "ordenados por revisión descendente. Página vacía si el operationId no existe. "
                       + "Filtros opcionales iguales a GET /v1/audit; con los mismos filtros, totalElements "
                       + "coincide con recordCount de la entrada. 'actor' y 'q' también aplican.")
    public ResponseEntity<Page<AuditLogEntryDto>> findOperationItems(
            @PathVariable String operationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) RevisionKind kind,
            @RequestParam(required = false) ActorType actor,
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit/operations/{}", operationId);
        return ResponseEntity.ok(auditRegistryService.findOperationItems(
                operationId, new AuditLogFilter(from, to, user, entityType, kind, actor, q), pageable));
    }

    @GetMapping("/revisions/{revision}")
    @Operation(summary = "Detalle de una transacción (revisión de Envers)",
               description = "Cambios (type=CHANGE) de una revisión de Envers (una transacción), con el diff por campo "
                       + "en 'changes', paginados, "
                       + "ordenados por revisión descendente, tipo de entidad y recordId. Página vacía si la "
                       + "revisión no existe. Filtros opcionales iguales a GET /v1/audit; con los mismos "
                       + "filtros, totalElements coincide con recordCount de la entrada. 400 por 'entityType' "
                       + "desconocido o si 'to' es anterior a 'from'.")
    public ResponseEntity<Page<AuditLogEntryDto>> findRevisionItems(
            @PathVariable int revision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) RevisionKind kind,
            @RequestParam(required = false) ActorType actor,
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit/revisions/{}", revision);
        return ResponseEntity.ok(auditRegistryService.findRevisionItems(
                revision, new AuditLogFilter(from, to, user, entityType, kind, actor, q), pageable));
    }

    @GetMapping("/entity-types")
    @Operation(summary = "Tipos de entidad auditados",
               description = "Etiquetas de dominio aceptadas por el parámetro 'entityType' de "
                       + "GET /v1/audit. Se descubren en runtime a partir de las entidades @Audited.")
    public ResponseEntity<List<String>> findEntityTypes() {
        return ResponseEntity.ok(auditRegistryService.findEntityTypes());
    }
}
