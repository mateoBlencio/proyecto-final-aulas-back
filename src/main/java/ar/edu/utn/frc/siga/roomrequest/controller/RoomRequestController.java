package ar.edu.utn.frc.siga.roomrequest.controller;

import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemStatusCountDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/room-requests")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de aula",
     description = "Alta pública de solicitudes de aula por docentes y bandeja de pedidos "
             + "para subsecretaría")
public class RoomRequestController {

    private final RoomRequestService roomRequestService;

    @PostMapping
    @Operation(summary = "Crear una solicitud de aula",
               description = "Registra una solicitud con sus pedidos y queda en estado PENDING "
                       + "para que subsecretaría la analice. Endpoint público")
    public ResponseEntity<RoomRequestResponseDto> create(@Valid @RequestBody CreateRoomRequestDto dto) {
        log.debug("POST /v1/room-requests: teacherName={}, type={}, items={}",
                dto.requester().teacherName(), dto.type(), dto.items().size());
        RoomRequestResponseDto response = roomRequestService.create(dto);
        log.info("Solicitud de aula creada vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar pedidos de aula",
               description = "Listado paginado de pedidos. Filtra por tipo, "
                       + "estado, ámbito y materia; por defecto oculta los pedidos con fecha pasada "
                       + "salvo que se pida includePast=true.")
    public ResponseEntity<Page<RoomRequestItemRowDto>> findItems(
            @PageableDefault(size = 20, sort = {"date", "startTime"}, direction = Sort.Direction.ASC)  Pageable pageable,
            @RequestParam(required = false) Set<RoomRequestType> types,
            @RequestParam(required = false) Set<RoomRequestStatus> statuses,
            @RequestParam(required = false) AcademicScope scope,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "false") boolean includePast) {

        log.debug("GET /v1/room-requests/items: types={}, statuses={}, scope={}, subjectId={}, page={}",
                types, statuses, scope, subjectId, pageable.getPageNumber());
        RoomRequestItemFilter filter =
                RoomRequestItemFilter.of(types, statuses, scope, subjectId, dateFrom, dateTo, includePast);
        Page<RoomRequestItemRowDto> page = roomRequestService.findItems(filter, pageable);
        log.info("Pedidos de aula listados vía controller: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/items/status-counts")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Contar pedidos de aula por estado",
               description = "Total de pedidos en cada estado.")
    public ResponseEntity<List<RoomRequestItemStatusCountDto>> countItemsByStatus(
            @RequestParam(required = false, defaultValue = "false") boolean includePast) {

        log.debug("GET /v1/room-requests/items/status-counts: includePast={}", includePast);
        List<RoomRequestItemStatusCountDto> counts = roomRequestService.countItemsByStatus(includePast);
        log.info("Pedidos de aula contados por estado vía controller: {}", counts);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Buscar un pedido por id",
               description = "Detalle completo de un pedido, con la cabecera de su solicitud "
                       + "(incluido el contacto del docente). 404 si no existe.")
    public ResponseEntity<RoomRequestItemDetailDto> findItemById(@PathVariable Long id) {
        log.debug("GET /v1/room-requests/items/{}", id);
        return ResponseEntity.ok(roomRequestService.findItemById(id));
    }
}
