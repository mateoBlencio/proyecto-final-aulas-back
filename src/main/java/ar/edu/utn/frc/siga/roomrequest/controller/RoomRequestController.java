package ar.edu.utn.frc.siga.roomrequest.controller;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de solicitudes de aula.
 *
 * <p><b>Endpoint público:</b> lo consume un docente sin usuario en el sistema.
 * El {@code permitAll} está declarado en {@code SecurityConfig} y es acotado a
 * este POST; el resto de {@code /v1/**} sigue requiriendo autenticación.
 * La protección contra abuso la da el {@code RateLimitFilter} general, que
 * corre sobre todas las requests con o sin auth.
 */
@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/room-requests")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de aula",
     description = "Alta pública de solicitudes de aula por parte de docentes")
public class RoomRequestController {

    private final RoomRequestService roomRequestService;

    @PostMapping
    @Operation(summary = "Crear una solicitud de aula",
               description = "Registra una solicitud con sus pedidos y queda en estado PENDING "
                       + "para que subsecretaría la analice. Endpoint público, sin autenticación. "
                       + "Devuelve la solicitud creada, que es lo que alimenta la pantalla de confirmación.")
    public ResponseEntity<RoomRequestResponseDto> create(@Valid @RequestBody CreateRoomRequestDto dto) {
        log.debug("POST /v1/room-requests: teacherName={}, type={}, items={}", dto.teacherName(), dto.type(), dto.items().size());
        RoomRequestResponseDto response = roomRequestService.create(dto);
        log.info("Solicitud de aula creada vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
