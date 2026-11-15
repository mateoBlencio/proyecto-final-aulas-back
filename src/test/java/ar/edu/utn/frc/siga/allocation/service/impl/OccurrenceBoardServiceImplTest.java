package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.NotPermittedIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardDateDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardEventDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceBoardSlotDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OverlapIssueDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedIssueDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceIssueType;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;
import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.recurring;
import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceBoardServiceImpl")
class OccurrenceBoardServiceImplTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate DATE = TODAY.plusYears(1);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final Set<OccurrenceIssueType> NO_FILTER = null;

    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;
    @Mock
    private AcademicPeriodService academicPeriodService;

    private OccurrenceBoardServiceImpl service;

    private final List<OccurrenceSlotDto> slots = new ArrayList<>();
    private final List<Allocation> allocations = new ArrayList<>();
    private final List<ClassroomResponseDto> classrooms = new ArrayList<>();
    private final List<AcademicEventResponseDto> events = new ArrayList<>();
    private Map<Long, ClassroomSubjectPermissionDto> permissions = Map.of();

    @BeforeEach
    void setUp() {
        service = new OccurrenceBoardServiceImpl(occurrenceService, allocationRepository, academicEventService,
                classroomService, buildingScopeResolver, new ConflictDateRange(academicPeriodService));
        lenient().when(buildingScopeResolver.scopeFor(any())).thenReturn(BuildingScope.unrestricted());
        lenient().when(academicPeriodService.findActive()).thenReturn(List.of());
    }

    private void stubData() {
        lenient().when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.copyOf(slots));
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.copyOf(allocations));
        lenient().when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.copyOf(classrooms));
        lenient().when(classroomService.findSubjectPermissions(any())).thenReturn(permissions);
        lenient().when(academicEventService.findByIds(any())).thenReturn(List.copyOf(events));
    }

    private List<OccurrenceBoardEventDto> board(Set<OccurrenceIssueType> issues) {
        return board(true, issues);
    }

    private List<OccurrenceBoardEventDto> board(boolean onlyFuture, Set<OccurrenceIssueType> issues) {
        stubData();
        return service.findBoard(TODAY, DATE.plusYears(1), onlyFuture, issues, null, null, null,
                PageRequest.of(0, 50)).getContent();
    }

    private AcademicEventResponseDto event(long id, int enrolled) {
        AcademicEventResponseDto event = recurring(id, "Materia " + id, "Ingeniería en Sistemas", "1K1",
                DayOfWeek.MONDAY, START, enrolled);
        events.add(event);
        return event;
    }

    private OccurrenceSlotDto slot(long id, long eventId, LocalDate date, LocalTime start, OccurrenceStatus status,
                                   int roomSlot, ReleaseReason reason, Long previousClassroomId) {
        OccurrenceSlotDto slot = new OccurrenceSlotDto(id, eventId, date, start, start.plusHours(1), status, 30,
                roomSlot, reason, null, previousClassroomId, roomSlot > 1 ? id - 1 : null);
        slots.add(slot);
        return slot;
    }

    private OccurrenceSlotDto needsRoom(long id, long eventId, LocalDate date, int roomSlot) {
        return slot(id, eventId, date, START, OccurrenceStatus.NEEDS_ROOM, roomSlot, null, null);
    }

    private OccurrenceSlotDto released(long id, long eventId, LocalDate date, int roomSlot, ReleaseReason reason,
                                       Long previousClassroomId) {
        return slot(id, eventId, date, START, OccurrenceStatus.ROOM_RELEASED, roomSlot, reason, previousClassroomId);
    }

    private void assigned(long occurrenceId, long classroomId) {
        allocations.add(Allocation.builder().id(occurrenceId * 10).occurrenceId(occurrenceId)
                .classroomId(classroomId).source(AllocationSource.MANUAL).build());
    }

    private ClassroomResponseDto room(long id, Integer capacity, long buildingId, UnavailableReason unavailable) {
        ClassroomResponseDto classroom = new ClassroomResponseDto(id, (int) id + 100, capacity, buildingId,
                "Edificio " + buildingId, 1L, "Aula", unavailable, true);
        classrooms.add(classroom);
        return classroom;
    }

    private ClassroomResponseDto room(long id, Integer capacity, long buildingId) {
        return room(id, capacity, buildingId, null);
    }

    private static OccurrenceBoardEventDto single(List<OccurrenceBoardEventDto> result) {
        assertThat(result).hasSize(1);
        return result.getFirst();
    }

    private static List<LocalDate> datesOf(OccurrenceBoardEventDto event) {
        return event.occurrences().stream().map(OccurrenceBoardDateDto::date).toList();
    }

    private static List<Long> occurrenceIdsOf(OccurrenceBoardDateDto date) {
        return date.slots().stream().map(OccurrenceBoardSlotDto::occurrenceId).toList();
    }

    @Test
    @DisplayName("principal asignada y espejo NEEDS_ROOM sin aula: una fila con 2 slots ordenados, requiredRooms 2 y 1 aula faltante")
    void principalAllocatedAndMirrorWithoutRoom() {
        event(1, 20);
        needsRoom(11, 1, DATE, 2);
        needsRoom(10, 1, DATE, 1);
        room(5, 40, 1);
        assigned(10, 5);

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        OccurrenceBoardDateDto date = result.occurrences().getFirst();
        assertThat(result.occurrences()).hasSize(1);
        assertThat(occurrenceIdsOf(date)).containsExactly(10L, 11L);
        assertThat(date.requiredRooms()).isEqualTo(2);
        assertThat(date.issues()).containsExactly(new UnallocatedIssueDto(1));
        assertThat(date.slots().get(0).allocation().classroomId()).isEqualTo(5L);
        assertThat(date.slots().get(1).allocation()).isNull();
        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.summary().unallocated()).isEqualTo(1);
    }

    @Test
    @DisplayName("fecha con 2 slots sin aula: summary.unallocated cuenta 1 fecha, no 2 aulas")
    void twoSlotsWithoutRoomCountOneDate() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        assertThat(result.occurrences().getFirst().issues()).containsExactly(new UnallocatedIssueDto(2));
        assertThat(result.summary().unallocated()).isEqualTo(1);
        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("espejo ROOM_RELEASED con MIRROR_RETIRED: no aparece en slots, no cuenta en released y con issues=RELEASED el evento no sale")
    void retiredMirrorIsHidden() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        released(11, 1, DATE, 2, ReleaseReason.MIRROR_RETIRED, null);
        room(5, 40, 1);
        assigned(10, 5);

        OccurrenceBoardEventDto unfiltered = single(board(NO_FILTER));
        List<OccurrenceBoardEventDto> releasedOnly = board(Set.of(OccurrenceIssueType.RELEASED));

        OccurrenceBoardDateDto date = unfiltered.occurrences().getFirst();
        assertThat(occurrenceIdsOf(date)).containsExactly(10L);
        assertThat(date.requiredRooms()).isEqualTo(1);
        assertThat(unfiltered.summary().released()).isZero();
        assertThat(releasedOnly).isEmpty();
    }

    @Test
    @DisplayName("slot liberado con VIRTUAL_CLASS: sin UNALLOCATED, requiredRooms 0, sale con issues=RELEASED y no con issues=UNALLOCATED")
    void releasedSlotIsNotUnallocated() {
        event(1, 20);
        released(10, 1, DATE, 1, ReleaseReason.VIRTUAL_CLASS, null);

        OccurrenceBoardEventDto unfiltered = single(board(NO_FILTER));
        List<OccurrenceBoardEventDto> releasedOnly = board(Set.of(OccurrenceIssueType.RELEASED));
        List<OccurrenceBoardEventDto> unallocatedOnly = board(Set.of(OccurrenceIssueType.UNALLOCATED));

        OccurrenceBoardDateDto date = unfiltered.occurrences().getFirst();
        assertThat(date.issues()).isEmpty();
        assertThat(date.requiredRooms()).isZero();
        assertThat(date.slots().getFirst().status()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        assertThat(date.slots().getFirst().releaseReason()).isEqualTo(ReleaseReason.VIRTUAL_CLASS);
        assertThat(unfiltered.summary().released()).isEqualTo(1);
        assertThat(unfiltered.summary().unallocated()).isZero();
        assertThat(releasedOnly).hasSize(1);
        assertThat(unallocatedOnly).isEmpty();
    }

    @Test
    @DisplayName("slot liberado con aula anterior desactivada: previousClassroom trae el aula y su unavailableReason")
    void releasedSlotWithDeactivatedPreviousClassroom() {
        event(1, 20);
        released(10, 1, DATE, 1, ReleaseReason.VIRTUAL_CLASS, 7L);
        room(7, 30, 1, UnavailableReason.DEACTIVATED);

        OccurrenceBoardSlotDto result = single(board(NO_FILTER)).occurrences().getFirst().slots().getFirst();

        assertThat(result.previousClassroom().classroomId()).isEqualTo(7L);
        assertThat(result.previousClassroom().roomNumber()).isEqualTo(107);
        assertThat(result.previousClassroom().buildingId()).isEqualTo(1L);
        assertThat(result.previousClassroom().buildingName()).isEqualTo("Edificio 1");
        assertThat(result.previousClassroom().capacity()).isEqualTo(30);
        assertThat(result.previousClassroom().unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
        verify(classroomService).findByIdsIncludingDeactivated(Set.of(7L));
    }

    @Test
    @DisplayName("slot liberado sin previousClassroomId: previousClassroom es null")
    void releasedSlotWithoutPreviousClassroom() {
        event(1, 20);
        released(10, 1, DATE, 1, ReleaseReason.CANCELLED, null);

        OccurrenceBoardSlotDto result = single(board(NO_FILTER)).occurrences().getFirst().slots().getFirst();

        assertThat(result.previousClassroom()).isNull();
    }

    @Test
    @DisplayName("slot NEEDS_ROOM sin asignación que conserva previousClassroomId (liberación revertida): previousClassroom completo")
    void revertedSlotKeepsPreviousClassroom() {
        event(1, 20);
        slot(10, 1, DATE, START, OccurrenceStatus.NEEDS_ROOM, 1, null, 7L);
        room(7, 30, 1);

        OccurrenceBoardSlotDto result = single(board(NO_FILTER)).occurrences().getFirst().slots().getFirst();

        assertThat(result.status()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        assertThat(result.allocation()).isNull();
        assertThat(result.previousClassroom().classroomId()).isEqualTo(7L);
        assertThat(result.previousClassroom().capacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("slot NEEDS_ROOM sin asignación con aula anterior fuera del alcance: el slot se ve y previousClassroom es null")
    void revertedSlotWithPreviousClassroomOutOfScope() {
        event(1, 20);
        slot(10, 1, DATE, START, OccurrenceStatus.NEEDS_ROOM, 1, null, 7L);
        room(7, 30, 1);
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(2L)));

        OccurrenceBoardSlotDto result = single(board(NO_FILTER)).occurrences().getFirst().slots().getFirst();

        assertThat(result.occurrenceId()).isEqualTo(10L);
        assertThat(result.previousClassroom()).isNull();
    }

    @Test
    @DisplayName("slot con asignación: previousClassroom es null aunque tenga previousClassroomId")
    void allocatedSlotHasNoPreviousClassroom() {
        event(1, 20);
        slot(10, 1, DATE, START, OccurrenceStatus.NEEDS_ROOM, 1, null, 7L);
        room(5, 40, 1);
        room(7, 30, 1);
        assigned(10, 5);

        OccurrenceBoardSlotDto result = single(board(NO_FILTER)).occurrences().getFirst().slots().getFirst();

        assertThat(result.allocation().classroomId()).isEqualTo(5L);
        assertThat(result.previousClassroom()).isNull();
    }

    @Test
    @DisplayName("summary no aplica issues: con issues=UNALLOCATED salen las 3 fechas sin aula y summary cuenta también el sobrecupo")
    void summaryIgnoresIssuesFilter() {
        event(1, 30);
        needsRoom(10, 1, DATE, 1);
        needsRoom(20, 1, DATE.plusDays(7), 1);
        needsRoom(30, 1, DATE.plusDays(14), 1);
        needsRoom(40, 1, DATE.plusDays(21), 1);
        room(5, 10, 1);
        assigned(40, 5);

        OccurrenceBoardEventDto result = single(board(Set.of(OccurrenceIssueType.UNALLOCATED)));

        assertThat(datesOf(result)).containsExactly(DATE, DATE.plusDays(7), DATE.plusDays(14));
        assertThat(result.summary().total()).isEqualTo(4);
        assertThat(result.summary().unallocated()).isEqualTo(3);
        assertThat(result.summary().overcrowded()).isEqualTo(1);
    }

    @Test
    @DisplayName("issues null o vacío no filtra: devuelve también la fecha OK (issues vacío) y la liberada")
    void emptyIssuesDoNotFilter() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        released(20, 1, DATE.plusDays(7), 1, ReleaseReason.VIRTUAL_CLASS, null);
        room(5, 40, 1);
        assigned(10, 5);

        for (Set<OccurrenceIssueType> issues : Arrays.asList(null, Set.<OccurrenceIssueType>of(),
                EnumSet.noneOf(OccurrenceIssueType.class))) {
            OccurrenceBoardEventDto result = single(board(issues));

            assertThat(datesOf(result)).containsExactly(DATE, DATE.plusDays(7));
            assertThat(result.occurrences().get(0).issues()).isEmpty();
            assertThat(result.occurrences().get(1).slots().getFirst().status())
                    .isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        }
    }

    @Test
    @DisplayName("issues se combinan con OR: OVERCROWDED,NOT_PERMITTED deja la fecha solo NOT_PERMITTED y descarta la solo UNALLOCATED")
    void issuesAreCombinedWithOr() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        needsRoom(20, 1, DATE.plusDays(7), 1);
        room(6, 100, 1);
        assigned(10, 6);
        permissions = Map.of(6L, new ClassroomSubjectPermissionDto(6L, false, Set.of(999L)));

        OccurrenceBoardEventDto result = single(board(
                EnumSet.of(OccurrenceIssueType.OVERCROWDED, OccurrenceIssueType.NOT_PERMITTED)));

        assertThat(datesOf(result)).containsExactly(DATE);
        assertThat(result.occurrences().getFirst().issues()).containsExactly(new NotPermittedIssueDto(10L, 6L));
        assertThat(result.summary().total()).isEqualTo(2);
        assertThat(result.summary().notPermitted()).isEqualTo(1);
        assertThat(result.summary().unallocated()).isEqualTo(1);
    }

    @Test
    @DisplayName("aula que habilita la materia: no hay NOT_PERMITTED")
    void permittedClassroomHasNoIssue() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        room(6, 100, 1);
        assigned(10, 6);
        permissions = Map.of(6L, new ClassroomSubjectPermissionDto(6L, false, Set.of(1L)));

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        assertThat(result.occurrences().getFirst().issues()).isEmpty();
    }

    @ParameterizedTest(name = "2 aulas de 20, {0} inscriptos: sobrecupo de {1}")
    @CsvSource({"50, 10", "41, 1"})
    @DisplayName("sobrecupo con 2 aulas de 20: capacity es la suma de las aulas de la fecha")
    void overcrowdedSumsClassroomCapacities(int enrolled, int overcrowdedBy) {
        event(1, enrolled);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(5, 20, 1);
        room(6, 20, 1);
        assigned(10, 5);
        assigned(11, 6);

        OccurrenceBoardDateDto date = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(date.issues()).containsExactly(new OvercrowdedIssueDto(enrolled, 40, overcrowdedBy));
    }

    @Test
    @DisplayName("inscriptos igual a la capacidad sumada de las aulas: no hay sobrecupo")
    void enrolledEqualToCapacityIsNotOvercrowded() {
        event(1, 40);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(5, 20, 1);
        room(6, 20, 1);
        assigned(10, 5);
        assigned(11, 6);

        OccurrenceBoardDateDto date = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(date.issues()).isEmpty();
    }

    @Test
    @DisplayName("sobrecupo con un aula sin capacidad informada: no hay issue")
    void nullCapacityHasNoOvercrowding() {
        event(1, 50);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(5, 20, 1);
        room(6, null, 1);
        assigned(10, 5);
        assigned(11, 6);

        OccurrenceBoardDateDto date = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(date.issues()).isEmpty();
    }

    @Test
    @DisplayName("evento con inscriptos null se trata como 0: no hay sobrecupo")
    void nullEnrolledIsTreatedAsZero() {
        events.add(recurring(1, "Materia 1", "Ingeniería en Sistemas", "1K1", DayOfWeek.MONDAY, START, null));
        needsRoom(10, 1, DATE, 1);
        room(5, 10, 1);
        assigned(10, 5);

        OccurrenceBoardDateDto date = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(date.issues()).isEmpty();
    }

    @Test
    @DisplayName("dos eventos en la misma aula y fecha con horarios que se pisan: cada uno tiene OVERLAP con el otro evento")
    void overlapBetweenTwoEvents() {
        event(1, 20);
        event(2, 20);
        slot(10, 1, DATE, LocalTime.of(9, 0), OccurrenceStatus.NEEDS_ROOM, 1, null, null);
        slot(20, 2, DATE, LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 1, null, null);
        room(5, 100, 1);
        assigned(10, 5);
        assigned(20, 5);

        List<OccurrenceBoardEventDto> result = board(NO_FILTER);

        assertThat(result).hasSize(2);
        OccurrenceBoardEventDto first = result.get(0);
        OccurrenceBoardEventDto second = result.get(1);
        assertThat(first.occurrences().getFirst().issues()).hasSize(1).first().isInstanceOfSatisfying(
                OverlapIssueDto.class, issue -> {
                    assertThat(issue.withEvent().id()).isEqualTo(2L);
                    assertThat(issue.occurrenceId()).isEqualTo(10L);
                    assertThat(issue.classroomId()).isEqualTo(5L);
                });
        assertThat(second.occurrences().getFirst().issues()).hasSize(1).first().isInstanceOfSatisfying(
                OverlapIssueDto.class, issue -> {
                    assertThat(issue.withEvent().id()).isEqualTo(1L);
                    assertThat(issue.occurrenceId()).isEqualTo(20L);
                });
        assertThat(first.summary().overlap()).isEqualTo(1);
        assertThat(second.summary().overlap()).isEqualTo(1);
    }

    @Test
    @DisplayName("dos eventos en la misma aula y fecha con horarios contiguos: no hay OVERLAP")
    void contiguousEventsDoNotOverlap() {
        event(1, 20);
        event(2, 20);
        slot(10, 1, DATE, LocalTime.of(9, 0), OccurrenceStatus.NEEDS_ROOM, 1, null, null);
        slot(20, 2, DATE, LocalTime.of(10, 0), OccurrenceStatus.NEEDS_ROOM, 1, null, null);
        room(5, 100, 1);
        assigned(10, 5);
        assigned(20, 5);

        List<OccurrenceBoardEventDto> result = board(NO_FILTER);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(e -> assertThat(e.occurrences().getFirst().issues()).isEmpty());
    }

    @Test
    @DisplayName("dos ocurrencias del mismo evento en la misma aula y fecha: no hay OVERLAP")
    void sameEventInSameRoomDoesNotOverlap() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(5, 100, 1);
        assigned(10, 5);
        assigned(11, 5);

        OccurrenceBoardDateDto date = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(date.issues()).isEmpty();
    }

    @Test
    @DisplayName("alcance: el slot de otro edificio y su NOT_PERMITTED desaparecen, pero el sobrecupo sigue sumando su aula")
    void scopeHidesSlotAndItsIssuesButKeepsOvercrowdingSum() {
        event(1, 50);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(5, 20, 1);
        room(6, 20, 2);
        assigned(10, 5);
        assigned(11, 6);
        permissions = Map.of(6L, new ClassroomSubjectPermissionDto(6L, false, Set.of(999L)));

        OccurrenceBoardDateDto unrestricted = single(board(NO_FILTER)).occurrences().getFirst();
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(1L)));
        OccurrenceBoardDateDto scoped = single(board(NO_FILTER)).occurrences().getFirst();

        assertThat(occurrenceIdsOf(unrestricted)).containsExactly(10L, 11L);
        assertThat(unrestricted.issues()).contains(new NotPermittedIssueDto(11L, 6L));
        assertThat(occurrenceIdsOf(scoped)).containsExactly(10L);
        assertThat(scoped.issues()).doesNotContain(new NotPermittedIssueDto(11L, 6L));
        assertThat(scoped.issues()).containsExactly(new OvercrowdedIssueDto(50, 40, 10));
        assertThat(scoped.requiredRooms()).isEqualTo(2);
    }

    @Test
    @DisplayName("alcance: si todas las aulas asignadas de la fecha son de otro edificio, la fecha se ve por el espejo sin aula y sin OVERCROWDED")
    void scopeDropsOvercrowdingWhenNoVisibleSlotHasRoom() {
        event(1, 50);
        needsRoom(10, 1, DATE, 1);
        needsRoom(11, 1, DATE, 2);
        room(6, 20, 2);
        assigned(10, 6);
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(1L)));

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        OccurrenceBoardDateDto date = result.occurrences().getFirst();
        assertThat(occurrenceIdsOf(date)).containsExactly(11L);
        assertThat(date.issues()).containsExactly(new UnallocatedIssueDto(1));
        assertThat(result.summary().overcrowded()).isZero();
    }

    @Test
    @DisplayName("alcance: una fecha con un único slot en otro edificio no aparece y no cuenta en summary.total")
    void scopeHidesDatesWithOnlyForeignSlots() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        needsRoom(20, 1, DATE.plusDays(7), 1);
        room(5, 40, 1);
        room(6, 40, 2);
        assigned(10, 5);
        assigned(20, 6);
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(1L)));

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        assertThat(datesOf(result)).containsExactly(DATE);
        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("alcance: un evento cuyas fechas están todas en otro edificio no aparece")
    void scopeHidesEventsWithOnlyForeignDates() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        room(6, 40, 2);
        assigned(10, 6);
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(1L)));

        assertThat(board(NO_FILTER)).isEmpty();
    }

    @Test
    @DisplayName("alcance: un slot sin aula se ve siempre; un liberado con aula anterior de otro edificio no")
    void scopeKeepsUnallocatedAndHidesForeignReleased() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        released(20, 1, DATE.plusDays(7), 1, ReleaseReason.VIRTUAL_CLASS, 6L);
        released(30, 1, DATE.plusDays(14), 1, ReleaseReason.VIRTUAL_CLASS, 5L);
        released(40, 1, DATE.plusDays(21), 1, ReleaseReason.VIRTUAL_CLASS, null);
        room(5, 40, 1);
        room(6, 40, 2);
        lenient().when(buildingScopeResolver.scopeFor(Permission.CONFLICT_READ))
                .thenReturn(BuildingScope.of(Set.of(1L)));

        OccurrenceBoardEventDto result = single(board(NO_FILTER));

        assertThat(datesOf(result)).containsExactly(DATE, DATE.plusDays(14), DATE.plusDays(21));
        assertThat(result.summary().total()).isEqualTo(3);
        assertThat(result.summary().released()).isEqualTo(2);
    }

    @Test
    @DisplayName("onlyFuture=true descarta la fecha de hoy que ya empezó; con false aparece con past=true")
    void onlyFutureDiscardsStartedDate() {
        event(1, 20);
        slot(10, 1, TODAY, LocalTime.MIN, OccurrenceStatus.NEEDS_ROOM, 1, null, null);
        needsRoom(20, 1, DATE, 1);

        OccurrenceBoardEventDto onlyFuture = single(board(true, NO_FILTER));
        OccurrenceBoardEventDto all = single(board(false, NO_FILTER));

        assertThat(datesOf(onlyFuture)).containsExactly(DATE);
        assertThat(onlyFuture.occurrences().getFirst().past()).isFalse();
        assertThat(datesOf(all)).containsExactly(TODAY, DATE);
        assertThat(all.occurrences().get(0).past()).isTrue();
        assertThat(all.occurrences().get(1).past()).isFalse();
    }

    @Test
    @DisplayName("onlyFuture=false sin from usa el startDate menor de los períodos activos y el endDate mayor como to")
    void defaultFromWithoutOnlyFutureUsesEarliestActivePeriodStart() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        LocalDate earliest = TODAY.minusDays(100);
        lenient().when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(1L, 2026, 1, TODAY.minusDays(50), TODAY.plusDays(200), null, null),
                new AcademicPeriodResponseDto(2L, 2026, 2, earliest, TODAY.plusDays(100), null, null)));
        stubData();

        service.findBoard(null, null, false, NO_FILTER, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceService).findSlotsBetween(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(earliest);
        assertThat(to.getValue()).isEqualTo(TODAY.plusDays(200));
    }

    @Test
    @DisplayName("onlyFuture=false sin from y sin períodos activos usa hoy")
    void defaultFromWithoutActivePeriodsIsToday() {
        stubData();

        service.findBoard(null, null, false, NO_FILTER, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceService).findSlotsBetween(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(TODAY);
        assertThat(to.getValue()).isEqualTo(TODAY.plusMonths(6));
    }

    @Test
    @DisplayName("onlyFuture=true sin from usa hoy aunque haya períodos activos que empezaron antes")
    void defaultFromWithOnlyFutureIsToday() {
        lenient().when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(1L, 2026, 1, TODAY.minusDays(100), TODAY.plusDays(100), null, null)));
        stubData();

        service.findBoard(null, null, true, NO_FILTER, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceService).findSlotsBetween(from.capture(), any());
        assertThat(from.getValue()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("un evento que findByIds no devuelve se descarta sin error")
    void missingEventIsDiscarded() {
        event(1, 20);
        needsRoom(10, 1, DATE, 1);
        needsRoom(20, 99, DATE, 1);

        List<OccurrenceBoardEventDto> result = board(NO_FILTER);

        assertThat(result).extracting(e -> e.event().id()).containsExactly(1L);
    }

    private void catalog() {
        events.add(recurring(1, "Análisis Matemático", "Ingeniería en Sistemas", "1K1", DayOfWeek.MONDAY, START, 30));
        events.add(recurring(2, "Física I", "Ingeniería Mecánica", "2K3", DayOfWeek.TUESDAY, START, 50));
        events.add(unique(3, "Algebra", "Ingeniería en Sistemas", null, DATE.with(DayOfWeek.WEDNESDAY), START, 20));
        events.add(recurring(4, "Sistemas de Gestión", "Ingeniería Industrial", "3K2", DayOfWeek.THURSDAY, START, 10));
        for (long id = 1; id <= 4; id++) {
            needsRoom(id * 10, id, DATE, 1);
        }
    }

    private List<Long> find(String q, DayOfWeek dayOfWeek, String specialty, Sort sort) {
        stubData();
        return service.findBoard(TODAY, DATE.plusYears(1), true, NO_FILTER, q, dayOfWeek, specialty,
                        PageRequest.of(0, 50, sort)).getContent().stream()
                .map(e -> e.event().id()).toList();
    }

    @Test
    @DisplayName("q, dayOfWeek y specialty descartan los eventos que no coinciden")
    void eventFiltersDiscardNonMatching() {
        catalog();

        assertThat(find(null, null, null, Sort.unsorted())).containsExactly(1L, 2L, 3L, 4L);
        assertThat(find("FÍSICA", null, null, Sort.unsorted())).containsExactly(2L);
        assertThat(find(null, DayOfWeek.TUESDAY, null, Sort.unsorted())).containsExactly(2L);
        assertThat(find(null, null, "Ingeniería en Sistemas", Sort.unsorted())).containsExactly(1L, 3L);
        assertThat(find("sistemas", DayOfWeek.WEDNESDAY, "Ingeniería en Sistemas", Sort.unsorted()))
                .containsExactly(3L);
    }

    @Test
    @DisplayName("sort=subject invierte el orden con desc")
    void sortBySubjectReversesWithDesc() {
        catalog();

        assertThat(find(null, null, null, Sort.by("subject").ascending())).containsExactly(3L, 1L, 2L, 4L);
        assertThat(find(null, null, null, Sort.by("subject").descending())).containsExactly(4L, 2L, 1L, 3L);
    }

    @Test
    @DisplayName("un sort inexistente lanza InvalidSortPropertyException antes de consultar nada")
    void unknownSortThrowsBeforeQuerying() {
        assertThatThrownBy(() -> service.findBoard(TODAY, DATE, true, NO_FILTER, null, null, null,
                PageRequest.of(0, 20, Sort.by("foo"))))
                .isInstanceOf(InvalidSortPropertyException.class);

        verifyNoInteractions(occurrenceService, allocationRepository, academicEventService, classroomService);
    }
}
