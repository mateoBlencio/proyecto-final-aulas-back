package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/buildings")
@RequiredArgsConstructor
@Tag(name = "Edificios", description = "Consulta de edificios disponibles")
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    @Operation(summary = "Listar edificios activos",
               description = "Devuelve todos los edificios activos disponibles para filtrar asignaciones.")
    public ResponseEntity<List<BuildingResponseDto>> findAll() {
        log.debug("GET /v1/buildings");
        List<BuildingResponseDto> buildings = buildingService.findAll();
        log.info("Buildings listed: count={}", buildings.size());
        return ResponseEntity.ok(buildings);
    }
}
