package ar.edu.utn.frc.siga.buildingactivity.controller;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.dto.request.MarkSeenRequestDto;
import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import ar.edu.utn.frc.siga.buildingactivity.dto.response.UnseenCountDto;
import ar.edu.utn.frc.siga.buildingactivity.service.BuildingActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("${siga.api.base-path}/building-activity")
@RequiredArgsConstructor
@Tag(name = "Movimientos por edificio", description = "Cambios de aula de Subsecretaría agrupados por edificio")
@PreAuthorize("hasAuthority('PERM_BUILDING_ACTIVITY_READ')")
public class BuildingActivityController {

    private final BuildingActivityService service;

    @GetMapping
    @Operation(summary = "Listar movimientos",
               description = "Dentro de los edificios del usuario. from/to filtran por fecha del movimiento; source, seen "
                       + "(relativo al usuario) y actor (parcial, sin distinguir mayúsculas) se combinan con AND. "
                       + "'sort=campo,asc|desc' acepta occurredAt, buildingName, actor y lineCount (otro campo da 400); "
                       + "sin sort, occurredAt,desc. Desempate por id desc.")
    public ResponseEntity<Page<BuildingActivityDto>> find(
            Authentication authentication,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AllocationSource source,
            @RequestParam(required = false) Boolean seen,
            @RequestParam(required = false) String actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.find(authentication.getName(), buildingId, from, to, source, seen, actor,
                pageable));
    }

    @GetMapping("/unseen-count")
    @Operation(summary = "Contar movimientos no vistos",
               description = "Movimientos posteriores a lo último marcado como visto, sin contar los propios.")
    public ResponseEntity<UnseenCountDto> unseenCount(Authentication authentication) {
        return ResponseEntity.ok(new UnseenCountDto(service.countUnseen(authentication.getName())));
    }

    @PostMapping("/seen")
    @Operation(summary = "Marcar movimientos como vistos",
               description = "Marca todo hasta lastSeenId inclusive. Un id menor al ya marcado se ignora.")
    public ResponseEntity<Void> markSeen(Authentication authentication, @Valid @RequestBody MarkSeenRequestDto request) {
        service.markSeen(authentication.getName(), request.lastSeenId());
        return ResponseEntity.noContent().build();
    }
}
