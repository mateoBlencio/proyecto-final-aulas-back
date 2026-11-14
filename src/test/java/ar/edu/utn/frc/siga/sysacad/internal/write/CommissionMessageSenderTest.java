package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.SysacadTestSettings;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadWriteClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageCommand;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.CommissionMessageResult;
import ar.edu.utn.frc.siga.sysacad.internal.config.CommissionMessageMode;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties.MessageCommission;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NotifyOnly;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.RoomRef;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionMessageSender")
class CommissionMessageSenderTest {

    private static final long SCHEDULE_ID = 1L;
    private static final long OCCURRENCE_ID = 100L;
    private static final int BUILDING = 5;
    private static final int ROOM = 229;
    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 11, 17);

    @Mock
    private SysacadWriteClient client;
    @Mock
    private SysacadScheduleStateWriter stateWriter;
    @Mock
    private SubjectService subjectService;
    @Mock
    private ClassroomService classroomService;

    private SysacadProperties properties;
    private SysacadRuntimeSettings runtimeSettings;
    private CommissionMessageSender sender;
    private RequestPacer pacer;
    private SysacadSchedule schedule;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        properties = new SysacadProperties();
        runtimeSettings = SysacadTestSettings.with(SysacadWriteMode.ON);
        lenient().when(runtimeSettings.commissionMessageMode()).thenReturn(CommissionMessageMode.ON);
        environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        sender = new CommissionMessageSender(client, stateWriter, subjectService, classroomService, properties,
                runtimeSettings, environment);
        pacer = new RequestPacer(Clock.systemUTC(), wait -> { }, 30);
        schedule = SysacadSchedule.builder().id(SCHEDULE_ID)
                .year(2026).specialty(9).plan(2023).subject(101).commission(1).build();
    }

    private static Send decision(int room) {
        return new Send(BUILDING, room, OCCURRENCE_ID, CLASS_DATE, LocalTime.of(8, 0), LocalTime.of(10, 15),
                List.of(new RoomRef(BUILDING, room)));
    }

    private void subjectIs(String name) {
        when(subjectService.findByCodeAndStudyPlan(101, 2023, 9))
                .thenReturn(new SubjectResponseDto(1L, 101, name, null, null, true, null));
    }

    private void buildingIs(String name) {
        when(classroomService.findByRoomNumberAndBuildingCode(ROOM, BUILDING)).thenReturn(Optional.of(
                new ClassroomResponseDto(1L, ROOM, 40, 1L, name, 1L, "Aula", null, true)));
    }

    private CommissionMessageCommand sentCommand() {
        ArgumentCaptor<CommissionMessageCommand> command = ArgumentCaptor.forClass(CommissionMessageCommand.class);
        verify(client).sendCommissionMessage(command.capture());
        return command.getValue();
    }

    @Test
    @DisplayName("En OFF no consulta nada, no llama a SysAcad y no registra, aunque la comisión esté listada")
    void offDoesNothing() {
        when(runtimeSettings.commissionMessageMode()).thenReturn(CommissionMessageMode.OFF);
        properties.setCommissionMessageTestCommissions(List.of(new MessageCommission(9, 2023, 101, 1)));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @Test
    @DisplayName("En ON con perfil prod manda a una comisión que no está listada")
    void onInProdSendsToUnlistedCommission() {
        properties.setCommissionMessageTestCommissions(List.of(new MessageCommission(27, 2008, 581, 5)));
        subjectIs("Física I");
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        assertThat(sentCommand().commission()).isEqualTo(1);
    }

    @Test
    @DisplayName("En ON sin perfil prod manda a una comisión listada")
    void onOutsideProdSendsToListedCommission() {
        environment.setActiveProfiles("dev");
        properties.setCommissionMessageTestCommissions(List.of(new MessageCommission(9, 2023, 101, 1)));
        subjectIs("Física I");
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        assertThat(sentCommand().commission()).isEqualTo(1);
    }

    @Test
    @DisplayName("En ON sin perfil prod y sin comisiones listadas no avisa a nadie")
    void onOutsideProdWithoutListSendsToNobody() {
        environment.setActiveProfiles("dev");

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @ParameterizedTest(name = "especialidad {0}, plan {1}, materia {2}, comisión {3}")
    @CsvSource({"27, 2023, 101, 1", "9, 2008, 101, 1", "9, 2023, 581, 1", "9, 2023, 101, 2"})
    @DisplayName("En ON sin perfil prod, un horario que difiere de la comisión listada en un solo campo no recibe el mensaje")
    void onOutsideProdSkipsUnlistedCommission(int specialty, int plan, int subject, int commission) {
        environment.setActiveProfiles("dev");
        properties.setCommissionMessageTestCommissions(List.of(new MessageCommission(9, 2023, 101, 1)));
        schedule = SysacadSchedule.builder().id(SCHEDULE_ID).year(2026)
                .specialty(specialty).plan(plan).subject(subject).commission(commission).build();

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @Test
    @DisplayName("Manda a la comisión del horario, visible hasta la fecha de la clase, y registra el envío")
    void sendsAndRecords() {
        subjectIs("Física I");
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        String note = "La clase de Física I del martes 17/11 será en el aula 229 de Maders.";
        assertThat(sentCommand()).isEqualTo(new CommissionMessageCommand(CLASS_DATE, 2026, 9, 2023, 101, 1, note));
        verify(stateWriter).recordCommissionMessage(SCHEDULE_ID, OCCURRENCE_ID, BUILDING, ROOM, note, true, "ok");
    }

    @Test
    @DisplayName("Clase liberada: no avisa a la comisión ni registra un mensaje")
    void releasedClassIsNotNotified() {
        sender.sendAfterConfirmation(schedule, decision(999), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @Test
    @DisplayName("Materia con nombre largo: recorta la materia y conserva el aula dentro de los 150 caracteres")
    void longSubjectKeepsRoomWithinLimit() {
        subjectIs("M".repeat(200));
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        assertThat(sentCommand().note()).hasSize(150).endsWith(" del martes 17/11 será en el aula 229 de Maders.");
    }

    @Test
    @DisplayName("SysAcad rechaza el mensaje: registra el fallo sin lanzar")
    void rejectedMessageIsRecorded() {
        subjectIs("Física I");
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(false, "HTTP 500: error"));

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verify(stateWriter).recordCommissionMessage(SCHEDULE_ID, OCCURRENCE_ID, BUILDING, ROOM,
                "La clase de Física I del martes 17/11 será en el aula 229 de Maders.", false,
                "HTTP 500: error");
    }

    @Test
    @DisplayName("Una excepción al armar el texto no sale del método, no llama a SysAcad y queda registrada")
    void failureDoesNotEscape() {
        when(subjectService.findByCodeAndStudyPlan(101, 2023, 9)).thenThrow(new IllegalStateException("sin materia"));

        assertThatCode(() -> sender.sendAfterConfirmation(schedule, decision(ROOM), pacer))
                .doesNotThrowAnyException();

        verify(client, never()).sendCommissionMessage(any());
        verify(stateWriter).recordCommissionMessage(eq(SCHEDULE_ID), eq(OCCURRENCE_ID), eq(BUILDING), eq(ROOM),
                isNull(), eq(false), argThat(response -> response.contains("sin materia")));
    }

    @Test
    @DisplayName("Aula sin edificio resuelto: no manda un número ambiguo y registra el fallo")
    void roomWithoutBuildingIsNotSent() {
        subjectIs("Física I");
        when(classroomService.findByRoomNumberAndBuildingCode(ROOM, BUILDING)).thenReturn(Optional.empty());

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verify(client, never()).sendCommissionMessage(any());
        verify(stateWriter).recordCommissionMessage(eq(SCHEDULE_ID), eq(OCCURRENCE_ID), eq(BUILDING), eq(ROOM),
                isNull(), eq(false), argThat(response -> response.contains("Sin edificio")));
    }

    @Test
    @DisplayName("Si falla el registro de un mensaje entregado, no lanza ni lo registra como fallido")
    void recordFailureDoesNotEscape() {
        subjectIs("Física I");
        buildingIs("Maders");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));
        doThrow(new IllegalStateException("sin base")).when(stateWriter)
                .recordCommissionMessage(any(), any(), any(), any(), any(), eq(true), any());

        assertThatCode(() -> sender.sendAfterConfirmation(schedule, decision(ROOM), pacer))
                .doesNotThrowAnyException();

        verify(stateWriter, never()).recordCommissionMessage(any(), any(), any(), any(), any(), eq(false), any());
    }

    @Test
    @DisplayName("Con ON fuera de prod avisa al arrancar que solo llega a las comisiones de prueba")
    void warnsOnStartupWhenOnOutsideProd() {
        environment.setActiveProfiles("dev");
        ListAppender<ILoggingEvent> logs = captureLogs();

        sender.warnIfLimitedToTestCommissions();

        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("No avisa al arrancar con ON en prod ni con OFF fuera de prod")
    void doesNotWarnInProdOrWhenOff() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        sender.warnIfLimitedToTestCommissions();

        environment.setActiveProfiles("dev");
        when(runtimeSettings.commissionMessageMode()).thenReturn(CommissionMessageMode.OFF);
        sender.warnIfLimitedToTestCommissions();

        assertThat(logs.list).isEmpty();
    }

    private void buildingIs(int building, int room, String name) {
        when(classroomService.findByRoomNumberAndBuildingCode(room, building)).thenReturn(Optional.of(
                new ClassroomResponseDto(1L, room, 40, 1L, name, 1L, "Aula", null, true)));
    }

    private static NotifyOnly notify(RoomRef... rooms) {
        return new NotifyOnly(OCCURRENCE_ID, CLASS_DATE, List.of(rooms));
    }

    @Test
    @DisplayName("Dos aulas en el mismo edificio: 'las aulas 910 y 920 de X'")
    void twoRoomsInSameBuilding() {
        subjectIs("Física I");
        buildingIs(BUILDING, 910, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendRoomsUpdate(schedule, notify(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920)), pacer);

        assertThat(sentCommand().note())
                .isEqualTo("La clase de Física I del martes 17/11 será en las aulas 910 y 920 de Possetto.");
    }

    @Test
    @DisplayName("Tres aulas en el mismo edificio: 'las aulas 910, 920 y 922 de X'")
    void threeRoomsInSameBuilding() {
        subjectIs("Física I");
        buildingIs(BUILDING, 910, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendRoomsUpdate(schedule,
                notify(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920), new RoomRef(BUILDING, 922)), pacer);

        assertThat(sentCommand().note())
                .isEqualTo("La clase de Física I del martes 17/11 será en las aulas 910, 920 y 922 de Possetto.");
    }

    @Test
    @DisplayName("Edificios distintos: 'el aula 3 de A y el aula 5 de B'")
    void roomsInDifferentBuildings() {
        subjectIs("Física I");
        buildingIs(2, 3, "Maders");
        buildingIs(BUILDING, 5, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendRoomsUpdate(schedule, notify(new RoomRef(2, 3), new RoomRef(BUILDING, 5)), pacer);

        assertThat(sentCommand().note()).isEqualTo(
                "La clase de Física I del martes 17/11 será en el aula 3 de Maders y el aula 5 de Possetto.");
    }

    @Test
    @DisplayName("Materia larga con dos aulas: recorta la materia, queda en 150 y conserva la cola")
    void longSubjectWithTwoRoomsKeepsTail() {
        subjectIs("M".repeat(200));
        buildingIs(BUILDING, 910, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendRoomsUpdate(schedule, notify(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920)), pacer);

        assertThat(sentCommand().note()).hasSize(150).startsWith("La clase de MMM")
                .endsWith(" del martes 17/11 será en las aulas 910 y 920 de Possetto.");
    }

    @Test
    @DisplayName("sendRoomsUpdate en OFF no consulta nada, no llama a SysAcad y no registra")
    void roomsUpdateOffDoesNothing() {
        when(runtimeSettings.commissionMessageMode()).thenReturn(CommissionMessageMode.OFF);

        sender.sendRoomsUpdate(schedule, notify(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920)), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @Test
    @DisplayName("sendRoomsUpdate en ON manda con la fecha de la clase y registra con la aula principal")
    void roomsUpdateOnSendsAndRecords() {
        subjectIs("Física I");
        buildingIs(BUILDING, 910, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));

        sender.sendRoomsUpdate(schedule, notify(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920)), pacer);

        String note = "La clase de Física I del martes 17/11 será en las aulas 910 y 920 de Possetto.";
        assertThat(sentCommand()).isEqualTo(new CommissionMessageCommand(CLASS_DATE, 2026, 9, 2023, 101, 1, note));
        verify(stateWriter).recordCommissionMessage(SCHEDULE_ID, OCCURRENCE_ID, BUILDING, 910, note, true, "ok");
    }

    @Test
    @DisplayName("sendAfterConfirmation con Send de dos aulas nombra ambas")
    void sendAfterConfirmationNamesAllRooms() {
        subjectIs("Física I");
        buildingIs(BUILDING, 910, "Possetto");
        when(client.sendCommissionMessage(any())).thenReturn(new CommissionMessageResult(true, "ok"));
        Send send = new Send(BUILDING, 910, OCCURRENCE_ID, CLASS_DATE, LocalTime.of(8, 0), LocalTime.of(10, 15),
                List.of(new RoomRef(BUILDING, 910), new RoomRef(BUILDING, 920)));

        sender.sendAfterConfirmation(schedule, send, pacer);

        assertThat(sentCommand().note()).endsWith("será en las aulas 910 y 920 de Possetto.");
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(CommissionMessageSender.class)).addAppender(appender);
        return appender;
    }
}
