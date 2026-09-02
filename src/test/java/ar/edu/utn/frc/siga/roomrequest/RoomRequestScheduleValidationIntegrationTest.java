package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOneTimeRoomChangeDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamInClassDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRegularRoomChangeDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.dto.request.ScheduledItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloqueo de calendario del lado del backend: los cambios de aula sólo se aceptan para días y fechas
 * en que la comisión efectivamente cursa, y el día/horario del pedido se deriva del cursado.
 */
@Import(IntegrationTestData.class)
@DisplayName("Solicitudes de aula — validación contra el cursado (integración)")
class RoomRequestScheduleValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private RoomRequestService roomRequestService;

    private Long subjectId;
    private Long commissionId;

    @BeforeEach
    void seedClassSchedule() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        subjectId = academic.subjectId();
        commissionId = academic.commissionId();
        // La comisión cursa los martes de 18:00 a 20:00 durante cuatro meses.
        testData.eventoRecurrente(subjectId, commissionId, DayOfWeek.TUESDAY, java.time.LocalTime.of(18, 0),
                120, LocalDate.now().minusDays(1), LocalDate.now().plusMonths(4), 30);
    }

    @Test
    @DisplayName("regular: día de dictado válido → 201, con date null y el horario tomado del cursado")
    void regular_validDay_derivesSchedule() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateRegularRoomChangeDto(
                RoomRequestType.REGULAR_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.TUESDAY))));

        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.date()).isNull();
            assertThat(item.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(item.startTime()).isEqualTo(java.time.LocalTime.of(18, 0));
            assertThat(item.durationMinutes()).isEqualTo(120);
            assertThat(item.estimated()).isNull();
        });
    }

    @Test
    @DisplayName("regular: cursada anual cargada como un evento recurrente por cuatrimestre → 201, no la trata como doble bloque")
    void regular_annualSplitSameDayAndTime_isAccepted() {
        testData.eventoRecurrente(subjectId, commissionId, DayOfWeek.TUESDAY, java.time.LocalTime.of(18, 0),
                120, LocalDate.now().plusMonths(4).plusDays(1), LocalDate.now().plusMonths(8), 30);

        RoomRequestResponseDto created = roomRequestService.create(new CreateRegularRoomChangeDto(
                RoomRequestType.REGULAR_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.TUESDAY))));

        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(item.startTime()).isEqualTo(java.time.LocalTime.of(18, 0));
            assertThat(item.durationMinutes()).isEqualTo(120);
        });
    }

    @Test
    @DisplayName("regular: día en que la comisión no dicta → rechazado")
    void regular_nonClassDay_isRejected() {
        assertThatThrownBy(() -> roomRequestService.create(new CreateRegularRoomChangeDto(
                RoomRequestType.REGULAR_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.MONDAY)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no dicta clase");
    }

    @Test
    @DisplayName("por única vez: fecha de cursado real → 201")
    void oneTime_classDate_isAccepted() {
        LocalDate nextTuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

        assertThatCode(() -> roomRequestService.create(new CreateOneTimeRoomChangeDto(
                RoomRequestType.ONE_TIME_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(nextTuesday, null))))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("por única vez: fecha sin clase (un lunes) → rechazado")
    void oneTime_nonClassDate_isRejected() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> roomRequestService.create(new CreateOneTimeRoomChangeDto(
                RoomRequestType.ONE_TIME_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(nextMonday, null)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no tiene clase");
    }

    @Test
    @DisplayName("parcial en clase: día de dictado válido → 201, hereda el horario del cursado y guarda el estimado")
    void partialInClass_validDay_derivesScheduleAndKeepsEstimated() {
        RoomRequestResponseDto created = roomRequestService.create(new CreatePartialExamInClassDto(
                RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.TUESDAY, 40))));

        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.date()).isNull();
            assertThat(item.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(item.startTime()).isEqualTo(java.time.LocalTime.of(18, 0));
            assertThat(item.durationMinutes()).isEqualTo(120);
            assertThat(item.estimated()).isEqualTo(40);
            assertThat(item.commission()).isNotNull();
        });
    }

    @Test
    @DisplayName("parcial en clase: día en que la comisión no dicta → rechazado")
    void partialInClass_nonClassDay_isRejected() {
        assertThatThrownBy(() -> roomRequestService.create(new CreatePartialExamInClassDto(
                RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.MONDAY, 40)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no dicta clase");
    }

    @Test
    @DisplayName("parcial en clase: sin cantidad estimada → rechazado, es el dato que el cursado no aporta")
    void partialInClass_withoutEstimated_isRejected() {
        assertThatThrownBy(() -> roomRequestService.create(new CreatePartialExamInClassDto(
                RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.TUESDAY, null)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("cantidad estimada");
    }

    @Test
    @DisplayName("parcial en clase: con fecha puntual → rechazado, este tipo se ata al día de dictado")
    void partialInClass_withDate_isRejected() {
        LocalDate nextTuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

        assertThatThrownBy(() -> roomRequestService.create(new CreatePartialExamInClassDto(
                RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(), subjectId, commissionId,
                List.of(scheduled(nextTuesday, DayOfWeek.TUESDAY, 40)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no a una fecha");
    }

    @Test
    @DisplayName("parcial en clase: cursada anual partida en dos tramos → 201, no la trata como doble bloque")
    void partialInClass_annualSplit_isAccepted() {
        testData.eventoRecurrente(subjectId, commissionId, DayOfWeek.TUESDAY, java.time.LocalTime.of(18, 0),
                120, LocalDate.now().plusMonths(4).plusDays(1), LocalDate.now().plusMonths(8), 30);

        RoomRequestResponseDto created = roomRequestService.create(new CreatePartialExamInClassDto(
                RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.TUESDAY, 40))));

        assertThat(created.items()).singleElement()
                .satisfies(item -> assertThat(item.startTime()).isEqualTo(java.time.LocalTime.of(18, 0)));
    }

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private static ScheduledItemDto scheduled(LocalDate date, DayOfWeek dayOfWeek) {
        return scheduled(date, dayOfWeek, null);
    }

    private static ScheduledItemDto scheduled(LocalDate date, DayOfWeek dayOfWeek, Integer estimated) {
        return new ScheduledItemDto(date, dayOfWeek, estimated, 1, false, false, null, null, null, null, List.of());
    }
}
