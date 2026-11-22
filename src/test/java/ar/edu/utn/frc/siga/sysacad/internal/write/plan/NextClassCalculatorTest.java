package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSysacadRefDto;
import ar.edu.utn.frc.siga.sysacad.internal.model.DestinationNotSendableReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoNextClass;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoRoom;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NoRoomAfterCancel;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NotSendable;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.NotifyOnly;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Send;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Unchanged;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.NextClassDecision.Waiting;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("NextClassCalculator")
class NextClassCalculatorTest {

    private static final int BUILDING = 1;
    private static final LocalDate D1 = LocalDate.of(2026, 3, 2);
    private static final LocalDate D2 = LocalDate.of(2026, 3, 9);
    private static final LocalDate D3 = LocalDate.of(2026, 3, 16);
    private static final LocalDate D4 = LocalDate.of(2026, 3, 23);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(10, 15);

    private static SysacadSchedule schedule(Integer knownBuilding, Integer knownRoom, LocalTime endTime) {
        return SysacadSchedule.builder()
                .knownBuilding(knownBuilding)
                .knownRoom(knownRoom)
                .endTime(endTime)
                .build();
    }

    private static SysacadSchedule known(int room) {
        return schedule(BUILDING, room, END);
    }

    private static OccurrenceSlotDto slot(long occurrenceId, long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, START, END, OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private static OccurrenceSlotDto slot(long occurrenceId, LocalDate date) {
        return slot(occurrenceId, 1L, date);
    }

    private static OccurrenceSlotDto slotWithStatus(long occurrenceId, LocalDate date, OccurrenceStatus status) {
        return new OccurrenceSlotDto(occurrenceId, 1L, date, START, END, status, 30);
    }

    private static OccurrenceSlotDto mirror(long occurrenceId, long mirrorOf, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, 1L, date, LocalTime.of(7, 45), END, OccurrenceStatus.NEEDS_ROOM,
                30, 2, null, null, null, mirrorOf);
    }

    private static ClassroomSysacadRefDto ref(int room) {
        return new ClassroomSysacadRefDto((long) room, room, BUILDING, true, true, true);
    }

    private static Map<Long, ClassroomSysacadRefDto> refs(int... rooms) {
        Map<Long, ClassroomSysacadRefDto> refs = new HashMap<>();
        for (int room : rooms) {
            refs.put((long) room, ref(room));
        }
        return refs;
    }

    private static Map<Long, Long> rooms(Long... roomPerOccurrence) {
        Map<Long, Long> byOccurrence = new HashMap<>();
        for (int i = 0; i < roomPerOccurrence.length; i++) {
            if (roomPerOccurrence[i] != null) {
                byOccurrence.put((long) (i + 1), roomPerOccurrence[i]);
            }
        }
        return byOccurrence;
    }

    private static NextClassDecision calc(SysacadSchedule schedule, List<OccurrenceSlotDto> slots,
            Map<Long, Long> classroomByOccurrence, LocalDateTime now) {
        return calc(schedule, slots, classroomByOccurrence, Map.of(), refs(227, 228, 229, 230), now);
    }

    private static NextClassDecision calc(SysacadSchedule schedule, List<OccurrenceSlotDto> slots,
            Map<Long, Long> classroomByOccurrence, Map<Long, List<Long>> mirrorClassrooms,
            Map<Long, ClassroomSysacadRefDto> refs, LocalDateTime now) {
        return NextClassCalculator.calculate(schedule, slots, classroomByOccurrence, mirrorClassrooms, refs,
                Map.of(), Set.of(), now);
    }

    private static Send send(int building, int room, long occurrenceId, LocalDate date) {
        return new Send(building, room, occurrenceId, date, START, END, List.of(new RoomRef(building, room)));
    }

    @Test
    @DisplayName("Sin cambios 228, 228, 228: Unchanged antes y después de cada clase")
    void unchanged() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(228L, 228L, 228L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(7, 0))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms, D1.atTime(11, 0))).isEqualTo(new Unchanged(2L, D2));
        assertThat(calc(known(228), slots, rooms, D2.atTime(11, 0))).isEqualTo(new Unchanged(3L, D3));
    }

    @Test
    @DisplayName("Puntual 228, 229, 228: se queda en 228 hasta terminar la clase y pasa a 229 al terminar")
    void singleChangeGoesToNewRoomWhenCurrentClassEnds() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(228L, 229L, 228L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(8, 30))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms, D1.atTime(10, 14))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms, D1.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 229, 2L, D2));
    }

    @Test
    @DisplayName("Puntual 228, 229, 228: con 229 conocido, vuelve a 228 al terminar la clase en 229")
    void singleChangeReturnsToBaseRoomAfterTheChangedClass() {
        List<OccurrenceSlotDto> slots = List.of(slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = Map.of(2L, 229L, 3L, 228L);

        assertThat(calc(known(229), slots, rooms, D2.atTime(10, 14))).isEqualTo(new Unchanged(2L, D2));
        assertThat(calc(known(229), slots, rooms, D2.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 228, 3L, D3));
    }

    @Test
    @DisplayName("Puntual en la próxima clase 229, 228, 228: envía 229 de entrada y vuelve a 228 al terminar")
    void singleChangeOnNextClassSendsImmediately() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(229L, 228L, 228L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(7, 0)))
                .isEqualTo(send(BUILDING, 229, 1L, D1));
        assertThat(calc(known(229), slots, rooms, D1.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 228, 2L, D2));
    }

    @Test
    @DisplayName("Rango 228, 229, 229, 228: pasa a 229 al terminar la primera, sigue en 229 y vuelve a 228 al terminar la última en 229")
    void rangeChange() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3), slot(4, D4));
        Map<Long, Long> rooms = rooms(228L, 229L, 229L, 228L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 229, 2L, D2));
        assertThat(calc(known(229), slots, rooms, D2.atTime(10, 15))).isEqualTo(new Unchanged(3L, D3));
        assertThat(calc(known(229), slots, rooms, D3.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 228, 4L, D4));
    }

    @Test
    @DisplayName("Desde una fecha 228, 229, 229: pasa a 229 al terminar la primera y ahí queda hasta que no haya más clases")
    void changeFromADate() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(228L, 229L, 229L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 229, 2L, D2));
        assertThat(calc(known(229), slots, rooms, D2.atTime(10, 15))).isEqualTo(new Unchanged(3L, D3));
        assertThat(calc(known(229), slots, rooms, D3.atTime(10, 15))).isEqualTo(new NoNextClass());
    }

    @Test
    @DisplayName("Materia anual con cambio solo en el segundo cuatrimestre: la próxima clase pasa de un evento al otro")
    void annualSubjectChangeInSecondTermOnly() {
        LocalDate lastOfFirstTerm = LocalDate.of(2026, 7, 6);
        LocalDate firstOfSecondTerm = LocalDate.of(2026, 8, 3);
        List<OccurrenceSlotDto> slots = List.of(
                slot(3, 2L, firstOfSecondTerm), slot(4, 2L, firstOfSecondTerm.plusWeeks(1)),
                slot(1, 1L, lastOfFirstTerm.minusWeeks(1)), slot(2, 1L, lastOfFirstTerm));
        Map<Long, Long> rooms = rooms(228L, 228L, 229L, 229L);

        assertThat(calc(known(228), slots, rooms, lastOfFirstTerm.atTime(10, 14)))
                .isEqualTo(new Unchanged(2L, lastOfFirstTerm));
        assertThat(calc(known(228), slots, rooms, lastOfFirstTerm.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 229, 3L, firstOfSecondTerm));
    }

    @Test
    @DisplayName("Materia anual con cambio solo en el primer cuatrimestre: tras su última clase vuelve a 228 con la del segundo evento")
    void annualSubjectChangeInFirstTermOnly() {
        LocalDate lastOfFirstTerm = LocalDate.of(2026, 7, 6);
        LocalDate firstOfSecondTerm = LocalDate.of(2026, 8, 3);
        List<OccurrenceSlotDto> slots = List.of(
                slot(1, 1L, lastOfFirstTerm.minusWeeks(1)), slot(2, 1L, lastOfFirstTerm),
                slot(3, 2L, firstOfSecondTerm));
        Map<Long, Long> rooms = rooms(228L, 229L, 228L);

        assertThat(calc(known(229), slots, rooms, lastOfFirstTerm.atTime(10, 15)))
                .isEqualTo(send(BUILDING, 228, 3L, firstOfSecondTerm));
    }

    @Test
    @DisplayName("Semana sin clase: un feriado se saltea y toma la siguiente clase que exista")
    void holidayIsSkipped() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(228L, 229L, 228L);

        NextClassDecision decision = NextClassCalculator.calculate(known(228), slots, rooms, Map.of(), refs(228, 229),
                Map.of(), Set.of(D2), D1.atTime(11, 0));

        assertThat(decision).isEqualTo(new Unchanged(3L, D3));
    }

    @Test
    @DisplayName("Una clase sigue siendo la próxima hasta HoraFin aunque su endTime de SIGA ya pasó, y a HoraFin exacta ya no cuenta")
    void classLastsUntilScheduleEndTime() {
        SysacadSchedule schedule = schedule(BUILDING, 228, LocalTime.of(10, 25));
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2));
        Map<Long, Long> rooms = rooms(229L, 228L);

        assertThat(calc(schedule, slots, rooms, D1.atTime(10, 20))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(schedule, slots, rooms, D1.atTime(10, 24, 59))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(schedule, slots, rooms, D1.atTime(10, 25))).isEqualTo(new Unchanged(2L, D2));
    }

    @Test
    @DisplayName("Con hora_fin nula usa el endTime del slot")
    void nullScheduleEndTimeFallsBackToSlotEndTime() {
        SysacadSchedule schedule = schedule(BUILDING, 228, null);
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2));
        Map<Long, Long> rooms = rooms(229L, 228L);

        assertThat(calc(schedule, slots, rooms, D1.atTime(10, 14))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(schedule, slots, rooms, D1.atTime(10, 15))).isEqualTo(new Unchanged(2L, D2));
    }

    @Test
    @DisplayName("Una clase con espejo manda el aula de la principal, aunque el espejo empiece antes")
    void mirrorIsIgnored() {
        List<OccurrenceSlotDto> slots = List.of(mirror(11, 1, D1), slot(1, D1));
        Map<Long, Long> rooms = Map.of(1L, 229L, 11L, 230L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(7, 0)))
                .isEqualTo(send(BUILDING, 229, 1L, D1));
        assertThat(calc(known(229), slots, rooms, D1.atTime(7, 0))).isEqualTo(new Unchanged(1L, D1));
    }

    @Test
    @DisplayName("Un ítem PENDING frena el envío aunque la clase esté por empezar")
    void pendingItemBlocksEvenIfClassIsAboutToStart() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1));
        Map<Long, OccurrenceRequestStateDto> states =
                Map.of(1L, new OccurrenceRequestStateDto(1L, 77L, OccurrenceRequestState.PENDING));

        NextClassDecision decision = NextClassCalculator.calculate(known(228), slots, rooms(229L), Map.of(),
                refs(228, 229), states, Set.of(), D1.atTime(7, 59));

        assertThat(decision).isEqualTo(new Waiting(1L, 77L, D1, START)).isNotInstanceOf(Send.class);
    }

    @Test
    @DisplayName("Un ítem ACCEPTED envía el aula asignada")
    void acceptedItemSends() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1));
        Map<Long, OccurrenceRequestStateDto> states =
                Map.of(1L, new OccurrenceRequestStateDto(1L, 77L, OccurrenceRequestState.ACCEPTED));

        NextClassDecision decision = NextClassCalculator.calculate(known(228), slots, rooms(229L), Map.of(),
                refs(228, 229), states, Set.of(), D1.atTime(7, 59));

        assertThat(decision).isEqualTo(send(BUILDING, 229, 1L, D1));
    }

    @Test
    @DisplayName("NEEDS_ROOM sin asignación da NoRoom y nunca Send")
    void needsRoomWithoutAllocationIsNoRoom() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1));

        NextClassDecision decision = calc(known(228), slots, Map.of(), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NoRoom(1L, D1)).isNotInstanceOf(Send.class);
    }

    @Test
    @DisplayName("NEEDS_ROOM sin asignación y con ítem CANCELLED da NoRoomAfterCancel con el ítem")
    void needsRoomAfterCancelledItem() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1));
        Map<Long, OccurrenceRequestStateDto> states =
                Map.of(1L, new OccurrenceRequestStateDto(1L, 88L, OccurrenceRequestState.CANCELLED));

        NextClassDecision decision = NextClassCalculator.calculate(known(228), slots, Map.of(), Map.of(), refs(228), states,
                Set.of(), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NoRoomAfterCancel(1L, 88L, D1));
    }

    @Test
    @DisplayName("ROOM_RELEASED envía el edificio conocido con aula 999")
    void roomReleasedSendsReleasedRoom() {
        List<OccurrenceSlotDto> slots = List.of(slotWithStatus(1, D1, OccurrenceStatus.ROOM_RELEASED));

        assertThat(calc(known(228), slots, Map.of(), D1.atTime(7, 0)))
                .isEqualTo(send(BUILDING, 999, 1L, D1));
    }

    @Test
    @DisplayName("ROOM_RELEASED con 999 ya conocido da Unchanged")
    void roomReleasedWithKnown999IsUnchanged() {
        List<OccurrenceSlotDto> slots = List.of(slotWithStatus(1, D1, OccurrenceStatus.ROOM_RELEASED));

        assertThat(calc(known(999), slots, Map.of(), D1.atTime(7, 0))).isEqualTo(new Unchanged(1L, D1));
    }

    @Test
    @DisplayName("ROOM_RELEASED sin edificio conocido da NoRoom")
    void roomReleasedWithoutKnownBuildingIsNoRoom() {
        List<OccurrenceSlotDto> slots = List.of(slotWithStatus(1, D1, OccurrenceStatus.ROOM_RELEASED));

        assertThat(calc(schedule(null, 228, END), slots, Map.of(), D1.atTime(7, 0)))
                .isEqualTo(new NoRoom(1L, D1));
    }

    @Test
    @DisplayName("Sin próxima clase da NoNextClass, con lista vacía o con todas las clases terminadas")
    void noNextClass() {
        assertThat(calc(known(228), List.of(), Map.of(), D1.atTime(7, 0))).isEqualTo(new NoNextClass());
        assertThat(calc(known(228), List.of(slot(1, D1)), rooms(228L), D1.atTime(10, 15)))
                .isEqualTo(new NoNextClass());
    }

    @Test
    @DisplayName("Si la próxima clase no tiene aula no avanza a la siguiente, aunque esa coincida con el valor conocido")
    void nextClassWithoutRoomDoesNotAdvance() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2));

        NextClassDecision decision = calc(known(228), slots, Map.of(2L, 228L), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NoRoom(1L, D1));
    }

    static Stream<Arguments> invalidDestinations() {
        return Stream.of(
                Arguments.of(new ClassroomSysacadRefDto(229L, 229, BUILDING, true, true, false),
                        DestinationNotSendableReason.BUILDING_NOT_IN_SYSACAD),
                Arguments.of(new ClassroomSysacadRefDto(229L, 229, BUILDING, false, true, true),
                        DestinationNotSendableReason.CLASSROOM_NOT_IN_SYSACAD),
                Arguments.of(new ClassroomSysacadRefDto(229L, 229, BUILDING, true, false, true),
                        DestinationNotSendableReason.CLASSROOM_DISABLED_IN_SYSACAD),
                Arguments.of(new ClassroomSysacadRefDto(229L, null, BUILDING, true, true, true),
                        DestinationNotSendableReason.CLASSROOM_WITHOUT_NUMBER),
                Arguments.of(new ClassroomSysacadRefDto(229L, 229, null, true, true, true),
                        DestinationNotSendableReason.BUILDING_WITHOUT_CODE),
                Arguments.of(new ClassroomSysacadRefDto(229L, 999, BUILDING, true, true, true),
                        DestinationNotSendableReason.CLASSROOM_RESERVED_NUMBER),
                Arguments.of(new ClassroomSysacadRefDto(229L, 0, BUILDING, true, true, true),
                        DestinationNotSendableReason.CLASSROOM_RESERVED_NUMBER),
                Arguments.of(new ClassroomSysacadRefDto(229L, -1, BUILDING, true, true, true),
                        DestinationNotSendableReason.CLASSROOM_RESERVED_NUMBER),
                Arguments.of(null, DestinationNotSendableReason.CLASSROOM_NOT_FOUND));
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidDestinations")
    @DisplayName("Un destino no enviable da NotSendable con su motivo")
    void invalidDestinationIsNotSendable(ClassroomSysacadRefDto destination, DestinationNotSendableReason reason) {
        Map<Long, ClassroomSysacadRefDto> refs = new HashMap<>();
        if (destination != null) {
            refs.put(229L, destination);
        }

        NextClassDecision decision = NextClassCalculator.calculate(known(228), List.of(slot(1, D1)), rooms(229L), Map.of(),
                refs, Map.of(), Set.of(), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NotSendable(1L, D1, reason));
    }

    @Test
    @DisplayName("El mismo número de aula en otro edificio es un cambio y da Send con el edificio nuevo")
    void sameRoomNumberInAnotherBuildingIsSent() {
        Map<Long, ClassroomSysacadRefDto> refs = Map.of(500L, new ClassroomSysacadRefDto(500L, 228, 2, true, true, true));

        NextClassDecision decision = NextClassCalculator.calculate(known(228), List.of(slot(1, D1)), rooms(500L), Map.of(),
                refs, Map.of(), Set.of(), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(send(2, 228, 1L, D1));
    }

    @Test
    @DisplayName("Clase en curso con otra aula en SIGA: Unchanged con now igual al inicio, en curso, y sin saltar a la siguiente")
    void startedClassWithDifferentRoomIsUnchanged() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2));
        Map<Long, Long> rooms = rooms(229L, 228L);

        assertThat(calc(known(228), slots, rooms, D1.atTime(START))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms, D1.atTime(9, 0))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms, D1.atTime(START).minusSeconds(1)))
                .isEqualTo(send(BUILDING, 229, 1L, D1));
    }

    @Test
    @DisplayName("Clase en curso: al llegar a HoraFin ya cuenta la siguiente y se envía su aula")
    void atScheduleEndTimeNextClassCounts() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2));
        Map<Long, Long> rooms = rooms(229L, 230L);

        assertThat(calc(known(229), slots, rooms, D1.atTime(END).minusSeconds(1)))
                .isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(229), slots, rooms, D1.atTime(END)))
                .isEqualTo(send(BUILDING, 230, 2L, D2));
    }

    @Test
    @DisplayName("ROOM_RELEASED con la clase ya empezada da Unchanged y no salta a la siguiente")
    void roomReleasedWithStartedClassIsUnchanged() {
        List<OccurrenceSlotDto> slots = List.of(slotWithStatus(1, D1, OccurrenceStatus.ROOM_RELEASED), slot(2, D2));

        assertThat(calc(known(228), slots, rooms(null, 228L), D1.atTime(START))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms(null, 228L), D1.atTime(9, 0))).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(228), slots, rooms(null, 228L), D1.atTime(START).minusSeconds(1)))
                .isEqualTo(send(BUILDING, 999, 1L, D1));
    }

    @Test
    @DisplayName("Waiting, NoRoom y NotSendable no cambian aunque la clase ya haya empezado")
    void nonSendDecisionsIgnoreStartedClass() {
        LocalDateTime inProgress = D1.atTime(9, 0);
        Map<Long, OccurrenceRequestStateDto> pending =
                Map.of(1L, new OccurrenceRequestStateDto(1L, 77L, OccurrenceRequestState.PENDING));

        assertThat(NextClassCalculator.calculate(known(228), List.of(slot(1, D1)), rooms(229L), Map.of(),
                refs(228, 229), pending, Set.of(), inProgress)).isEqualTo(new Waiting(1L, 77L, D1, START));
        assertThat(calc(known(228), List.of(slot(1, D1)), Map.of(), inProgress)).isEqualTo(new NoRoom(1L, D1));
        assertThat(NextClassCalculator.calculate(known(228), List.of(slot(1, D1)), rooms(500L), Map.of(),
                Map.of(500L, new ClassroomSysacadRefDto(500L, 0, BUILDING, true, true, true)), Map.of(), Set.of(), inProgress))
                .isEqualTo(new NotSendable(1L, D1, DestinationNotSendableReason.CLASSROOM_RESERVED_NUMBER));
    }

    @Test
    @DisplayName("Cambio directo aplicado con una clase en curso: el aula conocida nueva no genera un Send que la revierta")
    void directChangeWithClassInProgressDoesNotRevert() {
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), slot(2, D2), slot(3, D3));
        Map<Long, Long> rooms = rooms(228L, 229L, 229L);
        LocalDateTime inProgress = D1.atTime(9, 0);

        assertThat(calc(known(229), slots, rooms, inProgress)).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(known(229), slots, rooms, D1.atTime(END))).isEqualTo(new Unchanged(2L, D2));
    }

    private static SysacadSchedule announced(int room, String announcedRooms) {
        return SysacadSchedule.builder()
                .knownBuilding(BUILDING)
                .knownRoom(room)
                .announcedRooms(announcedRooms)
                .endTime(END)
                .build();
    }

    private static List<RoomRef> roomRefs(int... rooms) {
        return java.util.Arrays.stream(rooms).mapToObj(room -> new RoomRef(BUILDING, room)).toList();
    }

    private static NextClassDecision calcWithMirrors(SysacadSchedule schedule, Map<Long, Long> classrooms,
            Map<Long, List<Long>> mirrors, LocalDateTime now) {
        return calc(schedule, List.of(slot(1, D1), mirror(11, 1, D1)), classrooms, mirrors,
                refs(227, 228, 229, 230), now);
    }

    @Test
    @DisplayName("Con espejo y sin aviso previo, la principal igual a la conocida da NotifyOnly con ambas aulas")
    void mirrorAddedWithoutAnnouncementNotifiesOnly() {
        NextClassDecision decision = calcWithMirrors(known(228), Map.of(1L, 228L), Map.of(1L, List.of(229L)),
                D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NotifyOnly(1L, D1, roomRefs(228, 229)));
    }

    @Test
    @DisplayName("Conjunto ya avisado: Unchanged")
    void alreadyAnnouncedSetIsUnchanged() {
        NextClassDecision decision = calcWithMirrors(announced(228, "1-228,1-229"), Map.of(1L, 228L),
                Map.of(1L, List.of(229L)), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Unchanged(1L, D1));
    }

    @Test
    @DisplayName("Reordenar principal y espejo no cuenta como cambio")
    void reorderedSetIsUnchanged() {
        NextClassDecision decision = calcWithMirrors(announced(229, "1-228,1-229"), Map.of(1L, 229L),
                Map.of(1L, List.of(228L)), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Unchanged(1L, D1));
    }

    @Test
    @DisplayName("Se sacó el espejo: NotifyOnly con la principal sola")
    void mirrorRemovedNotifiesOnly() {
        NextClassDecision decision = calcWithMirrors(announced(228, "1-228,1-229"), Map.of(1L, 228L), Map.of(),
                D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NotifyOnly(1L, D1, roomRefs(228)));
    }

    @Test
    @DisplayName("Cambia solo el aula del espejo: NotifyOnly con el conjunto nuevo")
    void mirrorRoomChangedNotifiesOnly() {
        NextClassDecision decision = calcWithMirrors(announced(228, "1-228,1-229"), Map.of(1L, 228L),
                Map.of(1L, List.of(230L)), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new NotifyOnly(1L, D1, roomRefs(228, 230)));
    }

    @Test
    @DisplayName("Cambia la principal con espejos: Send con la principal primero y los espejos")
    void principalChangedSendsWithMirrors() {
        NextClassDecision decision = calcWithMirrors(known(228), Map.of(1L, 229L), Map.of(1L, List.of(230L)),
                D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Send(BUILDING, 229, 1L, D1, START, END, roomRefs(229, 230)));
    }

    @Test
    @DisplayName("Send lleva los espejos en el orden recibido, después de la principal")
    void sendKeepsMirrorOrder() {
        NextClassDecision decision = calcWithMirrors(known(228), Map.of(1L, 227L),
                Map.of(1L, List.of(230L, 229L)), D1.atTime(7, 0));

        assertThat(decision).isInstanceOfSatisfying(Send.class,
                send -> assertThat(send.rooms()).containsExactlyElementsOf(roomRefs(227, 230, 229)));
    }

    @Test
    @DisplayName("Un espejo sin asignación no entra al conjunto: Unchanged")
    void mirrorWithoutAllocationIsIgnored() {
        NextClassDecision decision = calcWithMirrors(known(228), Map.of(1L, 228L), Map.of(), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Unchanged(1L, D1));
    }

    @Test
    @DisplayName("Un espejo cuyo ref no tiene número queda fuera del conjunto")
    void mirrorWithoutNumberIsLeftOut() {
        Map<Long, ClassroomSysacadRefDto> refs = refs(228);
        refs.put(500L, new ClassroomSysacadRefDto(500L, null, BUILDING, true, true, true));
        List<OccurrenceSlotDto> slots = List.of(slot(1, D1), mirror(11, 1, D1), mirror(12, 1, D1));

        NextClassDecision decision = calc(known(228), slots, Map.of(1L, 228L),
                Map.of(1L, List.of(500L, 229L)), refs, D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Unchanged(1L, D1));
        assertThat(calc(announced(228, "1-228"), slots, Map.of(1L, 228L), Map.of(1L, List.of(500L)), refs,
                D1.atTime(7, 0))).isEqualTo(new Unchanged(1L, D1));
        refs.put(229L, ref(229));
        assertThat(calc(known(228), slots, Map.of(1L, 228L), Map.of(1L, List.of(500L, 229L)), refs,
                D1.atTime(7, 0))).isEqualTo(new NotifyOnly(1L, D1, roomRefs(228, 229)));
    }

    @Test
    @DisplayName("NotifyOnly no sale si la clase ya empezó, y sí un segundo antes")
    void notifyOnlyDoesNotLeaveOnceClassStarted() {
        Map<Long, List<Long>> mirrors = Map.of(1L, List.of(229L));

        assertThat(calcWithMirrors(known(228), Map.of(1L, 228L), mirrors, D1.atTime(START)))
                .isEqualTo(new Unchanged(1L, D1));
        assertThat(calcWithMirrors(known(228), Map.of(1L, 228L), mirrors, D1.atTime(START).minusSeconds(1)))
                .isEqualTo(new NotifyOnly(1L, D1, roomRefs(228, 229)));
    }

    @Test
    @DisplayName("Principal ROOM_RELEASED: Send con 999 y solo esa aula, sin mirar los espejos")
    void releasedPrincipalSendsReleasedRoomIgnoringMirrors() {
        List<OccurrenceSlotDto> slots = List.of(slotWithStatus(1, D1, OccurrenceStatus.ROOM_RELEASED),
                mirror(11, 1, D1));

        NextClassDecision decision = calc(known(228), slots, Map.of(), Map.of(1L, List.of(229L)),
                refs(228, 229), D1.atTime(7, 0));

        assertThat(decision).isEqualTo(new Send(BUILDING, 999, 1L, D1, START, END, roomRefs(999)));
    }
}
