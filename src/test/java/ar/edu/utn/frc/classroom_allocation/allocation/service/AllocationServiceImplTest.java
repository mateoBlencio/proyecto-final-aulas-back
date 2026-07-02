package ar.edu.utn.frc.classroom_allocation.allocation.service;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AcademicEventNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AllocationDomainException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.AllocationNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.exception.OccurrenceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.allocation.mapper.AllocationMapper;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Allocation;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AllocationSource;
import ar.edu.utn.frc.classroom_allocation.allocation.model.Occurrence;
import ar.edu.utn.frc.classroom_allocation.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.classroom_allocation.allocation.model.RecurringEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.model.UniqueEvent;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.classroom_allocation.allocation.service.impl.AllocationServiceImpl;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.space.repository.ClassroomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationServiceImplTest {

    @Mock AllocationRepository allocationRepository;
    @Mock OccurrenceRepository occurrenceRepository;
    @Mock AcademicEventRepository eventRepository;
    @Mock ClassroomRepository classroomRepository;
    @Mock AllocationMapper mapper;

    AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllocationServiceImpl(allocationRepository, occurrenceRepository, eventRepository, classroomRepository, mapper);
    }

    private RecurringEvent futureEvent() {
        return RecurringEvent.builder()
                .id(1L).enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();
    }

    private Occurrence futureOccurrence(Long id) {
        return Occurrence.builder()
                .id(id)
                .event(futureEvent())
                .date(LocalDate.now().plusDays(7))
                .status(OccurrenceStatus.SCHEDULED)
                .build();
    }

    private Occurrence pastOccurrence(Long id) {
        return Occurrence.builder()
                .id(id)
                .event(futureEvent())
                .date(LocalDate.now().minusDays(7))
                .status(OccurrenceStatus.SCHEDULED)
                .build();
    }

    private Classroom classroom(Integer id) {
        return Classroom.builder().id(id).roomNumber("101").capacity(40).build();
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    void upAs001_findById_notFound_throws() {
        when(allocationRepository.findByIdEager(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(AllocationNotFoundException.class);
    }

    @Test
    void upAs002_findById_found_mapsToDto() {
        Allocation allocation = Allocation.builder().id(1L).build();
        AllocationResponseDto dto = AllocationResponseDto.builder().id(1L).build();
        when(allocationRepository.findByIdEager(1L)).thenReturn(Optional.of(allocation));
        when(mapper.toDto(allocation)).thenReturn(dto);

        assertThat(service.findById(1L)).isSameAs(dto);
    }

    // ─── assign ──────────────────────────────────────────────────────────────

    @Test
    void upAs003_assign_occurrenceNotFound_throws() {
        when(occurrenceRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(5L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(OccurrenceNotFoundException.class);
    }

    @Test
    void upAs004_assign_pastOccurrence_throwsDomainException() {
        Occurrence occurrence = pastOccurrence(5L);
        when(occurrenceRepository.findById(5L)).thenReturn(Optional.of(occurrence));

        assertThatThrownBy(() -> service.assign(5L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(AllocationDomainException.class);
    }

    @Test
    void upAs005_assign_alreadyAllocated_throwsDomainException() {
        Occurrence occurrence = futureOccurrence(5L);
        when(occurrenceRepository.findById(5L)).thenReturn(Optional.of(occurrence));
        when(allocationRepository.findByOccurrence_Id(5L)).thenReturn(Optional.of(Allocation.builder().id(1L).build()));

        assertThatThrownBy(() -> service.assign(5L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(AllocationDomainException.class);
    }

    @Test
    void upAs006_assign_classroomNotFound_throwsDomainException() {
        Occurrence occurrence = futureOccurrence(5L);
        when(occurrenceRepository.findById(5L)).thenReturn(Optional.of(occurrence));
        when(allocationRepository.findByOccurrence_Id(5L)).thenReturn(Optional.empty());
        when(classroomRepository.findByIdAndDeletedFalse(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(5L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(AllocationDomainException.class);
    }

    @Test
    void upAs007_assign_success_savesManualAllocation() {
        Occurrence occurrence = futureOccurrence(5L);
        Classroom classroom = classroom(10);
        when(occurrenceRepository.findById(5L)).thenReturn(Optional.of(occurrence));
        when(allocationRepository.findByOccurrence_Id(5L)).thenReturn(Optional.empty());
        when(classroomRepository.findByIdAndDeletedFalse(10)).thenReturn(Optional.of(classroom));
        Allocation saved = Allocation.builder().id(1L).build();
        when(allocationRepository.save(any(Allocation.class))).thenReturn(saved);
        AllocationResponseDto dto = AllocationResponseDto.builder().id(1L).build();
        when(mapper.toDto(saved)).thenReturn(dto);

        AllocationResponseDto result = service.assign(5L, new AllocateOccurrenceRequestDto(10, "obs"));

        assertThat(result).isSameAs(dto);
        verify(allocationRepository).save(argThatAllocation(a ->
                a.getSource() == AllocationSource.MANUAL
                        && a.getOccurrence() == occurrence
                        && a.getClassroom() == classroom
                        && "obs".equals(a.getObservation())));
    }

    // ─── reassign ────────────────────────────────────────────────────────────

    @Test
    void upAs008_reassign_notFound_throws() {
        when(allocationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(1L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(AllocationNotFoundException.class);
    }

    @Test
    void upAs009_reassign_pastOccurrence_throwsDomainException() {
        Allocation allocation = Allocation.builder().id(1L).occurrence(pastOccurrence(5L)).build();
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> service.reassign(1L, new AllocateOccurrenceRequestDto(10, "obs")))
                .isInstanceOf(AllocationDomainException.class);
    }

    @Test
    void upAs010_reassign_success_updatesClassroomAndSource() {
        Allocation allocation = Allocation.builder().id(1L).occurrence(futureOccurrence(5L))
                .source(AllocationSource.AUTOMATIC).build();
        Classroom classroom = classroom(20);
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(allocation));
        when(classroomRepository.findByIdAndDeletedFalse(20)).thenReturn(Optional.of(classroom));
        when(allocationRepository.save(allocation)).thenReturn(allocation);
        AllocationResponseDto dto = AllocationResponseDto.builder().id(1L).build();
        when(mapper.toDto(allocation)).thenReturn(dto);

        AllocationResponseDto result = service.reassign(1L, new AllocateOccurrenceRequestDto(20, "new obs"));

        assertThat(result).isSameAs(dto);
        assertThat(allocation.getClassroom()).isSameAs(classroom);
        assertThat(allocation.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(allocation.getObservation()).isEqualTo("new obs");
    }

    // ─── batchReassign ───────────────────────────────────────────────────────

    @Test
    void upAs011_batchReassign_movesAll() {
        Allocation a1 = Allocation.builder().id(1L).occurrence(futureOccurrence(5L)).build();
        Allocation a2 = Allocation.builder().id(2L).occurrence(futureOccurrence(6L)).build();
        Classroom c10 = classroom(10);
        Classroom c20 = classroom(20);

        when(allocationRepository.findById(1L)).thenReturn(Optional.of(a1));
        when(allocationRepository.findById(2L)).thenReturn(Optional.of(a2));
        when(classroomRepository.findByIdAndDeletedFalse(10)).thenReturn(Optional.of(c10));
        when(classroomRepository.findByIdAndDeletedFalse(20)).thenReturn(Optional.of(c20));
        when(allocationRepository.save(a1)).thenReturn(a1);
        when(allocationRepository.save(a2)).thenReturn(a2);
        when(mapper.toDto(a1)).thenReturn(AllocationResponseDto.builder().id(1L).build());
        when(mapper.toDto(a2)).thenReturn(AllocationResponseDto.builder().id(2L).build());

        BatchReassignRequestDto dto = new BatchReassignRequestDto(List.of(
                new BatchReassignRequestDto.MoveDto(1L, 10),
                new BatchReassignRequestDto.MoveDto(2L, 20)));

        List<AllocationResponseDto> results = service.batchReassign(dto);

        assertThat(results).hasSize(2);
        assertThat(a1.getClassroom()).isSameAs(c10);
        assertThat(a2.getClassroom()).isSameAs(c20);
    }

    // ─── cancel ──────────────────────────────────────────────────────────────

    @Test
    void upAs012_cancel_pastOccurrence_throwsDomainException() {
        Allocation allocation = Allocation.builder().id(1L).occurrence(pastOccurrence(5L)).build();
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> service.cancel(1L)).isInstanceOf(AllocationDomainException.class);
        verify(allocationRepository, never()).delete(any());
    }

    @Test
    void upAs013_cancel_success_deletes() {
        Allocation allocation = Allocation.builder().id(1L).occurrence(futureOccurrence(5L)).build();
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(allocation));

        service.cancel(1L);

        verify(allocationRepository).delete(allocation);
    }

    // ─── assignFromDate ──────────────────────────────────────────────────────

    @Test
    void upAs014_assignFromDate_eventNotFound_throws() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        AllocateFromDateRequestDto dto = new AllocateFromDateRequestDto(1L, LocalDate.now(), 10, "obs");

        assertThatThrownBy(() -> service.assignFromDate(dto))
                .isInstanceOf(AcademicEventNotFoundException.class);
    }

    @Test
    void upAs015_assignFromDate_uniqueEvent_throwsDomainException() {
        UniqueEvent uniqueEvent = UniqueEvent.builder().id(1L).enrolled(10)
                .startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(60))
                .date(LocalDate.now().plusDays(1)).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(uniqueEvent));

        AllocateFromDateRequestDto dto = new AllocateFromDateRequestDto(1L, LocalDate.now(), 10, "obs");

        assertThatThrownBy(() -> service.assignFromDate(dto))
                .isInstanceOf(AllocationDomainException.class);
    }

    @Test
    void upAs016_assignFromDate_createsNewAllocationsForOccurrencesWithoutOne() {
        RecurringEvent event = futureEvent();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        Classroom classroom = classroom(10);
        when(classroomRepository.findByIdAndDeletedFalse(10)).thenReturn(Optional.of(classroom));

        Occurrence occ = futureOccurrence(5L);
        when(occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(anyLong(), any())).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrence_Id(5L)).thenReturn(Optional.empty());
        Allocation saved = Allocation.builder().id(1L).build();
        when(allocationRepository.save(any(Allocation.class))).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(AllocationResponseDto.builder().id(1L).build());

        AllocateFromDateRequestDto dto = new AllocateFromDateRequestDto(1L, LocalDate.now(), 10, "obs");

        List<AllocationResponseDto> results = service.assignFromDate(dto);

        assertThat(results).hasSize(1);
    }

    @Test
    void upAs017_assignFromDate_skipsPastOccurrences() {
        RecurringEvent event = futureEvent();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        Classroom classroom = classroom(10);
        when(classroomRepository.findByIdAndDeletedFalse(10)).thenReturn(Optional.of(classroom));

        Occurrence past = pastOccurrence(9L);
        when(occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(anyLong(), any())).thenReturn(List.of(past));

        AllocateFromDateRequestDto dto = new AllocateFromDateRequestDto(1L, LocalDate.now().minusDays(30), 10, "obs");

        List<AllocationResponseDto> results = service.assignFromDate(dto);

        assertThat(results).isEmpty();
        verify(allocationRepository, times(0)).save(any());
    }

    // ─── findByDate ──────────────────────────────────────────────────────────

    @Test
    void upAs018_findByDate_mapsAllToSummaryDto() {
        Allocation a1 = Allocation.builder().id(1L).build();
        Allocation a2 = Allocation.builder().id(2L).build();
        LocalDate date = LocalDate.of(2024, 3, 4);
        when(allocationRepository.findByDateEager(date)).thenReturn(List.of(a1, a2));
        when(mapper.toSummaryDto(a1)).thenReturn(ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationSummaryDto.builder().id(1L).build());
        when(mapper.toSummaryDto(a2)).thenReturn(ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AllocationSummaryDto.builder().id(2L).build());

        assertThat(service.findByDate(date)).hasSize(2);
    }

    private static Allocation argThatAllocation(java.util.function.Predicate<Allocation> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
