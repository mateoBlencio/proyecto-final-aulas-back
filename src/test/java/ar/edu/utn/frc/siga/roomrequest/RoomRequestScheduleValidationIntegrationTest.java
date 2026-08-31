package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOneTimeRoomChangeDto;
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
    void seedCursado() {
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
    @DisplayName("regular: día en que la comisión no dicta → rechazado")
    void regular_nonCursadoDay_isRejected() {
        assertThatThrownBy(() -> roomRequestService.create(new CreateRegularRoomChangeDto(
                RoomRequestType.REGULAR_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(null, DayOfWeek.MONDAY)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no dicta clase");
    }

    @Test
    @DisplayName("por única vez: fecha de cursado real → 201")
    void oneTime_cursadoDate_isAccepted() {
        LocalDate nextTuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

        assertThatCode(() -> roomRequestService.create(new CreateOneTimeRoomChangeDto(
                RoomRequestType.ONE_TIME_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(nextTuesday, null))))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("por única vez: fecha sin clase (un lunes) → rechazado")
    void oneTime_nonCursadoDate_isRejected() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> roomRequestService.create(new CreateOneTimeRoomChangeDto(
                RoomRequestType.ONE_TIME_ROOM_CHANGE, requester(), subjectId, commissionId,
                List.of(scheduled(nextMonday, null)))))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("no tiene clase");
    }

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private static ScheduledItemDto scheduled(LocalDate date, DayOfWeek dayOfWeek) {
        return new ScheduledItemDto(date, dayOfWeek, null, 1, false, false, null, null, null, null, List.of());
    }
}
