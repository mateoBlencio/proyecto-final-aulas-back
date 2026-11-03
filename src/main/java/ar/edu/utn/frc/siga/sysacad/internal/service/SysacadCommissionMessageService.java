package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageFailedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
public class SysacadCommissionMessageService {

    private static final String PROCEDURE = "EnviarMensajeComision";

    private final SysacadClient client;
    private final SysacadProperties properties;

    public void send(SendCommissionMessageRequest request) {
        SysacadProperties.Autogestion target = properties.getAutogestion();
        if (!isTarget(target, request)) {
            log.warn("Envío rechazado: especialidad={} plan={} materia={} comisión={} fuera de lo habilitado",
                    request.specialty(), request.plan(), request.subject(), request.commission());
            throw new CommissionMessageNotAllowedException();
        }
        try {
            client.executeProcedure(PROCEDURE, Map.of(
                    "@LegajoPublica", request.publicLegajo(),
                    "@IdTipoUsuarioPublica", request.publicUserType(),
                    "@FechaHasta", request.untilDate().toString(),
                    "@AnioAcademico", request.academicYear(),
                    "@Especialidad", request.specialty(),
                    "@Plan", request.plan(),
                    "@Materia", request.subject(),
                    "@Comision", request.commission(),
                    "@Nota", request.note()));
        } catch (RestClientResponseException e) {
            log.error("SysAcad respondió {} a {}", e.getStatusCode().value(), PROCEDURE);
            throw new CommissionMessageFailedException(e);
        }
        log.info("Mensaje enviado a comisión {} de materia {}", request.commission(), request.subject());
    }

    private static boolean isTarget(SysacadProperties.Autogestion target, SendCommissionMessageRequest request) {
        return target.specialty().equals(request.specialty())
                && target.plan().equals(request.plan())
                && target.subject().equals(request.subject())
                && target.commission().equals(request.commission());
    }
}
