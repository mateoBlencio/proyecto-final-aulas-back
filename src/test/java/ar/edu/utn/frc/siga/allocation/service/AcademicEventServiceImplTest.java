package ar.edu.utn.frc.siga.allocation.service;

import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AcademicEventNotFoundException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventMapper;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.allocation.service.impl.AcademicEventServiceImpl;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicEventServiceImplTest {

    @Mock AcademicEventRepository eventRepository;
    @Mock RecurringEventRepository recurringEventRepository;
    @Mock OccurrenceRepository occurrenceRepository;
    @Mock AcademicEventMapper mapper;
    @Mock SubjectService subjectService;
    @Mock CommissionService commissionService;

    AcademicEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicEventServiceImpl(eventRepository, recurringEventRepository,
                occurrenceRepository, mapper, subjectService, commissionService);
    }

    private RecurringEvent recurringEvent() {
        return RecurringEvent.builder()
                .id(1L).enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2024, 3, 4))
                .endDate(LocalDate.of(2024, 7, 5))
                .build();
    }

    // ─── findAll / findById ──────────────────────────────────────────────────

    @Test
    void upEs001_findAll_mapsEveryEvent() {
        RecurringEvent e1 = recurringEvent();
        when(eventRepository.findAll()).thenReturn(List.of(e1));
        when(mapper.toDto(e1)).thenReturn(AcademicEventResponseDto.builder().id(1L).build());

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void upEs002_findById_notFound_throws() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(AcademicEventNotFoundException.class);
    }

    @Test
    void upEs003_findById_found_mapsToDto() {
        RecurringEvent event = recurringEvent();
        AcademicEventResponseDto dto = AcademicEventResponseDto.builder().id(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(mapper.toDto(event)).thenReturn(dto);

        assertThat(service.findById(1L)).isSameAs(dto);
    }

    // ─── findOccurrencesByEventId ────────────────────────────────────────────

    @Test
    void upEs004_findOccurrencesByEventId_eventNotFound_throws() {
        when(eventRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.findOccurrencesByEventId(1L))
                .isInstanceOf(AcademicEventNotFoundException.class);
    }

    @Test
    void upEs005_findOccurrencesByEventId_mapsOccurrences() {
        RecurringEvent event = recurringEvent();
        Occurrence occ = Occurrence.builder().id(10L).event(event)
                .date(LocalDate.of(2024, 3, 4)).status(OccurrenceStatus.SCHEDULED).build();
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(occurrenceRepository.findByEvent_Id(1L)).thenReturn(List.of(occ));

        List<OccurrenceResponseDto> result = service.findOccurrencesByEventId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getEventId()).isEqualTo(1L);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    // ─── createRecurringEvent ────────────────────────────────────────────────

    @Test
    void upEs006_createRecurringEvent_subjectNotFound_throws() {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2024, 3, 4), null, 1L, 2L);
        when(subjectService.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRecurringEvent(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upEs007_createRecurringEvent_commissionNotFound_throws() {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2024, 3, 4), null, 1L, 2L);
        when(subjectService.findById(1L)).thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(commissionService.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRecurringEvent(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upEs008_createRecurringEvent_success_savesEventAndOccurrences() {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2024, 3, 4),
                LocalDate.of(2024, 3, 18), 1L, 2L);
        Subject subject = Subject.builder().id(1L).build();
        Commission commission = Commission.builder().id(2L).build();
        when(subjectService.findById(1L)).thenReturn(Optional.of(subject));
        when(commissionService.findById(2L)).thenReturn(Optional.of(commission));

        RecurringEvent saved = recurringEvent();
        when(eventRepository.save(any(RecurringEvent.class))).thenReturn(saved);
        AcademicEventResponseDto responseDto = AcademicEventResponseDto.builder().id(1L).type(EventType.RECURRING).build();
        when(mapper.toDto(saved)).thenReturn(responseDto);

        AcademicEventResponseDto result = service.createRecurringEvent(dto);

        assertThat(result).isSameAs(responseDto);
        verify(occurrenceRepository).saveAll(any());
    }

    // ─── findOrCreateRecurringEvent ──────────────────────────────────────────

    @Test
    void upEs009_findOrCreateRecurringEvent_existing_returnsNotCreated() {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2024, 3, 4), null, 1L, 2L);
        RecurringEvent existing = recurringEvent();
        AcademicEventResponseDto dtoResponse = AcademicEventResponseDto.builder().id(1L).build();
        when(recurringEventRepository.findBySubject_IdAndCommission_IdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                1L, 2L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalDate.of(2024, 3, 4), null))
                .thenReturn(Optional.of(existing));
        when(mapper.toDto(existing)).thenReturn(dtoResponse);

        FindOrCreateResult<AcademicEventResponseDto> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isFalse();
        assertThat(result.entity()).isSameAs(dtoResponse);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void upEs010_findOrCreateRecurringEvent_notExisting_createsNew() {
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, LocalDate.of(2024, 3, 4), null, 1L, 2L);
        when(recurringEventRepository.findBySubject_IdAndCommission_IdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                1L, 2L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalDate.of(2024, 3, 4), null))
                .thenReturn(Optional.empty());
        when(subjectService.findById(1L)).thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(commissionService.findById(2L)).thenReturn(Optional.of(Commission.builder().id(2L).build()));
        RecurringEvent saved = recurringEvent();
        when(eventRepository.save(any(RecurringEvent.class))).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(AcademicEventResponseDto.builder().id(1L).build());

        FindOrCreateResult<AcademicEventResponseDto> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isTrue();
    }

    // ─── createUniqueEvent ───────────────────────────────────────────────────

    @Test
    void upEs011_createUniqueEvent_success_savesEventAndOccurrences() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                80, LocalTime.of(8, 0), 180, LocalDate.of(2024, 7, 23), "Examen final");

        UniqueEvent saved = UniqueEvent.builder().id(9L).enrolled(80)
                .startTime(LocalTime.of(8, 0)).duration(Duration.ofMinutes(180))
                .date(LocalDate.of(2024, 7, 23)).description("Examen final").build();
        when(eventRepository.save(any(UniqueEvent.class))).thenReturn(saved);
        AcademicEventResponseDto responseDto = AcademicEventResponseDto.builder().id(9L).type(EventType.UNIQUE_EVENT).build();
        when(mapper.toDto(saved)).thenReturn(responseDto);

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isSameAs(responseDto);
        verify(occurrenceRepository).saveAll(any());
    }
}
