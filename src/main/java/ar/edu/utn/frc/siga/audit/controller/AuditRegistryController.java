package ar.edu.utn.frc.siga.audit.controller;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditOperationChainDto;
import ar.edu.utn.frc.siga.audit.service.AuditRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/audit")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
@Tag(name = "Auditoría", description = "Registro unificado de revisiones de todas las entidades auditadas")
public class AuditRegistryController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

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

    @GetMapping("/export")
    @Operation(summary = "Exportar el registro de auditoría a CSV",
               description = "Mismos filtros que GET /v1/audit (from, to, user, entityType, kind, actor, q), sin "
                       + "paginación: una fila por entrada del listado (OPERATION, TRANSACTION o CHANGE). CSV UTF-8 "
                       + "con BOM, separador ';' y comillas según RFC 4180. Columnas: fecha, tipo, actor, usuario, "
                       + "descripcion, entidades, cantidad, tipo_cambio, registro (recordLabel o recordId), "
                       + "operacion, operacion_padre, revision. Las celdas de texto que empiezan con =, +, -, @, "
                       + "tab o CR llevan un prefijo ' contra inyección de fórmulas. 400 si el filtro devuelve más "
                       + "entradas que 'siga.audit.export.max-rows' (10000 por defecto): hay que acotar los filtros; "
                       + "no se trunca en silencio. También 400 por los mismos filtros inválidos que GET /v1/audit. "
                       + "'fecha' y el nombre del archivo están en hora de Buenos Aires. Si entran revisiones "
                       + "mientras se exporta, puede haber filas repetidas en el borde entre páginas. Un error "
                       + "después de enviado el primer byte deja el archivo truncado (queda en el log del servidor).",
               responses = {
                       @ApiResponse(responseCode = "200", description = "Archivo CSV",
                               content = @Content(mediaType = "text/csv")),
                       @ApiResponse(responseCode = "400", description = "Filtro inválido o con más entradas que "
                               + "el máximo exportable")})
    public void export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) RevisionKind kind,
            @RequestParam(required = false) ActorType actor,
            @RequestParam(required = false) @Size(max = 100) String q,
            HttpServletResponse response) {
        log.debug("GET /v1/audit/export: from={}, to={}, user={}, entityType={}, kind={}", from, to, user, entityType, kind);
        try {
            auditRegistryService.exportCsv(new AuditLogFilter(from, to, user, entityType, kind, actor, q), () -> {
                response.setContentType("text/csv; charset=UTF-8");
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria-"
                        + LocalDateTime.now().format(FILE_DATE) + ".csv\"");
                try {
                    return response.getOutputStream();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            // Client closed the connection mid-download: the response is already committed, nothing to answer.
            log.debug("Exportación de auditoría interrumpida", e);
        }
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

    @GetMapping("/operations/{operationId}/chain")
    @Operation(summary = "Cadena de causa y efecto de una operación",
               description = "Operaciones (type=OPERATION) de la cadena que contiene a operationId: la raíz, sus "
                       + "descendientes y la propia operación, hasta 10 niveles y 200 operaciones. Ordenadas por "
                       + "revisión ascendente (la causa primero). Sin filtros ni paginación. 'truncated' es true si "
                       + "la cadena está incompleta: se cortó a las 200 operaciones, o el recorrido llegó a los 10 "
                       + "niveles sin alcanzar la raíz real (la que no tiene padre) o las hojas. Si el operationId "
                       + "no existe, 'entries' es [] y 'truncated' false. "
                       + "Cada entrada trae 'parentOperationId' con la operación que la causó; el detalle de "
                       + "cada una está en /audit/operations/{operationId}.")
    public ResponseEntity<AuditOperationChainDto> findOperationChain(@PathVariable String operationId) {
        log.debug("GET /v1/audit/operations/{}/chain", operationId);
        return ResponseEntity.ok(auditRegistryService.findOperationChain(operationId));
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

    @GetMapping("/entities/{entityType}/{recordId}")
    @Operation(summary = "Historial de un registro",
               description = "Cambios (type=CHANGE) de un único registro a lo largo de todas sus revisiones, con el "
                       + "diff por campo en 'changes', paginados y ordenados por revisión descendente. "
                       + "'entityType' es una etiqueta de GET /v1/audit/entity-types tal cual (con espacios y "
                       + "acentos, codificada en la URL por el cliente, por ejemplo 'Asignaci%C3%B3n%20de%20rol'); "
                       + "'recordId' es el id del registro. Página vacía si el registro no tiene revisiones "
                       + "(incluye ids que nunca existieron). 400 si 'entityType' no es un tipo conocido o si "
                       + "'recordId' no tiene el formato del id de la entidad (por ejemplo, texto para un id numérico). "
                       + "Convive con /v1/allocations/history y el historial de eventos, que piden otros permisos "
                       + "y devuelven snapshots tipados.")
    public ResponseEntity<Page<AuditLogEntryDto>> findEntityHistory(
            @PathVariable String entityType,
            @PathVariable String recordId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/audit/entities/{}/{}", entityType, recordId);
        return ResponseEntity.ok(auditRegistryService.findEntityHistory(entityType, recordId, pageable));
    }

    @GetMapping("/entity-types")
    @Operation(summary = "Tipos de entidad auditados",
               description = "Etiquetas de dominio aceptadas por el parámetro 'entityType' de "
                       + "GET /v1/audit. Se descubren en runtime a partir de las entidades @Audited.")
    public ResponseEntity<List<String>> findEntityTypes() {
        return ResponseEntity.ok(auditRegistryService.findEntityTypes());
    }
}
