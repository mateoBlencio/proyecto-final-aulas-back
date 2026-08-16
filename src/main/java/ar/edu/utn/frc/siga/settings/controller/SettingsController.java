package ar.edu.utn.frc.siga.settings.controller;

import ar.edu.utn.frc.siga.settings.dto.request.BatchUpdateSettingsRequestDto;
import ar.edu.utn.frc.siga.settings.dto.request.UpdateSettingRequestDto;
import ar.edu.utn.frc.siga.settings.dto.response.SettingResponseDto;
import ar.edu.utn.frc.siga.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUBSECRETARIA')")
@Tag(name = "Configuración", description = "Administración de parámetros de negocio del sistema")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @Operation(summary = "Listar configuración agrupada por categoría",
               description = "Devuelve los settings parametrizables agrupados por categoría, con su metadata "
                       + "(tipo, riesgo, cotas, warning) y el valor actual.")
    public ResponseEntity<Map<String, List<SettingResponseDto>>> findAll() {
        log.debug("GET /v1/settings");
        return ResponseEntity.ok(settingsService.findAllGroupedByCategory());
    }

    @GetMapping("/{key}")
    @Operation(summary = "Obtener un setting por clave",
               description = "Devuelve la metadata y el valor actual de un setting.")
    public ResponseEntity<SettingResponseDto> findByKey(@PathVariable String key) {
        log.debug("GET /v1/settings/{}", key);
        return ResponseEntity.ok(settingsService.findByKey(key));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Actualizar un setting",
               description = "Valida el tipo y las cotas del valor, lo persiste (auditado con Envers) y refresca "
                       + "el cache. El valor tiene efecto inmediato, sin reiniciar el backend.")
    public ResponseEntity<SettingResponseDto> update(
            @PathVariable String key, @Valid @RequestBody UpdateSettingRequestDto dto) {
        log.debug("PUT /v1/settings/{}", key);
        SettingResponseDto response = settingsService.update(key, dto.value());
        log.info("Setting actualizado vía controller: clave={}", key);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(summary = "Actualizar varios settings de forma transaccional",
               description = "Valida y persiste todos los settings en una única transacción: si alguno falla, "
                       + "ninguno se aplica.")
    public ResponseEntity<List<SettingResponseDto>> updateBatch(
            @Valid @RequestBody BatchUpdateSettingsRequestDto dto) {
        log.debug("PUT /v1/settings (batch): count={}", dto.settings().size());
        List<SettingResponseDto> response = settingsService.updateBatch(dto.settings());
        log.info("Batch de settings actualizado vía controller: count={}", response.size());
        return ResponseEntity.ok(response);
    }
}
