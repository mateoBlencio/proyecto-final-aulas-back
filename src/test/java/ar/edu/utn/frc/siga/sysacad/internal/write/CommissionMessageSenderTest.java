package ar.edu.utn.frc.siga.sysacad.internal.write;

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
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties.MessageCommission;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private CommissionMessageSender sender;
    private RequestPacer pacer;
    private SysacadSchedule schedule;

    @BeforeEach
    void setUp() {
        properties = new SysacadProperties();
        properties.setCommissionMessageEnabled(true);
        properties.setCommissionMessageCommissions(List.of(new MessageCommission(9, 2023, 101, 1)));
        sender = new CommissionMessageSender(client, stateWriter, subjectService, classroomService, properties);
        pacer = new RequestPacer(Clock.systemUTC(), wait -> { }, 30);
        schedule = SysacadSchedule.builder().id(SCHEDULE_ID)
                .year(2026).specialty(9).plan(2023).subject(101).commission(1).build();
    }

    private static Send decision(int room) {
        return new Send(BUILDING, room, OCCURRENCE_ID, CLASS_DATE, LocalTime.of(8, 0), LocalTime.of(10, 15));
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
    @DisplayName("Con la flag apagada no consulta nada, no llama a SysAcad y no registra")
    void disabledDoesNothing() {
        properties.setCommissionMessageEnabled(false);

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @Test
    @DisplayName("Sin comisiones listadas no avisa a nadie aunque la flag esté encendida")
    void emptyListSendsToNobody() {
        properties.setCommissionMessageCommissions(List.of());

        sender.sendAfterConfirmation(schedule, decision(ROOM), pacer);

        verifyNoInteractions(client, stateWriter, subjectService, classroomService);
    }

    @ParameterizedTest(name = "especialidad {0}, plan {1}, materia {2}, comisión {3}")
    @CsvSource({"27, 2023, 101, 1", "9, 2008, 101, 1", "9, 2023, 581, 1", "9, 2023, 101, 2"})
    @DisplayName("Un horario que difiere de la comisión listada en un solo campo no recibe el mensaje")
    void unlistedCommissionIsNotNotified(int specialty, int plan, int subject, int commission) {
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

        String note = "Cambio de aula: tu clase de Física I del martes 17/11 será en el aula 229 de Maders.";
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
                "Cambio de aula: tu clase de Física I del martes 17/11 será en el aula 229 de Maders.", false,
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
}
