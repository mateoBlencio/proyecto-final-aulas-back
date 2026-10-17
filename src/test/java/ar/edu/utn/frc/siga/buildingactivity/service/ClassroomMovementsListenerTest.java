package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements;
import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomMovementsListener")
class ClassroomMovementsListenerTest {

    private static final String ACTOR = "sub@utn.edu.ar";
    private static final Long CENTRAL = 1L;
    private static final Long ANNEX = 2L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Mock
    private BuildingActivityRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private NotificationSender notificationSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private ClassroomMovementsListener listener;

    @BeforeEach
    void setUp() {
        listener = new ClassroomMovementsListener(repository, userService, classroomService, academicEventService,
                notificationSender, objectMapper);
    }

    @Test
    @DisplayName("actor sin rol SUBSECRETARIA: no guarda, no manda mail y no consulta otros modulos")
    void ignoresActorWithoutSubsecretariaRole() {
        when(repository.existsByOperationId("op-1")).thenReturn(false);
        when(userService.hasRole(ACTOR, SystemRole.SUBSECRETARIA)).thenReturn(false);

        listener.on(event("op-1", change(10L, 5L, 6L)));

        verify(repository, never()).save(any());
        verifyNoInteractions(notificationSender, classroomService, academicEventService);
    }

    @Test
    @DisplayName("operationId ya registrado (reentrega): no hace nada ni consulta el rol")
    void ignoresRedeliveredOperation() {
        when(repository.existsByOperationId("op-1")).thenReturn(true);

        listener.on(event("op-1", change(10L, 5L, 6L)));

        verify(repository, never()).save(any());
        verifyNoInteractions(userService, notificationSender, classroomService, academicEventService);
    }

    @Test
    @DisplayName("cambio entre dos edificios: guarda una actividad por edificio, cada una con la linea")
    void movementBetweenBuildingsSavesOneActivityPerBuilding() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(7L, 201, ANNEX, "Anexo"));
        when(userService.findByRoleForBuilding(any(), any())).thenReturn(List.of());

        listener.on(event("op-1", change(10L, 5L, 7L)));

        List<BuildingActivity> saved = savedActivities(2);
        assertThat(saved).extracting(BuildingActivity::getBuildingId).containsExactlyInAnyOrder(CENTRAL, ANNEX);
        assertThat(saved).extracting(BuildingActivity::getBuildingName).containsExactlyInAnyOrder("Central", "Anexo");
        assertThat(saved).allSatisfy(activity -> {
            assertThat(activity.getOperationId()).isEqualTo("op-1");
            assertThat(activity.getActor()).isEqualTo(ACTOR);
            assertThat(activity.getSource()).isEqualTo(AllocationSource.MANUAL);
            assertThat(activity.getReason()).isEqualTo("obra");
            List<ActivityLine> lines = linesOf(activity);
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst().occurrenceId()).isEqualTo(10L);
            assertThat(lines.getFirst().date()).isEqualTo(DAY);
            assertThat(lines.getFirst().startTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(lines.getFirst().fromClassroom().roomNumber()).isEqualTo(101);
            assertThat(lines.getFirst().toClassroom().roomNumber()).isEqualTo(201);
        });
    }

    @Test
    @DisplayName("cambio dentro del mismo edificio: una sola actividad con una sola linea (no se duplica)")
    void movementInsideOneBuildingSavesOneLine() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(6L, 102, CENTRAL, "Central"));
        when(userService.findByRoleForBuilding(any(), any())).thenReturn(List.of());

        listener.on(event("op-1", change(10L, 5L, 6L)));

        List<BuildingActivity> saved = savedActivities(1);
        assertThat(linesOf(saved.getFirst())).hasSize(1);
    }

    @Test
    @DisplayName("alta y liberacion: la linea queda en el edificio del aula que existe, con el otro extremo nulo")
    void assignmentAndReleaseKeepNullEnd() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(7L, 201, ANNEX, "Anexo"));
        when(userService.findByRoleForBuilding(any(), any())).thenReturn(List.of());

        listener.on(event("op-1", change(10L, null, 5L), change(11L, 7L, null)));

        List<BuildingActivity> saved = savedActivities(2);
        BuildingActivity central = byBuilding(saved, CENTRAL);
        BuildingActivity annex = byBuilding(saved, ANNEX);
        assertThat(linesOf(central)).singleElement().satisfies(line -> {
            assertThat(line.fromClassroom()).isNull();
            assertThat(line.toClassroom().id()).isEqualTo(5L);
        });
        assertThat(linesOf(annex)).singleElement().satisfies(line -> {
            assertThat(line.fromClassroom().id()).isEqualTo(7L);
            assertThat(line.toClassroom()).isNull();
        });
    }

    @Test
    @DisplayName("30 cambios en un edificio: un solo send con 20 lineas y restantes=10; se guardan las 30")
    @SuppressWarnings("unchecked")
    void thirtyChangesSendOneMailWithTwentyLines() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(6L, 102, CENTRAL, "Central"));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, CENTRAL))
                .thenReturn(List.of(user("ana@utn.edu.ar", "Ana", "Perez"), user("luis@utn.edu.ar", "Luis", "Gomez")));
        Change[] changes = IntStream.rangeClosed(1, 30).mapToObj(i -> change((long) i, 5L, 6L)).toArray(Change[]::new);

        listener.on(event("op-1", changes));

        assertThat(linesOf(savedActivities(1).getFirst())).hasSize(30);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, times(1)).send(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.template()).isEqualTo(NotificationTemplate.BUILDING_ACTIVITY);
        assertThat(request.idempotencyKey()).isEqualTo("op-1-" + CENTRAL);
        assertThat(request.recipients()).containsExactly(
                new NotificationRecipient("Ana Perez", "ana@utn.edu.ar"),
                new NotificationRecipient("Luis Gomez", "luis@utn.edu.ar"));
        assertThat((List<String>) request.model().get("lineas")).hasSize(20);
        assertThat(request.model().get("restantes")).isEqualTo(10);
        assertThat(request.model().get("edificio")).isEqualTo("Central");
        assertThat(request.model().get("motivo")).isEqualTo("obra");
        assertThat((String) request.model().get("resumen")).contains("30 movimientos");
    }

    @Test
    @DisplayName("hasta 20 cambios: restantes=0 y se listan todos")
    @SuppressWarnings("unchecked")
    void twentyChangesLeaveNoRemaining() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(6L, 102, CENTRAL, "Central"));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, CENTRAL))
                .thenReturn(List.of(user("ana@utn.edu.ar", "Ana", "Perez")));
        Change[] changes = IntStream.rangeClosed(1, 20).mapToObj(i -> change((long) i, 5L, 6L)).toArray(Change[]::new);

        listener.on(event("op-1", changes));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        assertThat((List<String>) captor.getValue().model().get("lineas")).hasSize(20);
        assertThat(captor.getValue().model().get("restantes")).isEqualTo(0);
    }

    @Test
    @DisplayName("edificio sin auxiliares: guarda la actividad y no manda mail")
    void buildingWithoutAuxiliariesSavesButDoesNotNotify() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(6L, 102, CENTRAL, "Central"));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, CENTRAL)).thenReturn(List.of());

        listener.on(event("op-1", change(10L, 5L, 6L)));

        savedActivities(1);
        verify(notificationSender, never()).send(any());
    }

    @Test
    @DisplayName("etiquetas: evento unico usa 'materia - tipo', recurrente 'Clase', y sin materia solo el tipo")
    void labelsDependOnEventKindAndSubject() {
        subsecretariaActs();
        stubClassrooms(classroom(5L, 101, CENTRAL, "Central"), classroom(6L, 102, CENTRAL, "Central"));
        when(userService.findByRoleForBuilding(any(), any())).thenReturn(List.of());
        SubjectResponseDto subject = new SubjectResponseDto(1L, 5, "Fisica I", null, null, true, null);
        when(academicEventService.findByIds(anyCollection())).thenReturn(List.of(
                new UniqueEventResponseDto(1L, EventType.UNIQUE_EVENT, UniqueEventKind.PARCIAL, 30, LocalTime.of(8, 0),
                        90, DAY, null, subject, null, false, false, null, null, null),
                new RecurringEventResponseDto(2L, EventType.RECURRING, 30, LocalTime.of(8, 0), 90,
                        DayOfWeek.TUESDAY, DAY, DAY.plusWeeks(10), null, null)));

        listener.on(event("op-1", changeOfEvent(10L, 1L), changeOfEvent(11L, 2L)));

        assertThat(linesOf(savedActivities(1).getFirst())).extracting(ActivityLine::eventLabel)
                .containsExactly("Fisica I · Parcial", "Clase");
    }

    private void subsecretariaActs() {
        when(repository.existsByOperationId(any())).thenReturn(false);
        when(userService.hasRole(ACTOR, SystemRole.SUBSECRETARIA)).thenReturn(true);
    }

    private void stubClassrooms(ClassroomResponseDto... classrooms) {
        when(classroomService.findByIdsIncludingDeactivated(anyCollection())).thenReturn(List.of(classrooms));
    }

    private List<BuildingActivity> savedActivities(int expected) {
        ArgumentCaptor<BuildingActivity> captor = ArgumentCaptor.forClass(BuildingActivity.class);
        verify(repository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    private BuildingActivity byBuilding(List<BuildingActivity> activities, Long buildingId) {
        return activities.stream().filter(a -> a.getBuildingId().equals(buildingId)).findFirst().orElseThrow();
    }

    private List<ActivityLine> linesOf(BuildingActivity activity) {
        return objectMapper.readValue(activity.getLines(), new TypeReference<List<ActivityLine>>() {});
    }

    private ClassroomMovements event(String operationId, Change... changes) {
        return new ClassroomMovements(operationId, ACTOR, AllocationSource.MANUAL, "obra",
                Instant.parse("2026-10-17T12:00:00Z"), List.of(changes));
    }

    private Change change(Long occurrenceId, Long from, Long to) {
        return new Change(occurrenceId, 1L, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30), from, to);
    }

    private Change changeOfEvent(Long occurrenceId, Long eventId) {
        return new Change(occurrenceId, eventId, DAY, LocalTime.of(8, 0), LocalTime.of(9, 30), 5L, 6L);
    }

    private ClassroomResponseDto classroom(Long id, int roomNumber, Long buildingId, String buildingName) {
        return new ClassroomResponseDto(id, roomNumber, 40, buildingId, buildingName, 1L, "Normal", null, true);
    }

    private UserResponseDto user(String email, String firstName, String lastName) {
        return new UserResponseDto(1L, email, firstName, lastName, List.of());
    }
}
