package ar.edu.utn.frc.siga.realtime.controller;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.realtime.LiveTopicAudience;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.realtime.internal.LiveSubscriptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Tiempo real")
@RestController
@RequestMapping("/v1/live")
@RequiredArgsConstructor
class LiveController {

    private final LiveSubscriptions subscriptions;
    private final List<LiveTopicAudience> audiences;

    @Operation(summary = "Avisos de cambios en tiempo real (Server-Sent Events)",
            description = "Manda un evento `ready` al conectar y un evento `change` con el tema (`ALLOCATIONS`, "
                    + "`ROOM_REQUESTS` o `BUILDING_ACTIVITY`) cada vez que cambian datos que el usuario puede ver. "
                    + "El back cierra la conexión cada 15 minutos; el cliente reconecta con su token vigente.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(HttpServletResponse response) {
        Map<LiveTopic, BuildingScope> scopes = new EnumMap<>(LiveTopic.class);
        audiences.forEach(audience -> scopes.put(audience.topic(), audience.currentScope()));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return subscriptions.open(scopes).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Se alcanzó el tope de conexiones de avisos"));
    }
}
