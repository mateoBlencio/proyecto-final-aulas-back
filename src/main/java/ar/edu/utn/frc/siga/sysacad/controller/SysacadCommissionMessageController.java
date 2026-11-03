package ar.edu.utn.frc.siga.sysacad.controller;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadCommissionMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/sysacad/commission-messages")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
@Tag(name = "Mensajes de autogestión", description = "Notificación a una comisión vía EnviarMensajeComision de SysAcad")
public class SysacadCommissionMessageController {

    private final SysacadCommissionMessageService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SYSACAD_SYNC')")
    @Operation(summary = "Enviar un mensaje de autogestión a una comisión",
               description = "Ejecuta EnviarMensajeComision, que escribe en SysAcad productivo. Solo existe con "
                       + "siga.sysacad.autogestion.enabled=true y rechaza con 403 cualquier especialidad, plan, "
                       + "materia o comisión distinta de la configurada en siga.sysacad.autogestion. "
                       + "Responde 502 si SysAcad no acepta el mensaje.")
    public ResponseEntity<Void> send(@Valid @RequestBody SendCommissionMessageRequest request) {
        log.info("POST /v1/sysacad/commission-messages");
        service.send(request);
        return ResponseEntity.noContent().build();
    }
}
