package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationGroupDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSiblingDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationComposer")
class AllocationComposerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationRepository allocationRepository;

    private AllocationComposer composer;

    @BeforeEach
    void setUp() {
        composer = new AllocationComposer(Mappers.getMapper(AllocationMapper.class), academicEventService,
                occurrenceService, classroomService, allocationRepository);
        lenient().when(academicEventService.findByIds(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("compose: arma el grupo con las aulas hermanas y la capacidad sumada")
    void armaGrupoConHermanas() {
        stubSlots(slot(10L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM), slot(11L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM));
        Allocation own = allocation(100L, 10L, 5L);
        Allocation sibling = allocation(101L, 11L, 6L);
        stubGroup(List.of(own, sibling), classroom(5L, 70, 1L, "Central"), classroom(6L, 25, 2L, "Malvinas"));

        AllocationResponseDto result = composer.compose(own);

        assertThat(result.classroom().id()).isEqualTo(5L);
        AllocationGroupDto group = result.group();
        assertThat(group.roomCount()).isEqualTo(2);
        assertThat(group.capacity()).isEqualTo(95);
        assertThat(group.siblings()).containsExactly(new AllocationSiblingDto(101L, 6L, 6, 2L, "Malvinas", 25));
    }

    @Test
    @DisplayName("composeAll: no incluye en el grupo las ocurrencias con aula liberada")
    void excluyeRoomReleased() {
        stubSlots(slot(10L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM), slot(11L, 1L, DATE, OccurrenceStatus.ROOM_RELEASED));
        Allocation own = allocation(100L, 10L, 5L);
        Allocation released = allocation(101L, 11L, 6L);
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return java.util.stream.Stream.of(own, released).filter(a -> ids.contains(a.getOccurrenceId())).toList();
        });
        lenient().when(classroomService.findByIdsIncludingDeactivated(any()))
                .thenReturn(List.of(classroom(5L, 70, 1L, "Central"), classroom(6L, 25, 1L, "Central")));

        AllocationResponseDto result = composer.composeAll(List.of(own)).getFirst();

        assertThat(result.group().roomCount()).isEqualTo(1);
        assertThat(result.group().capacity()).isEqualTo(70);
        assertThat(result.group().siblings()).isEmpty();
    }

    @Test
    @DisplayName("compose: con una sola aula, roomCount es 1 y no hay hermanas")
    void unaSolaAula() {
        stubSlots(slot(10L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM));
        Allocation own = allocation(100L, 10L, 5L);
        stubGroup(List.of(own), classroom(5L, 70, 1L, "Central"));

        AllocationGroupDto group = composer.compose(own).group();

        assertThat(group.roomCount()).isEqualTo(1);
        assertThat(group.capacity()).isEqualTo(70);
        assertThat(group.siblings()).isEmpty();
    }

    @Test
    @DisplayName("compose: la capacidad del grupo es null si un aula no tiene capacidad")
    void capacidadNullSiUnAulaNoTiene() {
        stubSlots(slot(10L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM), slot(11L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM));
        Allocation own = allocation(100L, 10L, 5L);
        Allocation sibling = allocation(101L, 11L, 6L);
        stubGroup(List.of(own, sibling), classroom(5L, 70, 1L, "Central"), classroom(6L, null, 1L, "Central"));

        AllocationGroupDto group = composer.compose(own).group();

        assertThat(group.roomCount()).isEqualTo(2);
        assertThat(group.capacity()).isNull();
        assertThat(group.siblings()).singleElement().extracting(AllocationSiblingDto::capacity).isNull();
    }

    @Test
    @DisplayName("compose: el mismo evento en otra fecha no entra en el grupo")
    void otraFechaNoEntraEnElGrupo() {
        stubSlots(slot(10L, 1L, DATE, OccurrenceStatus.NEEDS_ROOM), slot(11L, 1L, DATE.plusDays(7), OccurrenceStatus.NEEDS_ROOM));
        Allocation own = allocation(100L, 10L, 5L);
        Allocation otherDate = allocation(101L, 11L, 6L);
        stubGroup(List.of(own, otherDate), classroom(5L, 70, 1L, "Central"), classroom(6L, 25, 1L, "Central"));

        AllocationGroupDto group = composer.compose(own).group();

        assertThat(group.roomCount()).isEqualTo(1);
        assertThat(group.siblings()).isEmpty();
    }

    private void stubSlots(OccurrenceSlotDto... slots) {
        List<OccurrenceSlotDto> all = List.of(slots);
        lenient().when(occurrenceService.findSlots(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return all.stream().filter(s -> ids.contains(s.occurrenceId())).toList();
        });
        lenient().when(occurrenceService.findSlotsByDate(any())).thenReturn(all);
    }

    private void stubGroup(List<Allocation> allocations, ClassroomResponseDto... classrooms) {
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return allocations.stream().filter(a -> ids.contains(a.getOccurrenceId())).toList();
        });
        lenient().when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(classrooms));
    }

    private static OccurrenceSlotDto slot(long id, long eventId, LocalDate date, OccurrenceStatus status) {
        return new OccurrenceSlotDto(id, eventId, date, LocalTime.of(8, 0), LocalTime.of(10, 0), status,
                40, 1, null, null, null, null);
    }

    private static Allocation allocation(long id, long occurrenceId, long classroomId) {
        return Allocation.builder().id(id).occurrenceId(occurrenceId).classroomId(classroomId)
                .source(AllocationSource.MANUAL).build();
    }

    private static ClassroomResponseDto classroom(long id, Integer capacity, long buildingId, String buildingName) {
        return new ClassroomResponseDto(id, (int) id, capacity, buildingId, buildingName, 1L, "Tipo", null, true);
    }
}
