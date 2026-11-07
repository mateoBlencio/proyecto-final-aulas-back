package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Mails a Subsecretaría: novedades de la corrida y caída prolongada de SysAcad. Nunca a auxiliares. */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadAlertService {

    private static final int MAIL_MAX_LINES = 30;
    private static final Locale ES = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<ScheduleLogType> NEWS_TYPES = List.of(ScheduleLogType.REVIEW_OPENED,
            ScheduleLogType.NO_ROOM_AFTER_CANCEL);

    private final SysacadScheduleLogRepository logRepository;
    private final SysacadScheduleRepository scheduleRepository;
    private final SysacadWriteStateService stateService;
    private final OccurrenceService occurrenceService;
    private final UserService userService;
    private final NotificationSender notificationSender;
    private final SysacadProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** {@code previousRunEnd} es el fin de la corrida anterior, leído antes de que esta corrida lo pise. */
    public void alert(Instant previousRunEnd) {
        if (properties.getWrite().mode() != SysacadWriteMode.ON) {
            return;
        }
        SysacadWriteState state = stateService.current();
        alertNews(previousRunEnd, state);
        alertOutage(state);
        alertHoursFreeze(state);
    }

    private void alertNews(Instant previousRunEnd, SysacadWriteState state) {
        Instant after = previousRunEnd != null ? previousRunEnd : state.getLastRunStartedAt();
        Instant until = state.getLastRunFinishedAt();
        if (after == null || until == null || !until.isAfter(after) || state.getLastRunStartedAt() == null) {
            return;
        }
        List<SysacadScheduleLogEntry> news = logRepository.findBetween(NEWS_TYPES, after, until);
        if (news.isEmpty()) {
            return;
        }
        List<NotificationRecipient> recipients = recipients();
        if (recipients.isEmpty()) {
            return;
        }
        Map<Long, SysacadSchedule> schedules = Maps.byId(scheduleRepository.findAllById(
                news.stream().map(SysacadScheduleLogEntry::getScheduleId).collect(Collectors.toSet())),
                SysacadSchedule::getId);
        Map<Long, OccurrenceSlotDto> slots = slotsOf(news);

        List<String> lines = news.stream().limit(MAIL_MAX_LINES)
                .map(entry -> describe(entry, schedules.get(entry.getScheduleId()), slots))
                .toList();
        String count = news.size() + (news.size() == 1 ? " horario necesita" : " horarios necesitan");
        Map<String, Object> model = new HashMap<>();
        model.put("asunto", "SysAcad: novedades en los envíos de aula");
        model.put("resumen", count + " atención de Subsecretaría tras la última corrida de envío a SysAcad.");
        model.put("lineas", lines);
        model.put("restantes", news.size() - lines.size());
        notificationSender.send(new NotificationRequest(NotificationTemplate.SYSACAD_ALERT, recipients, model,
                "sysacad-alert-" + until.toEpochMilli()));
    }

    private void alertOutage(SysacadWriteState state) {
        Instant now = clock.instant();
        SysacadProperties.Write config = properties.getWrite();
        Instant since = state.getOutageSince();
        Instant lastAlert = state.getLastOutageAlertAt();
        if (since == null || !now.isAfter(since.plus(config.outageAlertAfter()))
                || lastAlert != null && !now.isAfter(lastAlert.plus(config.outageAlertRepeat()))) {
            return;
        }
        List<NotificationRecipient> recipients = recipients();
        if (!recipients.isEmpty()) {
            Map<String, Object> model = new HashMap<>();
            model.put("asunto", "SysAcad: el envío de aulas no puede comunicarse");
            model.put("resumen", "El job de envío de aulas no logra comunicarse con SysAcad desde el "
                    + DATE.format(since.atZone(clock.getZone())) + ". Los cambios de aula quedan en SIGA hasta que "
                    + "vuelva la conexión.");
            model.put("lineas", List.of());
            model.put("restantes", 0);
            notificationSender.send(new NotificationRequest(NotificationTemplate.SYSACAD_ALERT, recipients, model,
                    "sysacad-outage-" + since.toEpochMilli() + "-" + (lastAlert == null ? 0 : lastAlert.toEpochMilli())));
        }
        stateService.recordOutageAlert(now);
    }

    private void alertHoursFreeze(SysacadWriteState state) {
        if (!scheduleRepository.existsByStatusAndReviewReason(SysacadScheduleStatus.REVIEW,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND)) {
            return;
        }
        Instant now = clock.instant();
        Instant lastAlert = state.getLastOutageAlertAt();
        if (lastAlert != null && !now.isAfter(lastAlert.plus(properties.getWrite().outageAlertRepeat()))) {
            return;
        }
        List<NotificationRecipient> recipients = recipients();
        if (!recipients.isEmpty()) {
            Map<String, Object> model = new HashMap<>();
            model.put("asunto", "SysAcad: el envío de aulas está frenado");
            model.put("resumen", "Un envío cambió la hora de un horario en SysAcad. El job no envía nada hasta que "
                    + "Subsecretaría resuelva ese horario en la lista de revisión.");
            model.put("lineas", List.of());
            model.put("restantes", 0);
            notificationSender.send(new NotificationRequest(NotificationTemplate.SYSACAD_ALERT, recipients, model,
                    "sysacad-hours-freeze-" + now.toEpochMilli()));
        }
        stateService.recordOutageAlert(now);
    }

    private List<NotificationRecipient> recipients() {
        Map<String, NotificationRecipient> byEmail = new LinkedHashMap<>();
        for (UserResponseDto user : userService.findByRole(SystemRole.SUBSECRETARIA)) {
            byEmail.putIfAbsent(user.email(), new NotificationRecipient(user.firstName() + " " + user.lastName(),
                    user.email()));
        }
        return List.copyOf(byEmail.values());
    }

    private Map<Long, OccurrenceSlotDto> slotsOf(List<SysacadScheduleLogEntry> news) {
        Set<Long> ids = new HashSet<>();
        for (SysacadScheduleLogEntry entry : news) {
            ids.addAll(occurrenceIdsOf(entry));
        }
        return ids.isEmpty() ? Map.of() : Maps.byId(occurrenceService.findSlots(ids), OccurrenceSlotDto::occurrenceId);
    }

    private List<Long> occurrenceIdsOf(SysacadScheduleLogEntry entry) {
        if (entry.getOccurrenceId() != null) {
            return List.of(entry.getOccurrenceId());
        }
        if (entry.getOccurrences() == null) {
            return List.of();
        }
        return List.of(objectMapper.readValue(entry.getOccurrences(), Long[].class));
    }

    private String describe(SysacadScheduleLogEntry entry, SysacadSchedule schedule,
            Map<Long, OccurrenceSlotDto> slots) {
        String subject = schedule == null ? "horario " + entry.getScheduleId()
                : "curso " + schedule.getCourseCode() + ", materia " + schedule.getSubject() + ", "
                        + DayOfWeek.of(schedule.getDayOfWeek()).getDisplayName(TextStyle.FULL, ES);
        String classes = occurrenceIdsOf(entry).stream()
                .map(slots::get)
                .filter(Objects::nonNull)
                .map(slot -> DATE.format(slot.date().atTime(slot.startTime())))
                .collect(Collectors.joining(", "));
        return subject + ": " + reasonText(entry) + (classes.isEmpty() ? "" : " (clase: " + classes + ")");
    }

    private static String reasonText(SysacadScheduleLogEntry entry) {
        if (entry.getType() == ScheduleLogType.NO_ROOM_AFTER_CANCEL) {
            return "se canceló el pedido y la próxima clase quedó sin aula";
        }
        return switch (SysacadReviewReason.valueOf(entry.getReason())) {
            case ROOM_OCCUPIED -> "el aula que puso SysAcad está ocupada en SIGA";
            case NO_ROOM_IN_SYSACAD -> "SysAcad dejó el horario sin aula";
            case ROOM_UNKNOWN_IN_SIGA -> "SIGA no tiene el aula que puso SysAcad";
            case REQUEST_ON_FUTURE_CLASS -> "hay un pedido de aula en una clase futura";
            case RELEASED_FUTURE_CLASS -> "hay una clase futura con el aula liberada";
            case HOUR_MISMATCH -> "la hora en SysAcad no coincide con la de SIGA";
            case HOURS_CHANGED_AFTER_SEND -> "el envío cambió la hora en SysAcad";
            case NOT_CONFIRMED -> "SysAcad no mostró el aula enviada";
            case MAX_ATTEMPTS -> "se agotaron los intentos de envío";
            case SCHEDULE_NOT_FOUND -> "SysAcad no encuentra el horario";
            case ROOM_NOT_IN_SYSACAD -> "SysAcad no tiene el aula de SIGA";
            case ROWS_DIVERGED -> "las filas del horario en SysAcad no coinciden entre sí";
        };
    }
}
