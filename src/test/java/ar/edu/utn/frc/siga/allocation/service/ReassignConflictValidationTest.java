package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.impl.AllocationServiceImpl;
import ar.edu.utn.frc.siga.space.model.Classroom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReassignConflictValidationTest {

    @Mock AllocationRepository allocationRepository;
    @Mock ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository occurrenceRepository;
    @Mock ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository eventRepository;
    @Mock ar.edu.utn.frc.siga.space.service.ClassroomService classroomService;
    @Mock ar.edu.utn.frc.siga.allocation.mapper.AllocationMapper mapper;
    @InjectMocks AllocationServiceImpl service;

    private Method validateNoOverlap;
    private final LocalDate day = LocalDate.now().plusMonths(1).with(java.time.DayOfWeek.MONDAY);

    @BeforeEach
    void setUp() throws Exception {
        validateNoOverlap = AllocationServiceImpl.class.getDeclaredMethod(
                "validateNoOverlap", List.class, Classroom.class, AcademicEvent.class);
        validateNoOverlap.setAccessible(true);
    }

    private RecurringEvent event(long id) {
        return RecurringEvent.builder()
                .id(id).enrolled(30)
                .startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                .dayOfWeek(java.time.DayOfWeek.MONDAY).startDate(day)
                .build();
    }

    private Occurrence occurrence(long id, AcademicEvent event) {
        return Occurrence.builder().id(id).date(day).event(event).status(OccurrenceStatus.SCHEDULED).build();
    }

    private Classroom classroom(int id) {
        return Classroom.builder().id(id).roomNumber("R" + id).capacity(40).build();
    }

    private void invoke(List<Occurrence> targets, Classroom classroom, AcademicEvent event) throws Exception {
        try {
            validateNoOverlap.invoke(service, targets, classroom, event);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Test
    void occupiedSameRoomSameSlot_throwsWithConflictList() throws Exception {
        RecurringEvent target = event(10L);
        Occurrence targetOcc = occurrence(100L, target);
        Classroom room = classroom(1);

        RecurringEvent occupant = event(20L);
        Allocation existing = Allocation.builder()
                .id(500L).classroom(room).occurrence(occurrence(200L, occupant)).build();
        lenient().when(allocationRepository.findOccupancyBetween(day, day, OccurrenceStatus.ASSIGNED)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> invoke(List.of(targetOcc), room, target))
                .isInstanceOf(ReassignConflictException.class)
                .satisfies(ex -> assertThat(((ReassignConflictException) ex).getConflicts())
                        .singleElement()
                        .satisfies(c -> {
                            assertThat(c.occurrenceId()).isEqualTo(100L);
                            assertThat(c.conflictingEventId()).isEqualTo(20L);
                            assertThat(c.conflictingAllocationId()).isEqualTo(500L);
                        }));
    }

    @Test
    void occupiedDifferentRoom_noConflict() throws Exception {
        RecurringEvent target = event(10L);
        Occurrence targetOcc = occurrence(100L, target);

        RecurringEvent occupant = event(20L);
        Allocation existing = Allocation.builder()
                .id(500L).classroom(classroom(2)).occurrence(occurrence(200L, occupant)).build();
        lenient().when(allocationRepository.findOccupancyBetween(day, day, OccurrenceStatus.ASSIGNED)).thenReturn(List.of(existing));

        assertThatCode(() -> invoke(List.of(targetOcc), classroom(1), target)).doesNotThrowAnyException();
    }
}
