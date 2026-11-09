package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.CommissionMessageMode;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties.MessageCommission;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final SysacadRuntimeSettings runtimeSettings;
    private final Environment environment;

    // No propaga errores: el aula ya quedó confirmada y un fallo del mensaje no es un fallo del envío.
    public void sendAfterConfirmation(SysacadSchedule schedule, Send decision, RequestPacer pacer) {
        if (decision.room() == RELEASED_ROOM || !enabledFor(schedule)) {
            return;
        }
        String note = null;
        CommissionMessageResult result;
        try {
            note = note(schedule.getSpecialty(), schedule.getPlan(), schedule.getSubject(), decision.date(),
                    decision.building(), decision.room());
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

    // EnviarMensajeComision no tiene entorno de pruebas y llega a alumnos reales: fuera de prod solo se avisa a las
    // comisiones de prueba listadas.
    private boolean enabledFor(SysacadSchedule schedule) {
        return switch (runtimeSettings.commissionMessageMode()) {
            case OFF -> false;
            case ON -> isProduction() || properties
                    .getCommissionMessageTestCommissions().stream().anyMatch(c -> c.matches(
                            schedule.getSpecialty(), schedule.getPlan(), schedule.getSubject(), schedule.getCommission()));
        };
    }

    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfLimitedToTestCommissions() {
        if (runtimeSettings.commissionMessageMode() == CommissionMessageMode.ON && !isProduction()) {
            log.warn("Mensaje de autogestión en ON fuera del perfil prod: solo llega a las comisiones de "
                    + "commission-message-test-commissions");
        }
    }

    private void record(SysacadSchedule schedule, Send decision, String note, CommissionMessageResult result) {
        try {
            stateWriter.recordCommissionMessage(schedule.getId(), decision.occurrenceId(), decision.building(),
                    decision.room(), note, result.delivered(), result.response());
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el mensaje a la comisión del horario {}", schedule.getId(), e);
        }
    }

    // Prueba manual, descartable: el mismo texto y el mismo procedure que tras un envío confirmado, sin horario ni
    // registro, porque la comisión de prueba no tiene horario en SysAcad. Ignora el modo y solo acepta las
    // comisiones de prueba listadas.
    public TestMessageResult sendTestMessage(MessageCommission target, int year, LocalDate classDate, int building,
            int room) {
        boolean listed = properties.getCommissionMessageTestCommissions().stream().anyMatch(c -> c.matches(
                target.specialty(), target.plan(), target.subject(), target.commission()));
        if (!listed) {
            throw new InvalidSelectionException("La comisión no está en commission-message-test-commissions");
        }
        String note = note(target.specialty(), target.plan(), target.subject(), classDate, building, room);
        CommissionMessageResult result = client.sendCommissionMessage(new CommissionMessageCommand(classDate, year,
                target.specialty(), target.plan(), target.subject(), target.commission(), note));
        log.info("Mensaje de prueba a la comisión {}: entregado={}, respuesta={}", target, result.delivered(),
                result.response());
        return new TestMessageResult(note, result.delivered(), result.response());
    }

    public record TestMessageResult(String note, boolean delivered, String response) {}

    private String note(int specialty, int plan, int subjectCode, LocalDate date, int building, int room) {
        String subject = subjectService.findByCodeAndStudyPlan(subjectCode, plan, specialty).name();
        String tail = " del " + CLASS_DATE.format(date) + destination(building, room) + ".";
        int subjectMax = Math.max(0, NOTE_MAX - NOTE_PREFIX.length() - tail.length());
        return truncate(NOTE_PREFIX + truncate(subject, subjectMax) + tail, NOTE_MAX);
    }

    private String destination(int buildingCode, int room) {
        String building = classroomService.findByRoomNumberAndBuildingCode(room, buildingCode)
                .map(ClassroomResponseDto::buildingName)
                .map(name -> " de " + name)
                .orElseThrow(() -> new IllegalStateException("Sin edificio para el aula " + room
                        + " del edificio " + buildingCode + ": el número solo es ambiguo"));
        return " será en el aula " + room + building;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
