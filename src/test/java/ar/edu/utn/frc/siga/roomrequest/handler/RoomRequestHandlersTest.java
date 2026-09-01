package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateConferenceDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateFinalExamDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOneTimeRoomChangeDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOtherDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamInClassDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreatePartialExamOffScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRegularRoomChangeDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.dto.request.ScheduledItemDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.validator.AcademicReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassroomReferenceValidator;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoomRequestTypeHandler")
class RoomRequestHandlersTest {

    private static final Long SUBJECT = 42L;
    private static final Long COMMISSION = 7L;
    private static final ClassSlot TUESDAY_SLOT =
            new ClassSlot(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));

    @Mock
    private AcademicReferenceValidator academicReference;
    @Mock
    private ClassroomReferenceValidator classroomReference;
    @Mock
    private ClassScheduleService classSchedule;

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private static ScheduledItemDto scheduledItem(LocalDate date, DayOfWeek dayOfWeek, Integer estimated) {
        return new ScheduledItemDto(date, dayOfWeek, estimated, 1, false, false, null, null, null, null, List.of());
    }

    private static FreeFormItemDto freeFormItem(Long commissionId) {
        return new FreeFormItemDto(commissionId, LocalDate.now().plusDays(7), LocalTime.of(10, 0),
                LocalTime.of(12, 0), 35, 1, false, false, null, null, null, null, List.of());
    }

    @Nested
    @DisplayName("OneTimeRoomChangeHandler")
    class OneTime {

        private OneTimeRoomChangeHandler handler;

        @BeforeEach
        void setUp() {
            handler = new OneTimeRoomChangeHandler(academicReference, classroomReference, classSchedule);
        }

        private CreateOneTimeRoomChangeDto dto(ScheduledItemDto... items) {
            return new CreateOneTimeRoomChangeDto(RoomRequestType.ONE_TIME_ROOM_CHANGE, requester(),
                    SUBJECT, COMMISSION, List.of(items));
        }

        @Test
        @DisplayName("happy: fecha por ítem, sin día ni estimado; valida materia+comisión y cada fecha contra el cursado")
        void happy() {
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), any())).thenReturn(TUESDAY_SLOT);
            CreateOneTimeRoomChangeDto dto = dto(scheduledItem(LocalDate.now().plusDays(7), null, null));

            assertThatCode(() -> handler.validate(dto)).doesNotThrowAnyException();
            verify(academicReference).requireSubject(SUBJECT);
            verify(academicReference).requireCommissionOfSubject(SUBJECT, COMMISSION);
        }

        @Test
        @DisplayName("sin fecha / con día / con estimado: rechazado antes de tocar otros módulos")
        void badShape() {
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(null, null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), DayOfWeek.MONDAY, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), null, 30))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            verifyNoInteractions(academicReference, classSchedule);
        }

        @Test
        @DisplayName("dos ítems con la misma fecha: rechazado")
        void duplicateDates() {
            LocalDate date = LocalDate.now().plusDays(7);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(date, null, null), scheduledItem(date, null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: deriva horario y evento del cursado, deja date y comisión, estimado null")
        void assemble() {
            LocalDate date = LocalDate.now().plusDays(7);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date))).thenReturn(TUESDAY_SLOT);

            RoomRequest request = handler.assemble(dto(scheduledItem(date, null, null)));

            assertThat(request.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getDate()).isEqualTo(date);
                assertThat(item.getDayOfWeek()).isNull();
                assertThat(item.getCommissionId()).isEqualTo(COMMISSION);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(18, 0));
                assertThat(item.getDuration()).isEqualTo(Duration.ofHours(2));
                assertThat(item.getSourceRecurringEventId()).isEqualTo(100L);
                assertThat(item.getEstimated()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("RegularRoomChangeHandler")
    class Regular {

        private RegularRoomChangeHandler handler;

        @BeforeEach
        void setUp() {
            handler = new RegularRoomChangeHandler(academicReference, classroomReference, classSchedule);
        }

        private CreateRegularRoomChangeDto dto(ScheduledItemDto... items) {
            return new CreateRegularRoomChangeDto(RoomRequestType.REGULAR_ROOM_CHANGE, requester(),
                    SUBJECT, COMMISSION, List.of(items));
        }

        @Test
        @DisplayName("happy: día por ítem, sin fecha; valida cada día contra el cursado")
        void happy() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), any())).thenReturn(TUESDAY_SLOT);
            assertThatCode(() -> handler.validate(dto(scheduledItem(null, DayOfWeek.TUESDAY, null))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sin día / con fecha: rechazado")
        void badShape() {
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(null, null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), DayOfWeek.TUESDAY, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("días repetidos: rechazado")
        void duplicateDays() {
            assertThatThrownBy(() -> handler.validate(dto(
                    scheduledItem(null, DayOfWeek.TUESDAY, null), scheduledItem(null, DayOfWeek.TUESDAY, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: date null, dayOfWeek seteado, horario del cursado")
        void assemble() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), eq(DayOfWeek.TUESDAY)))
                    .thenReturn(TUESDAY_SLOT);

            RoomRequest request = handler.assemble(dto(scheduledItem(null, DayOfWeek.TUESDAY, null)));

            assertThat(request.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getDate()).isNull();
                assertThat(item.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(18, 0));
                assertThat(item.getEstimated()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("PartialExamInClassHandler")
    class PartialInClass {

        private PartialExamInClassHandler handler;

        @BeforeEach
        void setUp() {
            handler = new PartialExamInClassHandler(academicReference, classroomReference, classSchedule);
        }

        private CreatePartialExamInClassDto dto(ScheduledItemDto... items) {
            return new CreatePartialExamInClassDto(RoomRequestType.PARTIAL_EXAM_IN_CLASS, requester(),
                    SUBJECT, COMMISSION, List.of(items));
        }

        @Test
        @DisplayName("happy: día + estimado; horario del cursado")
        void happy() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), any())).thenReturn(TUESDAY_SLOT);
            assertThatCode(() -> handler.validate(dto(scheduledItem(null, DayOfWeek.TUESDAY, 35))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sin estimado: rechazado (el parcial sí lo pide)")
        void estimatedRequired() {
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(null, DayOfWeek.TUESDAY, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: date null, estimado copiado, horario del cursado")
        void assemble() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), eq(DayOfWeek.TUESDAY)))
                    .thenReturn(TUESDAY_SLOT);

            RoomRequest request = handler.assemble(dto(scheduledItem(null, DayOfWeek.TUESDAY, 35)));

            assertThat(request.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getDate()).isNull();
                assertThat(item.getEstimated()).isEqualTo(35);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(18, 0));
            });
        }
    }

    @Nested
    @DisplayName("PartialExamOffScheduleHandler")
    class PartialOffSchedule {

        private PartialExamOffScheduleHandler handler;

        @BeforeEach
        void setUp() {
            handler = new PartialExamOffScheduleHandler(academicReference, classroomReference, classSchedule);
        }

        private CreatePartialExamOffScheduleDto dto(FreeFormItemDto... items) {
            return new CreatePartialExamOffScheduleDto(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, requester(),
                    SUBJECT, List.of(items));
        }

        @Test
        @DisplayName("happy: una comisión por ítem, distintas; valida cada comisión contra la materia")
        void happy() {
            CreatePartialExamOffScheduleDto dto = dto(freeFormItem(7L), freeFormItem(8L));

            assertThatCode(() -> handler.validate(dto)).doesNotThrowAnyException();
            verify(academicReference).requireCommissionOfSubject(SUBJECT, 7L);
            verify(academicReference).requireCommissionOfSubject(SUBJECT, 8L);
            verifyNoInteractions(classSchedule);
        }

        @Test
        @DisplayName("ítem sin comisión / comisiones repetidas: rechazado")
        void commissionRules() {
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(7L), freeFormItem(7L))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: usa fecha, franja y comisión del propio ítem")
        void assemble() {
            RoomRequest request = handler.assemble(dto(freeFormItem(7L)));

            assertThat(request.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getCommissionId()).isEqualTo(7L);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(10, 0));
                assertThat(item.getDuration()).isEqualTo(Duration.ofHours(2));
                assertThat(item.getEstimated()).isEqualTo(35);
            });
        }
    }

    @Nested
    @DisplayName("FinalExamHandler")
    class Final {

        private FinalExamHandler handler;

        @BeforeEach
        void setUp() {
            handler = new FinalExamHandler(academicReference, classroomReference, classSchedule);
        }

        private CreateFinalExamDto dto(FreeFormItemDto... items) {
            return new CreateFinalExamDto(RoomRequestType.FINAL_EXAM, requester(), SUBJECT, List.of(items));
        }

        @Test
        @DisplayName("happy: un solo ítem sin comisión; valida sólo la materia")
        void happy() {
            assertThatCode(() -> handler.validate(dto(freeFormItem(null)))).doesNotThrowAnyException();
            verify(academicReference).requireSubject(SUBJECT);
            verifyNoInteractions(classSchedule);
        }

        @Test
        @DisplayName("más de un ítem / ítem con comisión: rechazado")
        void rules() {
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(null), freeFormItem(null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(7L))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: comisión nula en la entidad")
        void assemble() {
            RoomRequest request = handler.assemble(dto(freeFormItem(null)));
            assertThat(request.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getCommissionId()).isNull());
        }
    }

    @Nested
    @DisplayName("ConferenceHandler / OtherHandler")
    class ConferenceAndOther {

        private ConferenceHandler conference;
        private OtherHandler other;

        @BeforeEach
        void setUp() {
            conference = new ConferenceHandler(academicReference, classroomReference, classSchedule);
            other = new OtherHandler(academicReference, classroomReference, classSchedule);
        }

        @Test
        @DisplayName("conferencia: materia y comisión opcionales; requiresExamUsers debe venir null")
        void conference() {
            CreateConferenceDto ok = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, null, List.of(freeFormItem(null)));
            assertThatCode(() -> conference.validate(ok)).doesNotThrowAnyException();
            verify(academicReference).validateOptionalSubject(null);

            FreeFormItemDto withExamUsers = new FreeFormItemDto(null, LocalDate.now().plusDays(7),
                    LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1, false, true, 5, true, null, null, List.of());
            CreateConferenceDto bad = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, null, List.of(withExamUsers));
            assertThatThrownBy(() -> conference.validate(bad)).isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("otro: cada ítem exige observations")
        void other() {
            FreeFormItemDto withObs = new FreeFormItemDto(null, LocalDate.now().plusDays(7),
                    LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1, false, false, null, null, null,
                    "Grabación de video", List.of());
            CreateOtherDto ok = new CreateOtherDto(RoomRequestType.OTHER, requester(), null, null, List.of(withObs));
            assertThatCode(() -> other.validate(ok)).doesNotThrowAnyException();

            CreateOtherDto bad = new CreateOtherDto(RoomRequestType.OTHER, requester(), null, null,
                    List.of(freeFormItem(null)));
            assertThatThrownBy(() -> other.validate(bad))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("observations");
        }
    }
}
