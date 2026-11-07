package ar.edu.utn.frc.siga.sysacad.controller;

import ar.edu.utn.frc.siga.sysacad.api.KeepSysacadRequestDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleLogEntryDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadScheduleReviewItemDto;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadReviewService;
import ar.edu.utn.frc.siga.sysacad.internal.write.SysacadScheduleQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/sysacad/schedules")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
@Tag(name = "Horarios de SysAcad", description = "Revisión de horarios, registro de envíos y resolución manual")
public class SysacadScheduleController {

    private final SysacadScheduleQuery query;
    private final SysacadReviewService reviewService;

    @GetMapping("/review")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_READ')")
    @Operation(summary = "Listar los horarios que necesitan atención",
               description = "Devuelve, paginados, los horarios en revisión (REVIEW), los que no se pueden enviar "
                       + "y están en la vista (NOT_SENDABLE) y los que esperan un pedido sin aceptar con la clase "
                       + "a menos de 24 horas (WAITING). Cada ítem trae el motivo, el aula conocida, el aula "
                       + "leída de SysAcad, el último envío confirmado y, en los WAITING, la clase y el ítem que "
                       + "frenan el envío. Orden: tipo y antigüedad; el sort del pedido se ignora.")
    public ResponseEntity<Page<SysacadScheduleReviewItemDto>> findReview(
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/sysacad/schedules/review");
        return ResponseEntity.ok(query.findReviewItems(pageable));
    }

    @GetMapping("/{id}/log")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_READ')")
    @Operation(summary = "Registro de un horario",
               description = "Devuelve, paginado y del más nuevo al más viejo, lo que el job y las personas "
                       + "hicieron con el horario: intenciones de envío, confirmaciones, fallos con la respuesta "
                       + "de SysAcad (recortada a 2000 caracteres), cambios directos y resoluciones.")
    public ResponseEntity<Page<SysacadScheduleLogEntryDto>> findLog(@PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /v1/sysacad/schedules/{}/log", id);
        return ResponseEntity.ok(query.findLog(id, pageable));
    }

    @PostMapping("/{id}/review/resend-siga")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_SYNC')")
    @Operation(summary = "Reenviar lo de SIGA",
               description = "Toma la última lectura de SysAcad como valor conocido, limpia la diferencia, los "
                       + "intentos y el error, y devuelve el horario a la cola: el job recalcula y envía el aula "
                       + "de SIGA en la corrida siguiente. Responde 409 si el horario no está en revisión o si "
                       + "corre el job o el sync.")
    public ResponseEntity<SysacadScheduleDto> resendSiga(@PathVariable Long id, Authentication authentication) {
        log.info("POST /v1/sysacad/schedules/{}/review/resend-siga", id);
        return ResponseEntity.ok(reviewService.resendSiga(id, authentication.getName()));
    }

    @PostMapping("/{id}/review/keep-sysacad")
    @PreAuthorize("hasAuthority('PERM_SYSACAD_SYNC')")
    @Operation(summary = "Quedarse con lo de SysAcad",
               description = "Aplica en SIGA el aula que muestra SysAcad en las clases futuras del horario, con "
                       + "la regla manual de solapamiento y la observación obligatoria. Pisa los pedidos de esas "
                       + "clases y deja el motivo en la operación. Si SysAcad muestra el aula 999 o 0, las clases "
                       + "futuras quedan sin aula, sin liberarlas. Las clases con el aula liberada no se tocan. "
                       + "Responde 409 si el horario no está en revisión, si SIGA no tiene el aula o si corre el "
                       + "job o el sync.")
    public ResponseEntity<SysacadScheduleDto> keepSysacad(@PathVariable Long id,
            @Valid @RequestBody KeepSysacadRequestDto request, Authentication authentication) {
        log.info("POST /v1/sysacad/schedules/{}/review/keep-sysacad", id);
        return ResponseEntity.ok(reviewService.keepSysacad(id, authentication.getName(), request.observation()));
    }
}
