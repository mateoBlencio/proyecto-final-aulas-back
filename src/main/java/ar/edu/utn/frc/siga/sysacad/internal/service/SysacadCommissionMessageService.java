package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageFailedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.CommissionMessageNotAllowedException;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
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
        log.info("Enviando {} a SysAcad: curso={} especialidad={} plan={} materia={} comisión={} año={} hasta={}",
                PROCEDURE, request.courseCode(), target.especialid(), target.plan(), target.materia(),
                target.comision(), request.academicYear(), request.untilDate());
        String response;
        try {
            response = client.executeProcedure(PROCEDURE, Map.of(
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
            log.error("SysAcad respondió {} a {}: {}", e.getStatusCode().value(), PROCEDURE,
                    e.getResponseBodyAsString());
            throw new CommissionMessageFailedException(e);
        } catch (SysacadUnavailableException e) {
            log.error("Sin conexión con SysAcad al ejecutar {}", PROCEDURE);
            throw new CommissionMessageFailedException(e);
        }
        log.info("SysAcad aceptó {}: {}", PROCEDURE, response);
    }

    public boolean isTarget(String courseCode, Integer specialty, Integer subject) {
        SysacadProperties.Autogestion allowed = properties.getAutogestion();
        return sameCourseCode(allowed.courseCode(), courseCode)
                && allowed.specialty().equals(specialty)
                && allowed.subject().equals(subject);
    }

    private RawCommission resolveTarget(SendCommissionMessageRequest request) {
        SysacadProperties.Autogestion allowed = properties.getAutogestion();
        if (!isTarget(request.courseCode(), request.specialty(), request.subject())) {
            log.warn("Envío rechazado: curso={} especialidad={} materia={} fuera de lo habilitado",
                    request.courseCode(), request.specialty(), request.subject());
            throw new CommissionMessageNotAllowedException();
        }
        List<RawCommission> matches = commissionViewFetcher.fetch().stream()
                .filter(row -> sameCourseCode(allowed.courseCode(), row.curso())
                        && allowed.specialty().equals(row.especialid())
                        && allowed.subject().equals(row.materia())
                        && request.academicYear().equals(row.anoacademi()))
                .toList();
        if (matches.size() != 1) {
            log.warn("Envío rechazado: {} comisiones de SysAcad coinciden con curso={} especialidad={} materia={} año={}",
                    matches.size(), request.courseCode(), request.specialty(), request.subject(),
                    request.academicYear());
            throw new CommissionMessageNotAllowedException();
        }
        return matches.get(0);
    }

    private static boolean sameCourseCode(String expected, String actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
