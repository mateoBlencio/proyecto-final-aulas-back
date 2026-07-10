package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.service.impl.AutoAllocationServiceImpl;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gap 2: la ocupación de eventos propios ya asignados (allocations existentes de las
 * occurrences ASSIGNED del mismo evento que se está resolviendo) debe llegar al solver,
 * no descartarse. Ver AutoAllocationServiceImpl.buildOccupancy.
 */
@ExtendWith(MockitoExtension.class)
class AutoAllocationServiceImplTest {

    @Mock AcademicEventRepository eventRepository;
    @Mock OccurrenceRepository occurrenceRepository;
    @Mock AllocationRepository allocationRepository;
    @Mock ClassroomService classroomService;
    @Mock SolverService solverService;
    @InjectMocks AutoAllocationServiceImpl service;

    private final LocalDate monday = LocalDate.now().plusMonths(1).with(DayOfWeek.MONDAY);

    private RecurringEvent event(long id, LocalDate startDate, LocalDate endDate) {
        return RecurringEvent.builder()
                .id(id).enrolled(30)
                .startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    @Test
    void ownEventAlreadyAssignedOccupancy_isIncludedInPreview() {
        LocalDate endDate = monday.plusWeeks(4);
        RecurringEvent own = event(1L, monday, endDate);

        // Una occurrence SCHEDULED pendiente de asignar y otra ya ASSIGNED con allocation
        // existente: gap 2 exige que la ASSIGNED sí bloquee su aula ante el solver.
        Occurrence scheduledOcc = Occurrence.builder()
                .id(10L).event(own).date(monday.plusWeeks(1)).status(OccurrenceStatus.SCHEDULED).build();
        Occurrence assignedOcc = Occurrence.builder()
                .id(11L).event(own).date(monday).status(OccurrenceStatus.ASSIGNED).build();

        Classroom room = Classroom.builder().id(5).roomNumber("R5").capacity(40).build();
        Allocation ownAllocation = Allocation.builder()
                .id(100L).occurrence(assignedOcc).classroom(room)
                .source(AllocationSource.MANUAL).createdAt(LocalDateTime.now()).build();

        when(eventRepository.findAllById(List.of(1L))).thenReturn(List.of(own));
        when(occurrenceRepository.findByEvent_IdInAndStatus(Set.of(1L), OccurrenceStatus.SCHEDULED))
                .thenReturn(List.of(scheduledOcc));
        when(classroomService.findAllAvailable()).thenReturn(List.of(
                ClassroomResponseDTO.builder().id(5).capacity(40).buildingId(1).build(),
                ClassroomResponseDTO.builder().id(6).capacity(40).buildingId(1).build()));
        when(allocationRepository.findOccupancyBetween(eq(monday), eq(endDate), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(ownAllocation));
        when(solverService.preview(any(), any(), any(), anyInt()))
                .thenReturn(new SolverPreview("prev_test", List.of()));

        AutoPreviewRequestDto request = new AutoPreviewRequestDto();
        request.setEventIds(List.of(1L));

        service.autoPreview(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SolverOccupancy>> occupancyCaptor = ArgumentCaptor.forClass(List.class);
        verify(solverService).preview(any(), any(), occupancyCaptor.capture(), anyInt());

        List<SolverOccupancy> occupancy = occupancyCaptor.getValue();
        assertThat(occupancy).hasSize(1);
        assertThat(occupancy.get(0).classroomId()).isEqualTo(5);
        assertThat(occupancy.get(0).date()).isEqualTo(monday);
        assertThat(occupancy.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(occupancy.get(0).endTime()).isEqualTo(LocalTime.of(9, 30));
    }
}