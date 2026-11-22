package ar.edu.utn.frc.siga.cameracount.controller;

import ar.edu.utn.frc.siga.cameracount.dto.request.CameraCountRequestDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/camera-counts")
@RequiredArgsConstructor
@Tag(name = "Conteo por cámaras", description = "Conteo de personas a partir del stream RTSP de una cámara")
public class CameraCountController {

    private final CameraCountService cameraCountService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CAMERA_COUNT_RUN')")
    @Operation(summary = "Contar personas con una cámara",
               description = "Pide al servicio de conteo que tome samples fotogramas (1 a 10; sin samples usa el "
                       + "default del servicio, 5) del stream rtspUrl y devuelve la mediana redondeada. Tarda unos "
                       + "25 s y hasta 90 s en el peor caso. status NO_SIGNAL (people null) significa que la cámara "
                       + "no dio fotogramas suficientes. Responde 422 si el servicio rechaza la URL, 503 si el "
                       + "servicio está caído, saturado (con Retry-After) o deshabilitado en el entorno, y 502 ante "
                       + "otro error del servicio. La URL nunca se devuelve ni se loguea en claro.")
    public ResponseEntity<CameraCountResponseDto> count(@Valid @RequestBody CameraCountRequestDto dto) {
        log.debug("POST /v1/camera-counts: samples={}", dto.samples());
        return ResponseEntity.ok(cameraCountService.count(dto.rtspUrl(), dto.samples()));
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('PERM_CAMERA_COUNT_RUN')")
    @Operation(summary = "Estado del servicio de conteo",
               description = "ready=false mientras el servicio carga el modelo (hasta 2 minutos después de un "
                       + "reinicio). Responde 503 si el servicio no responde o está deshabilitado en el entorno.")
    public ResponseEntity<CameraCountHealthResponseDto> health() {
        log.debug("GET /v1/camera-counts/health");
        return ResponseEntity.ok(cameraCountService.health());
    }
}
