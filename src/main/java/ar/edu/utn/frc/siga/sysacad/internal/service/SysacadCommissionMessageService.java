package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageFailedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"siga.sysacad.enabled", "siga.sysacad.autogestion.enabled"}, havingValue = "true")
public class SysacadCommissionMessageService {

    private static final String PROCEDURE = "EnviarMensajeComision";

    private final SysacadClient client;
    private final CommissionViewFetcher commissionViewFetcher;
    private final SysacadProperties properties;

    public void send(SendCommissionMessageRequest request) {
        RawCommission target = resolveTarget(request);
        try {
            client.executeProcedure(PROCEDURE, Map.of(
                    "@LegajoPublica", request.publicLegajo(),
                    "@IdTipoUsuarioPublica", request.publicUserType(),
                    "@FechaHasta", request.untilDate().toString(),
                    "@AnioAcademico", request.academicYear(),
                    "@Especialidad", target.especialid(),
                    "@Plan", target.plan(),
                    "@Materia", target.materia(),
                    "@Comision", target.comision(),
                    "@Nota", request.note()));
        } catch (RestClientResponseException e) {
            log.error("SysAcad respondió {} a {}", e.getStatusCode().value(), PROCEDURE);
            throw new CommissionMessageFailedException(e);
        }
        log.info("Mensaje enviado a la comisión {} de la especialidad {}", request.courseCode(), request.specialty());
    }

    private RawCommission resolveTarget(SendCommissionMessageRequest request) {
        SysacadProperties.Autogestion allowed = properties.getAutogestion();
        if (!sameCourseCode(allowed.courseCode(), request.courseCode())
                || !allowed.specialty().equals(request.specialty())) {
            log.warn("Envío rechazado: curso={} especialidad={} fuera de lo habilitado",
                    request.courseCode(), request.specialty());
            throw new CommissionMessageNotAllowedException();
        }
        List<RawCommission> matches = commissionViewFetcher.fetch().stream()
                .filter(row -> sameCourseCode(allowed.courseCode(), row.curso())
                        && allowed.specialty().equals(row.especialid())
                        && request.academicYear().equals(row.anoacademi()))
                .toList();
        if (matches.size() != 1) {
            log.warn("Envío rechazado: {} comisiones de SysAcad coinciden con curso={} especialidad={} año={}",
                    matches.size(), request.courseCode(), request.specialty(), request.academicYear());
            throw new CommissionMessageNotAllowedException();
        }
        return matches.get(0);
    }

    private static boolean sameCourseCode(String expected, String actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
