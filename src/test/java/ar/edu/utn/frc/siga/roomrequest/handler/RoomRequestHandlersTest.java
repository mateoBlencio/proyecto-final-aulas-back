package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.events.exception.InvalidEventScheduleException;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
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
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoomRequestTypeHandler")
class RoomRequestHandlersTest {

    private static final Long SUBJECT = 42L;
    private static final Long OTHER_SUBJECT = 43L;
    private static final Long COMMISSION = 7L;
    private static final ClassSlot TUESDAY_SLOT =
            new ClassSlot(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));

    @Mock
    private AcademicReferenceValidator academicReference;
    @Mock
    private ClassroomReferenceValidator classroomReference;
    @Mock
    private ClassScheduleService classSchedule;
    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private EventScheduleValidator eventSchedule;

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private static ScheduledItemDto scheduledItem(LocalDate date, DayOfWeek dayOfWeek, Integer estimated) {
        return scheduledItem(date, dayOfWeek, estimated, null);
    }

    private static ScheduledItemDto scheduledItem(LocalDate date, DayOfWeek dayOfWeek, Integer estimated,
                                                  LocalTime startTime) {
        return new ScheduledItemDto(date, dayOfWeek, startTime, estimated, 1, false, false, null, null, null, null,
                List.of());
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
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), any(), any())).thenReturn(TUESDAY_SLOT);
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
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date), any())).thenReturn(TUESDAY_SLOT);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(date, null, null), scheduledItem(date, null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("misma fecha con dos horarios distintos de la comisión: se aceptan (caso 6)")
        void sameDateDifferentClasses() {
            LocalDate date = LocalDate.now().plusDays(7);
            ClassSlot morning = new ClassSlot(200L, DayOfWeek.TUESDAY, LocalTime.of(10, 25), LocalTime.of(11, 55));
            ClassSlot night = new ClassSlot(201L, DayOfWeek.TUESDAY, LocalTime.of(20, 40), LocalTime.of(22, 55));
            when(classSchedule.requireClassDate(SUBJECT, COMMISSION, date, LocalTime.of(10, 25))).thenReturn(morning);
            when(classSchedule.requireClassDate(SUBJECT, COMMISSION, date, LocalTime.of(20, 40))).thenReturn(night);
            assertThatCode(() -> handler.validate(dto(
                    scheduledItem(date, null, null, LocalTime.of(10, 25)),
                    scheduledItem(date, null, null, LocalTime.of(20, 40)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("misma fecha, una con horario y otra sin, que resuelven la misma clase: rechazado")
        void sameDateSameResolvedClass() {
            LocalDate date = LocalDate.now().plusDays(7);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date), any())).thenReturn(TUESDAY_SLOT);
            assertThatThrownBy(() -> handler.validate(dto(
                    scheduledItem(date, null, null, null),
                    scheduledItem(date, null, null, LocalTime.of(18, 0)))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("hoy con horario ya pasado: rechazado")
        void todayButAlreadyPast() {
            LocalDate today = LocalDate.now();
            ClassSlot pastSlot = new ClassSlot(100L, today.getDayOfWeek(), LocalTime.MIDNIGHT, LocalTime.MAX);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(today), any())).thenReturn(pastSlot);

            assertThatThrownBy(() -> handler.assemble(dto(scheduledItem(today, null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("hoy con horario que todavía no llegó: permitido")
        void todayButNotYetPast() {
            LocalDate today = LocalDate.now();
            ClassSlot futureSlot = new ClassSlot(100L, today.getDayOfWeek(), LocalTime.MAX, LocalTime.MAX);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(today), any())).thenReturn(futureSlot);

            assertThatCode(() -> handler.assemble(dto(scheduledItem(today, null, null))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assemble: deriva horario y evento del cursado, deja date y comisión, estimado null")
        void assemble() {
            LocalDate date = LocalDate.now().plusDays(7);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date), any())).thenReturn(TUESDAY_SLOT);

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
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), any(), any())).thenReturn(TUESDAY_SLOT);
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
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), eq(DayOfWeek.TUESDAY), any()))
                    .thenReturn(TUESDAY_SLOT);
            assertThatThrownBy(() -> handler.validate(dto(
                    scheduledItem(null, DayOfWeek.TUESDAY, null), scheduledItem(null, DayOfWeek.TUESDAY, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("martes y martes 18:00 resuelven la misma clase: rechazado")
        void sameDayOneWithTimeOneWithout() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), eq(DayOfWeek.TUESDAY), any()))
                    .thenReturn(TUESDAY_SLOT);
            assertThatThrownBy(() -> handler.validate(dto(
                    scheduledItem(null, DayOfWeek.TUESDAY, null, null),
                    scheduledItem(null, DayOfWeek.TUESDAY, null, LocalTime.of(18, 0)))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("mismo día con dos horarios distintos: se aceptan y cada uno toma su clase (caso 6)")
        void sameDayDifferentClasses() {
            ClassSlot morning = new ClassSlot(200L, DayOfWeek.MONDAY, LocalTime.of(10, 25), LocalTime.of(11, 55));
            ClassSlot night = new ClassSlot(201L, DayOfWeek.MONDAY, LocalTime.of(20, 40), LocalTime.of(22, 55));
            when(classSchedule.requireClassDay(SUBJECT, COMMISSION, DayOfWeek.MONDAY, LocalTime.of(10, 25))).thenReturn(morning);
            when(classSchedule.requireClassDay(SUBJECT, COMMISSION, DayOfWeek.MONDAY, LocalTime.of(20, 40))).thenReturn(night);

            RoomRequest request = handler.assemble(dto(
                    scheduledItem(null, DayOfWeek.MONDAY, null, LocalTime.of(10, 25)),
                    scheduledItem(null, DayOfWeek.MONDAY, null, LocalTime.of(20, 40))));
            assertThatCode(() -> handler.validate(dto(
                    scheduledItem(null, DayOfWeek.MONDAY, null, LocalTime.of(10, 25)),
                    scheduledItem(null, DayOfWeek.MONDAY, null, LocalTime.of(20, 40)))))
                    .doesNotThrowAnyException();

            assertThat(request.getItems()).extracting(RoomRequestItem::getStartTime)
                    .containsExactly(LocalTime.of(10, 25), LocalTime.of(20, 40));
        }

        @Test
        @DisplayName("assemble: date null, dayOfWeek seteado, horario del cursado")
        void assemble() {
            when(classSchedule.requireClassDay(eq(SUBJECT), eq(COMMISSION), eq(DayOfWeek.TUESDAY), any()))
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
        @DisplayName("happy: fecha + estimado; horario derivado del cursado de esa fecha")
        void happy() {
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), any(), any())).thenReturn(TUESDAY_SLOT);
            assertThatCode(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), null, 35))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sin fecha / con día: rechazado antes de tocar otros módulos")
        void badShape() {
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(null, null, 35))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), DayOfWeek.TUESDAY, 35))))
                    .isInstanceOf(InvalidRoomRequestException.class);
            verifyNoInteractions(academicReference, classSchedule);
        }

        @Test
        @DisplayName("sin estimado: rechazado (el parcial sí lo pide)")
        void estimatedRequired() {
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(LocalDate.now().plusDays(7), null, null))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("dos ítems con la misma fecha: rechazado")
        void duplicateDates() {
            LocalDate date = LocalDate.now().plusDays(7);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date), any())).thenReturn(TUESDAY_SLOT);
            assertThatThrownBy(() -> handler.validate(dto(scheduledItem(date, null, 35), scheduledItem(date, null, 40))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("assemble: dayOfWeek null, fecha y estimado copiados, horario del cursado")
        void assemble() {
            LocalDate date = LocalDate.now().plusDays(7);
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(date), any())).thenReturn(TUESDAY_SLOT);

            RoomRequest request = handler.assemble(dto(scheduledItem(date, null, 35)));

            assertThat(request.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getDate()).isEqualTo(date);
                assertThat(item.getDayOfWeek()).isNull();
                assertThat(item.getEstimated()).isEqualTo(35);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(18, 0));
                assertThat(item.getSourceRecurringEventId()).isEqualTo(100L);
            });
        }

        @Test
        @DisplayName("menos de 2hs de anticipación según el horario resuelto del cursado: rechazado")
        void requiresAdvanceNotice() {
            LocalDate today = LocalDate.now();
            ClassSlot soonSlot = new ClassSlot(100L, today.getDayOfWeek(),
                    LocalTime.now().plusMinutes(30), LocalTime.now().plusMinutes(90));
            when(classSchedule.requireClassDate(eq(SUBJECT), eq(COMMISSION), eq(today), any())).thenReturn(soonSlot);

            assertThatThrownBy(() -> handler.assemble(dto(scheduledItem(today, null, 35))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }
    }

    @Nested
    @DisplayName("PartialExamOffScheduleHandler")
    class PartialOffSchedule {

        private PartialExamOffScheduleHandler handler;

        @BeforeEach
        void setUp() {
            handler = new PartialExamOffScheduleHandler(academicReference, classroomReference, classSchedule, itemRepository, eventSchedule);
            when(itemRepository.findActiveOffScheduleItemsByDateIn(any())).thenReturn(List.of());
        }

        private CreatePartialExamOffScheduleDto dto(FreeFormItemDto... items) {
            return new CreatePartialExamOffScheduleDto(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, requester(),
                    SUBJECT, List.of(items));
        }

        private FreeFormItemDto freeFormItemAt(Long commissionId, LocalDate date, LocalTime startTime, LocalTime endTime) {
            return new FreeFormItemDto(commissionId, date, startTime, endTime, 35, 1, false, false, null, null, null, null, List.of());
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
        @DisplayName("sin comisión: válido, significa 'todas las vigentes' y no valida ninguna comisión puntual")
        void nullCommissionMeansAllActive() {
            assertThatCode(() -> handler.validate(dto(freeFormItem(null)))).doesNotThrowAnyException();
            verify(academicReference).requireSubject(SUBJECT);
            verify(academicReference, never()).requireCommissionOfSubject(any(), any());
        }

        @Test
        @DisplayName("dos ítems 'todas' que se pisan en la misma solicitud: rechazado (misma materia)")
        void twoNullCommissionsOverlappingCollide() {
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(null), freeFormItem(null))))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("Esa comisión ya tiene otro pedido en la misma fecha y horario");
        }

        @Test
        @DisplayName("dos ítems 'todas' en horarios que no se pisan en la misma solicitud: permitido")
        void twoNullCommissionsNotOverlappingDoNotCollide() {
            LocalDate date = LocalDate.now().plusDays(7);
            assertThatCode(() -> handler.validate(dto(
                    freeFormItemAt(null, date, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    freeFormItemAt(null, date, LocalTime.of(12, 0), LocalTime.of(14, 0)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("misma comisión, misma fecha, mismo horario: rechazado")
        void commissionRules() {
            assertThatThrownBy(() -> handler.validate(dto(freeFormItem(7L), freeFormItem(7L))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("misma comisión, distinta fecha: permitido")
        void sameCommissionDifferentDate() {
            LocalDate day1 = LocalDate.now().plusDays(7);
            LocalDate day2 = LocalDate.now().plusDays(21);
            assertThatCode(() -> handler.validate(dto(
                    freeFormItemAt(7L, day1, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    freeFormItemAt(7L, day2, LocalTime.of(10, 0), LocalTime.of(12, 0)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("misma comisión, misma fecha, horarios que no se pisan: permitido")
        void sameCommissionSameDateNonOverlappingTime() {
            LocalDate date = LocalDate.now().plusDays(7);
            assertThatCode(() -> handler.validate(dto(
                    freeFormItemAt(7L, date, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    freeFormItemAt(7L, date, LocalTime.of(14, 0), LocalTime.of(16, 0)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("misma comisión, misma fecha, horarios que se pisan: rechazado")
        void sameCommissionSameDateOverlappingTime() {
            LocalDate date = LocalDate.now().plusDays(7);
            assertThatThrownBy(() -> handler.validate(dto(
                    freeFormItemAt(7L, date, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    freeFormItemAt(7L, date, LocalTime.of(11, 0), LocalTime.of(13, 0)))))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("Esa comisión ya tiene otro pedido en la misma fecha y horario");
        }

        @Test
        @DisplayName("caso 9 a: misma materia, misma comisión, se pisan: rechazado")
        void sameSubjectSameCommissionOverlapIsRejected() {
            givenExisting(SUBJECT, 7L, 12, 14);

            assertThatThrownBy(() -> handler.validate(dto(freeFormItemAtHours(7L, 13, 15))))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("Esa comisión ya tiene otro pedido en la misma fecha y horario");
        }

        @Test
        @DisplayName("caso 9 b: otra materia, misma comisión, se pisan: aceptado")
        void otherSubjectSameCommissionOverlapIsAccepted() {
            givenExisting(OTHER_SUBJECT, 7L, 12, 14);

            assertThatCode(() -> handler.validate(dto(freeFormItemAtHours(7L, 12, 14)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("caso 9 c: existente 'todas' de otra materia, nuevo con comisión puntual: aceptado")
        void otherSubjectExistingAllIsAccepted() {
            givenExisting(OTHER_SUBJECT, null, 12, 14);

            assertThatCode(() -> handler.validate(dto(freeFormItemAtHours(7L, 12, 14)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("caso 9 d: existente 'todas' (dos comisiones) de otra materia, nuevo de otra comisión: aceptado")
        void otherSubjectExistingAllWithDifferentCommissionIsAccepted() {
            givenExisting(OTHER_SUBJECT, null, 12, 14);

            assertThatCode(() -> handler.validate(dto(freeFormItemAtHours(9L, 12, 14)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("caso 9 e: misma materia, los dos 'todas', se pisan: rechazado")
        void sameSubjectBothAllIsRejected() {
            givenExisting(SUBJECT, null, 12, 14);

            assertThatThrownBy(() -> handler.validate(dto(freeFormItemAtHours(null, 12, 14))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("caso 9 e': misma materia, existente 'todas' y nuevo con comisión puntual: rechazado")
        void sameSubjectExistingAllIsRejected() {
            givenExisting(SUBJECT, null, 12, 14);

            assertThatThrownBy(() -> handler.validate(dto(freeFormItemAtHours(7L, 13, 15))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("caso 9 f: borde exacto, no se pisan: aceptado")
        void touchingBordersAreAccepted() {
            givenExisting(SUBJECT, 7L, 12, 14);

            assertThatCode(() -> handler.validate(dto(freeFormItemAtHours(7L, 14, 16)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("misma materia, distinta comisión puntual, se pisan: aceptado")
        void sameSubjectDifferentCommissionIsAccepted() {
            givenExisting(SUBJECT, 7L, 12, 14);

            assertThatCode(() -> handler.validate(dto(freeFormItemAtHours(8L, 12, 14)))).doesNotThrowAnyException();
        }

        private void givenExisting(Long subjectId, Long commissionId, int fromHour, int toHour) {
            RoomRequest request = RoomRequest.builder().subjectId(subjectId).build();
            RoomRequestItem existing = RoomRequestItem.builder()
                    .request(request)
                    .commissionId(commissionId)
                    .date(LocalDate.now().plusDays(7))
                    .startTime(LocalTime.of(fromHour, 0))
                    .duration(Duration.ofHours(toHour - fromHour))
                    .build();
            when(itemRepository.findActiveOffScheduleItemsByDateIn(any())).thenReturn(List.of(existing));
        }

        private FreeFormItemDto freeFormItemAtHours(Long commissionId, int fromHour, int toHour) {
            return freeFormItemAt(commissionId, LocalDate.now().plusDays(7),
                    LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
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

        @Test
        @DisplayName("menos de 2hs de anticipación: rechazado")
        void requiresAdvanceNotice() {
            assertThatThrownBy(() -> handler.validate(dto(
                    freeFormItemAt(null, LocalDate.now(), LocalTime.now().plusMinutes(30), LocalTime.now().plusMinutes(90)))))
                    .isInstanceOf(InvalidRoomRequestException.class);
        }
    }

    @Nested
    @DisplayName("FinalExamHandler")
    class Final {

        private FinalExamHandler handler;

        @BeforeEach
        void setUp() {
            handler = new FinalExamHandler(academicReference, classroomReference, classSchedule, eventSchedule);
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
        @DisplayName("menos de 2hs de anticipación: rechazado")
        void requiresAdvanceNotice() {
            FreeFormItemDto tooSoon = new FreeFormItemDto(null, LocalDate.now(),
                    LocalTime.now().plusMinutes(30), LocalTime.now().plusMinutes(90),
                    35, 1, false, false, null, null, null, null, List.of());
            assertThatThrownBy(() -> handler.validate(dto(tooSoon)))
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
            conference = new ConferenceHandler(academicReference, classroomReference, classSchedule, eventSchedule);
            other = new OtherHandler(academicReference, classroomReference, classSchedule, eventSchedule);
        }

        @Test
        @DisplayName("conferencia: materia opcional; requiresExamUsers debe venir null")
        void conference() {
            CreateConferenceDto ok = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, List.of(freeFormItem(null)));
            assertThatCode(() -> conference.validate(ok)).doesNotThrowAnyException();
            verify(academicReference).validateOptionalSubject(null);

            FreeFormItemDto withExamUsers = new FreeFormItemDto(null, LocalDate.now().plusDays(7),
                    LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1, false, true, 5, true, null, null, List.of());
            CreateConferenceDto bad = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, List.of(withExamUsers));
            assertThatThrownBy(() -> conference.validate(bad)).isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("otro: cada ítem exige observations")
        void other() {
            CreateOtherDto ok = new CreateOtherDto(RoomRequestType.OTHER, requester(), null, List.of(withObservations(null)));
            assertThatCode(() -> other.validate(ok)).doesNotThrowAnyException();

            CreateOtherDto bad = new CreateOtherDto(RoomRequestType.OTHER, requester(), null,
                    List.of(freeFormItem(null)));
            assertThatThrownBy(() -> other.validate(bad))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("observations");
        }

        @Test
        @DisplayName("conferencia y otro no llevan comisión: un ítem que la declara se rechaza")
        void commissionIsNotAllowed() {
            CreateConferenceDto conferenceWithCommission = new CreateConferenceDto(RoomRequestType.CONFERENCE,
                    requester(), null, List.of(freeFormItem(COMMISSION)));
            assertThatThrownBy(() -> conference.validate(conferenceWithCommission))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("no llevan comisión");

            CreateOtherDto otherWithCommission = new CreateOtherDto(RoomRequestType.OTHER,
                    requester(), null, List.of(withObservations(COMMISSION)));
            assertThatThrownBy(() -> other.validate(otherWithCommission))
                    .isInstanceOf(InvalidRoomRequestException.class)
                    .hasMessageContaining("no llevan comisión");
        }

        @Test
        @DisplayName("hoy con horario ya pasado: rechazado en conferencia y en otro")
        void todayButAlreadyPast() {
            FreeFormItemDto pastToday = new FreeFormItemDto(null, LocalDate.now(),
                    LocalTime.MIDNIGHT, LocalTime.MAX,
                    35, 1, false, false, null, null, null, null, List.of());
            CreateConferenceDto conferenceDto = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, List.of(pastToday));
            assertThatThrownBy(() -> conference.validate(conferenceDto)).isInstanceOf(InvalidRoomRequestException.class);

            FreeFormItemDto pastTodayWithObservations = new FreeFormItemDto(null, LocalDate.now(),
                    LocalTime.MIDNIGHT, LocalTime.MAX,
                    35, 1, false, false, null, null, null, "Grabación de video", List.of());
            CreateOtherDto otherDto = new CreateOtherDto(RoomRequestType.OTHER, requester(), null,
                    List.of(pastTodayWithObservations));
            assertThatThrownBy(() -> other.validate(otherDto)).isInstanceOf(InvalidRoomRequestException.class);
        }

        @Test
        @DisplayName("hoy con horario que todavía no llegó: permitido")
        void todayButNotYetPast() {
            FreeFormItemDto futureToday = new FreeFormItemDto(null, LocalDate.now(),
                    LocalTime.MAX, LocalTime.MAX,
                    35, 1, false, false, null, null, null, null, List.of());
            CreateConferenceDto conferenceDto = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(),
                    null, List.of(futureToday));
            assertThatCode(() -> conference.validate(conferenceDto)).doesNotThrowAnyException();
        }

        private FreeFormItemDto withObservations(Long commissionId) {
            return new FreeFormItemDto(commissionId, LocalDate.now().plusDays(7),
                    LocalTime.of(10, 0), LocalTime.of(12, 0), 35, 1, false, false, null, null, null,
                    "Grabación de video", List.of());
        }
    }

    @Nested
    @DisplayName("ventana horaria (events.hours): los cuatro tipos de horario libre la validan al crear")
    class BusinessHours {

        private final LocalTime tooEarly = LocalTime.of(7, 30);
        private final LocalTime tooLate = LocalTime.of(23, 30);

        private FreeFormItemDto itemAt(LocalTime start, LocalTime end, String observations) {
            return new FreeFormItemDto(null, LocalDate.now().plusDays(7), start, end, 35, 1, false, false, null, null,
                    null, observations, List.of());
        }

        private void windowRejects(LocalTime start, LocalTime end) {
            org.mockito.Mockito.doThrow(new InvalidEventScheduleException("fuera de la ventana permitida"))
                    .when(eventSchedule).validateBusinessHours(start, end);
        }

        @Test
        @DisplayName("conferencia 07:30-09:00: rechazada con el error de la ventana, sin llegar a materia")
        void conferenceBeforeStart() {
            windowRejects(tooEarly, LocalTime.of(9, 0));
            CreateConferenceDto dto = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(), null,
                    List.of(itemAt(tooEarly, LocalTime.of(9, 0), null)));

            assertThatThrownBy(() -> new ConferenceHandler(academicReference, classroomReference, classSchedule,
                    eventSchedule).validate(dto)).isInstanceOf(InvalidEventScheduleException.class);
            verifyNoInteractions(academicReference);
        }

        @Test
        @DisplayName("otro 22:00-23:30: rechazado")
        void otherAfterEnd() {
            windowRejects(LocalTime.of(22, 0), tooLate);
            CreateOtherDto dto = new CreateOtherDto(RoomRequestType.OTHER, requester(), null,
                    List.of(itemAt(LocalTime.of(22, 0), tooLate, "detalle")));

            assertThatThrownBy(() -> new OtherHandler(academicReference, classroomReference, classSchedule,
                    eventSchedule).validate(dto)).isInstanceOf(InvalidEventScheduleException.class);
        }

        @Test
        @DisplayName("final 07:30-10:00: rechazado")
        void finalBeforeStart() {
            windowRejects(tooEarly, LocalTime.of(10, 0));
            CreateFinalExamDto dto = new CreateFinalExamDto(RoomRequestType.FINAL_EXAM, requester(), SUBJECT,
                    List.of(itemAt(tooEarly, LocalTime.of(10, 0), null)));

            assertThatThrownBy(() -> new FinalExamHandler(academicReference, classroomReference, classSchedule,
                    eventSchedule).validate(dto)).isInstanceOf(InvalidEventScheduleException.class);
        }

        @Test
        @DisplayName("parcial fuera de horario 22:00-23:30: rechazado antes de consultar superposiciones")
        void partialOffScheduleAfterEnd() {
            windowRejects(LocalTime.of(22, 0), tooLate);
            CreatePartialExamOffScheduleDto dto = new CreatePartialExamOffScheduleDto(
                    RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE, requester(), SUBJECT,
                    List.of(itemAt(LocalTime.of(22, 0), tooLate, null)));

            assertThatThrownBy(() -> new PartialExamOffScheduleHandler(academicReference, classroomReference,
                    classSchedule, itemRepository, eventSchedule).validate(dto))
                    .isInstanceOf(InvalidEventScheduleException.class);
            verifyNoInteractions(itemRepository);
        }

        @Test
        @DisplayName("08:00-23:00 (los bordes de la ventana): se acepta y se consulta con esos valores")
        void edgesAreAccepted() {
            LocalTime start = LocalTime.of(8, 0);
            LocalTime end = LocalTime.of(23, 0);
            CreateConferenceDto dto = new CreateConferenceDto(RoomRequestType.CONFERENCE, requester(), null,
                    List.of(itemAt(start, end, null)));

            assertThatCode(() -> new ConferenceHandler(academicReference, classroomReference, classSchedule,
                    eventSchedule).validate(dto)).doesNotThrowAnyException();
            verify(eventSchedule).validateBusinessHours(start, end);
        }
    }
}
