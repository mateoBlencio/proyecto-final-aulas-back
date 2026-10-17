package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.ClassroomRef;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registra los movimientos de aula por edificio y avisa por mail a los auxiliares. Solo cuenta lo que hizo
 * Subsecretaría: lo de un auxiliar no se avisa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ClassroomMovementsListener {

    private static final int MAIL_MAX_LINES = 20;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BuildingActivityRepository repository;
    private final UserService userService;
    private final ClassroomService classroomService;
    private final AcademicEventService academicEventService;
    private final NotificationSender notificationSender;
    private final ObjectMapper objectMapper;

    @ApplicationModuleListener
    void on(ClassroomMovements event) {
        if (repository.existsByOperationId(event.operationId())) return;
        if (!userService.hasRole(event.actor(), SystemRole.SUBSECRETARIA)) return;

        Map<Long, ClassroomRef> classrooms = classroomsOf(event.changes());
        Map<Long, String> labels = labelsOf(event.changes());

        Map<Long, List<ActivityLine>> linesByBuilding = new LinkedHashMap<>();
        Map<Long, String> buildingNames = new HashMap<>();
        for (Change change : event.changes()) {
            ClassroomRef from = classrooms.get(change.fromClassroomId());
            ClassroomRef to = classrooms.get(change.toClassroomId());
            ActivityLine line = new ActivityLine(change.occurrenceId(), labels.get(change.eventId()), change.date(),
                    change.startTime(), change.endTime(), from, to);
            Set<Long> buildingIds = new HashSet<>();
            for (ClassroomRef ref : new ClassroomRef[] {from, to}) {
                if (ref != null && buildingIds.add(ref.buildingId())) {
                    buildingNames.put(ref.buildingId(), ref.buildingName());
                    linesByBuilding.computeIfAbsent(ref.buildingId(), id -> new ArrayList<>()).add(line);
                }
            }
        }

        linesByBuilding.forEach((buildingId, lines) ->
                register(event, buildingId, buildingNames.get(buildingId), lines));
    }

    private void register(ClassroomMovements event, Long buildingId, String buildingName, List<ActivityLine> lines) {
        repository.save(BuildingActivity.builder()
                .buildingId(buildingId)
                .buildingName(buildingName)
                .operationId(event.operationId())
                .actor(event.actor())
                .source(event.source())
                .reason(event.reason())
                .occurredAt(event.occurredAt())
                .lines(objectMapper.writeValueAsString(lines))
                .build());

        List<NotificationRecipient> recipients = userService
                .findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, buildingId).stream()
                .map(ClassroomMovementsListener::toRecipient)
                .toList();
        if (recipients.isEmpty()) return;

        List<String> shown = lines.stream().limit(MAIL_MAX_LINES).map(ClassroomMovementsListener::describe).toList();
        Map<String, Object> model = new HashMap<>();
        model.put("edificio", buildingName);
        model.put("resumen", "Subsecretaría registró " + lines.size()
                + (lines.size() == 1 ? " movimiento" : " movimientos") + " de aula en " + buildingName + ".");
        model.put("motivo", event.reason());
        model.put("lineas", shown);
        model.put("restantes", lines.size() - shown.size());
        notificationSender.send(new NotificationRequest(NotificationTemplate.BUILDING_ACTIVITY, recipients, model,
                event.operationId() + "-" + buildingId));
    }

    private Map<Long, ClassroomRef> classroomsOf(List<Change> changes) {
        Set<Long> ids = new HashSet<>();
        for (Change change : changes) {
            if (change.fromClassroomId() != null) ids.add(change.fromClassroomId());
            if (change.toClassroomId() != null) ids.add(change.toClassroomId());
        }
        Map<Long, ClassroomRef> refs = new HashMap<>();
        for (ClassroomResponseDto classroom : classroomService.findByIdsIncludingDeactivated(ids)) {
            refs.put(classroom.id(), new ClassroomRef(classroom.id(), classroom.roomNumber(),
                    classroom.buildingId(), classroom.buildingName()));
        }
        return refs;
    }

    private Map<Long, String> labelsOf(List<Change> changes) {
        Set<Long> eventIds = new HashSet<>();
        changes.forEach(change -> eventIds.add(change.eventId()));
        Map<Long, String> labels = new HashMap<>();
        for (AcademicEventResponseDto academicEvent : academicEventService.findByIds(eventIds)) {
            labels.put(academicEvent.id(), labelOf(academicEvent));
        }
        return labels;
    }

    private static String labelOf(AcademicEventResponseDto event) {
        String type = event instanceof UniqueEventResponseDto unique ? switch (unique.eventType()) {
            case PARCIAL -> "Parcial";
            case TRABAJO_PRACTICO -> "Trabajo práctico";
            case EXAMEN_FINAL -> "Examen final";
            case CONFERENCIA -> "Conferencia";
            case OTRO -> "Evento";
        } : "Clase";
        SubjectResponseDto subject = event.subject();
        return subject == null ? type : subject.name() + " · " + type;
    }

    private static NotificationRecipient toRecipient(UserResponseDto user) {
        return new NotificationRecipient(user.firstName() + " " + user.lastName(), user.email());
    }

    private static String describe(ActivityLine line) {
        String when = line.date().format(DATE) + " " + line.startTime().format(TIME) + "-" + line.endTime().format(TIME);
        String movement;
        if (line.fromClassroom() == null) {
            movement = "se asignó " + room(line.toClassroom());
        } else if (line.toClassroom() == null) {
            movement = "se liberó " + room(line.fromClassroom());
        } else {
            movement = "de " + room(line.fromClassroom()) + " a " + room(line.toClassroom());
        }
        return Objects.toString(line.eventLabel(), "Clase") + ", " + when + ": " + movement;
    }

    private static String room(ClassroomRef ref) {
        return "aula " + ref.roomNumber() + " (" + ref.buildingName() + ")";
    }
}
