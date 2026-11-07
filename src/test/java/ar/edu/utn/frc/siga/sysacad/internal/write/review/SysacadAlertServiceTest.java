package ar.edu.utn.frc.siga.sysacad.internal.write.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadWriteStateService;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadAlertService")
class SysacadAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant RUN_START = NOW.minusSeconds(120);
    private static final Instant RUN_END = NOW.minusSeconds(60);
    private static final Instant PREVIOUS_END = NOW.minus(Duration.ofMinutes(10));

    @Mock
    private SysacadScheduleLogRepository logRepository;
    @Mock
    private SysacadScheduleRepository scheduleRepository;
    @Mock
    private SysacadWriteStateService stateService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationSender notificationSender;

    private SysacadWriteState state;
    private SysacadAlertService service;

    @BeforeEach
    void setUp() {
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.ON));
        state = new SysacadWriteState();
        service = new SysacadAlertService(logRepository, scheduleRepository, stateService, occurrenceService,
                userService, notificationSender, properties, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(stateService.current()).thenReturn(state);
    }

    private void finishedRun() {
        state.setLastRunStartedAt(RUN_START);
        state.setLastRunFinishedAt(RUN_END);
    }

    private static UserResponseDto user(String email) {
        return new UserResponseDto(1L, email, "Sub", "Secretaria", List.of());
    }

    private void subsecretaria(String... emails) {
        when(userService.findByRole(SystemRole.SUBSECRETARIA))
                .thenReturn(java.util.Arrays.stream(emails).map(SysacadAlertServiceTest::user).toList());
    }

    private static SysacadScheduleLogEntry reviewEntry(long scheduleId) {
        return SysacadScheduleLogEntry.builder().scheduleId(scheduleId).type(ScheduleLogType.REVIEW_OPENED)
                .reason(SysacadReviewReason.ROOM_OCCUPIED.name()).occurredAt(RUN_END).build();
    }

    private void newsInWindow(SysacadScheduleLogEntry... entries) {
        when(logRepository.findBetween(any(), eq(PREVIOUS_END), eq(RUN_END))).thenReturn(List.of(entries));
        SysacadSchedule schedule = SysacadSchedule.builder().id(1L).courseCode("1K1").subject(115).dayOfWeek(1)
                .build();
        when(scheduleRepository.findAllById(any())).thenReturn(List.of(schedule));
    }

    @Test
    @DisplayName("novedades de la ventana: un mail SYSACAD_ALERT solo a Subsecretaría, con los datos del horario")
    @SuppressWarnings("unchecked")
    void newsMailGoesOnlyToSubsecretaria() {
        finishedRun();
        newsInWindow(reviewEntry(1L));
        subsecretaria("sub@utn.edu.ar");

        service.alert(PREVIOUS_END);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.template()).isEqualTo(NotificationTemplate.SYSACAD_ALERT);
        assertThat(request.recipients()).extracting(r -> r.email()).containsExactly("sub@utn.edu.ar");
        assertThat((List<String>) request.model().get("lineas")).singleElement().asString()
                .contains("1K1").contains("115").contains("ocupada");
        verify(userService, never()).findByRoleForBuilding(any(), any());
        verify(userService, never()).findByRole(SystemRole.AUXILIAR_AULICO);
    }

    @Test
    @DisplayName("sin novedades en la ventana: no manda mail")
    void noNewsNoMail() {
        finishedRun();
        when(logRepository.findBetween(any(), eq(PREVIOUS_END), eq(RUN_END))).thenReturn(List.of());

        service.alert(PREVIOUS_END);

        verify(notificationSender, never()).send(any());
    }

    @Test
    @DisplayName("la ventana arranca en el fin de la corrida anterior: la misma corrida avisada dos veces no duplica la clave")
    void idempotencyKeyIsStablePerRun() {
        finishedRun();
        newsInWindow(reviewEntry(1L));
        subsecretaria("sub@utn.edu.ar");

        service.alert(PREVIOUS_END);
        service.alert(PREVIOUS_END);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationRequest::idempotencyKey).containsOnly(
                "sysacad-alert-" + RUN_END.toEpochMilli());
    }

    @Test
    @DisplayName("corrida que no terminó después de la anterior: no consulta novedades")
    void runNotFinishedAfterPreviousSkipsNews() {
        state.setLastRunStartedAt(RUN_START);
        state.setLastRunFinishedAt(PREVIOUS_END);

        service.alert(PREVIOUS_END);

        verify(logRepository, never()).findBetween(any(), any(), any());
        verify(notificationSender, never()).send(any());
    }

    @Test
    @DisplayName("novedades sin usuarios de Subsecretaría: no manda mail")
    void newsWithoutRecipientsSendsNothing() {
        finishedRun();
        when(logRepository.findBetween(any(), eq(PREVIOUS_END), eq(RUN_END))).thenReturn(List.of(reviewEntry(1L)));
        subsecretaria();

        service.alert(PREVIOUS_END);

        verify(notificationSender, never()).send(any());
    }

    @Test
    @DisplayName("caída: nada hasta outageAlertAfter exacto, un mail apenas lo supera y queda registrado el aviso")
    void outageAlertBoundary() {
        state.setOutageSince(NOW.minus(Duration.ofHours(1)));

        service.alert(null);
        verify(notificationSender, never()).send(any());
        verify(stateService, never()).recordOutageAlert(any());

        state.setOutageSince(NOW.minus(Duration.ofHours(1)).minusSeconds(1));
        subsecretaria("sub@utn.edu.ar");

        service.alert(null);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(NotificationTemplate.SYSACAD_ALERT);
        assertThat((String) captor.getValue().model().get("resumen")).contains("no logra comunicarse");
        verify(stateService).recordOutageAlert(NOW);
    }

    @Test
    @DisplayName("caída: otro mail recién cuando pasó outageAlertRepeat desde el último, y no antes")
    void outageRepeatBoundary() {
        state.setOutageSince(NOW.minus(Duration.ofDays(3)));
        state.setLastOutageAlertAt(NOW.minus(Duration.ofDays(1)));

        service.alert(null);
        verify(notificationSender, never()).send(any());

        state.setLastOutageAlertAt(NOW.minus(Duration.ofDays(1)).minusSeconds(1));
        subsecretaria("sub@utn.edu.ar");

        service.alert(null);

        verify(notificationSender).send(any());
        verify(stateService).recordOutageAlert(NOW);
    }

    @Test
    @DisplayName("sin caída en curso: no manda mail de caída")
    void noOutageNoMail() {
        service.alert(null);

        verify(notificationSender, never()).send(any());
    }

    @Test
    @DisplayName("un fallo del sender se propaga a quien llama (el job lo atrapa) y no registra el aviso de caída")
    void senderFailureDoesNotRecordOutageAlert() {
        state.setOutageSince(NOW.minus(Duration.ofHours(5)));
        subsecretaria("sub@utn.edu.ar");
        doThrow(new IllegalStateException("smtp")).when(notificationSender).send(any());

        assertThatCode(() -> service.alert(null)).isInstanceOf(IllegalStateException.class);

        verify(stateService, never()).recordOutageAlert(any());
    }

    @Test
    @DisplayName("en DRY_RUN no sale ningún aviso: los avisos corren solo en ON")
    void dryRunSendsNoAlert() {
        SysacadProperties properties = new SysacadProperties();
        properties.setWrite(SysacadProperties.Write.of(SysacadWriteMode.DRY_RUN));
        SysacadAlertService dryRun = new SysacadAlertService(logRepository, scheduleRepository, stateService,
                occurrenceService, userService, notificationSender, properties, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        dryRun.alert(PREVIOUS_END);

        verifyNoInteractions(notificationSender, logRepository);
    }

    @Test
    @DisplayName("job frenado por horas cambiadas: avisa a Subsecretaría y no repite antes de un día")
    void hoursFreezeAlertsAtMostOncePerDay() {
        when(scheduleRepository.existsByStatusAndReviewReason(SysacadScheduleStatus.REVIEW,
                SysacadReviewReason.HOURS_CHANGED_AFTER_SEND)).thenReturn(true);
        subsecretaria("sub@utn.edu.ar");

        service.alert(null);

        verify(notificationSender).send(any());
        verify(stateService).recordOutageAlert(NOW);

        state.setLastOutageAlertAt(NOW.minusSeconds(3600));
        service.alert(null);

        verifyNoMoreInteractions(notificationSender);
    }
}
