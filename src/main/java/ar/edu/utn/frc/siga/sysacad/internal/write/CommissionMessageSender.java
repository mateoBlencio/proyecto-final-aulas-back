package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class CommissionMessageSender {

    private static final int RELEASED_ROOM = 999;
    private static final int NOTE_MAX = 150;
    private static final String NOTE_PREFIX = "Cambio de aula: tu clase de ";
    private static final DateTimeFormatter CLASS_DATE =
            DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.forLanguageTag("es-AR"));

    private final SysacadWriteClient client;
    private final SysacadScheduleStateWriter stateWriter;
    private final SubjectService subjectService;
    private final ClassroomService classroomService;
    private final SysacadProperties properties;

    // No propaga errores: el aula ya quedó confirmada y un fallo del mensaje no es un fallo del envío.
    public void sendAfterConfirmation(SysacadSchedule schedule, Send decision, RequestPacer pacer) {
        if (!properties.isCommissionMessageEnabled() || decision.room() == RELEASED_ROOM) {
            return;
        }
        String note = null;
        CommissionMessageResult result;
        try {
            note = note(schedule, decision);
            pacer.acquire();
            result = client.sendCommissionMessage(new CommissionMessageCommand(
                    decision.date(), schedule.getYear(), schedule.getSpecialty(), schedule.getPlan(),
                    schedule.getSubject(), schedule.getCommission(), note));
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el mensaje a la comisión del horario {}", schedule.getId(), e);
            result = new CommissionMessageResult(false, e.toString());
        }
        record(schedule, decision, note, result);
    }

    private void record(SysacadSchedule schedule, Send decision, String note, CommissionMessageResult result) {
        try {
            stateWriter.recordCommissionMessage(schedule.getId(), decision.occurrenceId(), decision.building(),
                    decision.room(), note, result.delivered(), result.response());
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el mensaje a la comisión del horario {}", schedule.getId(), e);
        }
    }

    private String note(SysacadSchedule schedule, Send decision) {
        String subject = subjectService.findByCodeAndStudyPlan(schedule.getSubject(), schedule.getPlan(),
                schedule.getSpecialty()).name();
        String tail = " del " + CLASS_DATE.format(decision.date()) + destination(decision) + ".";
        int subjectMax = Math.max(0, NOTE_MAX - NOTE_PREFIX.length() - tail.length());
        return truncate(NOTE_PREFIX + truncate(subject, subjectMax) + tail, NOTE_MAX);
    }

    private String destination(Send decision) {
        String building = classroomService.findByRoomNumberAndBuildingCode(decision.room(), decision.building())
                .map(ClassroomResponseDto::buildingName)
                .map(name -> " de " + name)
                .orElseThrow(() -> new IllegalStateException("Sin edificio para el aula " + decision.room()
                        + " del edificio " + decision.building() + ": el número solo es ambiguo"));
        return " será en el aula " + decision.room() + building;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
