package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.AllocationTestData;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicEventServiceImpl")
class AcademicEventServiceImplTest {

    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private RecurringEventRepository recurringEventRepository;
    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private AcademicEventComposer composer;
    @Mock
    private OccurrenceMapper occurrenceMapper;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;

    private AcademicEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicEventServiceImpl(eventRepository, recurringEventRepository, occurrenceRepository,
                composer, occurrenceMapper, subjectService, commissionService);
    }

    // ---------- createRecurringEvent ----------

    @Test
    @DisplayName("createRecurringEvent: valida materia y comisión vía fachada, persiste el evento y sus ocurrencias generadas")
    void createRecurringEventFeliz() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19)); // 3 semanas → 3 ocurrencias
        RecurringEvent saved = AllocationTestData.recurringEvent(1L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(AllocationTestData.commissionResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyRecurringResponseDto(1L));

        AcademicEventResponseDto result = service.createRecurringEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService).findById(1L);

        ArgumentCaptor<RecurringEvent> eventCaptor = ArgumentCaptor.forClass(RecurringEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        RecurringEvent persisted = eventCaptor.getValue();
        assertThat(persisted.getDayOfWeek()).isEqualTo(dto.dayOfWeek());
        assertThat(persisted.getStartDate()).isEqualTo(dto.startDate());
        assertThat(persisted.getEndDate()).isEqualTo(dto.endDate());
        assertThat(persisted.getSubjectId()).isEqualTo(dto.subjectId());
        assertThat(persisted.getCommissionId()).isEqualTo(dto.commissionId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(occurrencesCaptor.capture());
        List<Occurrence> occurrences = occurrencesCaptor.getValue();
        assertThat(occurrences).hasSize(3);
        assertThat(occurrences).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED));
    }

    // ---------- findOrCreateRecurringEvent ----------

    @Test
    @DisplayName("findOrCreateRecurringEvent: existe uno con la misma sextupla → lo reusa, no crea")
    void findOrCreateReusaExistente() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        RecurringEvent existing = AllocationTestData.recurringEvent(7L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.of(existing));
        when(composer.compose(existing)).thenReturn(dummyRecurringResponseDto(7L));

        FindOrCreateResult<AcademicEventResponseDto> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isFalse();
        assertThat(result.value().id()).isEqualTo(7L);
        verify(eventRepository, never()).save(any());
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());
    }

    @Test
    @DisplayName("findOrCreateRecurringEvent: no existe ninguno con esa sextupla → lo crea")
    void findOrCreateCreaNuevo() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.empty());
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(AllocationTestData.commissionResponseDto(1L));
        RecurringEvent saved = AllocationTestData.recurringEvent(9L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyRecurringResponseDto(9L));

        FindOrCreateResult<AcademicEventResponseDto> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isTrue();
        assertThat(result.value().id()).isEqualTo(9L);
        verify(eventRepository).save(any());
    }

    // ---------- createUniqueEvent ----------

    @Test
    @DisplayName("createUniqueEvent: persiste el evento y su única ocurrencia SCHEDULED")
    void createUniqueEventFeliz() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                20, LocalTime.of(10, 0), 60, LocalDate.of(2026, 3, 10), "evento especial");
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(), Duration.ofMinutes(dto.durationMinutes()));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(occurrencesCaptor.capture());
        assertThat(occurrencesCaptor.getValue()).hasSize(1);
        assertThat(occurrencesCaptor.getValue().getFirst().getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
    }

    // ---------- findUnassignedEvents ----------

    @Test
    @DisplayName("findUnassignedEvents: 'to' anterior a 'from' → InvalidDateRangeException")
    void findUnassignedRangoInvalido() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = from.minusDays(1);

        assertThatThrownBy(() -> service.findUnassignedEvents(from, to))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("findUnassignedEvents: 'from' null → usa hoy como default")
    void findUnassignedFromNullUsaHoy() {
        when(occurrenceRepository.findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
                eq(OccurrenceStatus.SCHEDULED), eq(LocalDate.now())))
                .thenReturn(List.of());
        when(composer.compose(anyCollection())).thenReturn(List.of());

        service.findUnassignedEvents(null, null);

        verify(occurrenceRepository).findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
                OccurrenceStatus.SCHEDULED, LocalDate.now());
    }

    @Test
    @DisplayName("findUnassignedEvents: varias ocurrencias del mismo evento se dedupean preservando el orden de aparición")
    void findUnassignedDedupePreservandoOrden() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        RecurringEvent eventA = AllocationTestData.recurringEvent(1L, DayOfWeek.MONDAY, from, to);
        RecurringEvent eventB = AllocationTestData.recurringEvent(2L, DayOfWeek.TUESDAY, from, to);
        Occurrence occA1 = AllocationTestData.occurrence(10L, eventA, from, OccurrenceStatus.SCHEDULED);
        Occurrence occB1 = AllocationTestData.occurrence(11L, eventB, from.plusDays(1), OccurrenceStatus.SCHEDULED);
        Occurrence occA2 = AllocationTestData.occurrence(12L, eventA, from.plusDays(7), OccurrenceStatus.SCHEDULED);

        when(occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(OccurrenceStatus.SCHEDULED, from, to))
                .thenReturn(List.of(occA1, occB1, occA2));
        when(composer.compose(anyCollection())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Collection<AcademicEvent> events = invocation.getArgument(0);
            return events.stream().<AcademicEventResponseDto>map(e -> dummyRecurringResponseDto(e.getId())).toList();
        });

        List<AcademicEventResponseDto> result = service.findUnassignedEvents(from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    // ---------- findAll / findById / findOccurrencesByEventId ----------

    @Test
    @DisplayName("findAll: delega en el composer sobre todos los eventos")
    void findAllDelegaEnComposer() {
        when(eventRepository.findAll()).thenReturn(List.of());
        when(composer.compose(anyCollection())).thenReturn(List.of());

        List<AcademicEventResponseDto> result = service.findAll();

        assertThat(result).isEmpty();
        verify(composer).compose(anyCollection());
    }

    @Test
    @DisplayName("findById: evento inexistente → 404")
    void findByIdInexistente() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findById: evento existente → lo compone")
    void findByIdExistente() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), null);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(composer.compose(event)).thenReturn(dummyRecurringResponseDto(1L));

        AcademicEventResponseDto result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findOccurrencesByEventId: evento inexistente → 404")
    void findOccurrencesEventoInexistente() {
        when(eventRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findOccurrencesByEventId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findOccurrencesByEventId: evento existente → mapea sus ocurrencias")
    void findOccurrencesEventoExistente() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), null);
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, LocalDate.of(2026, 1, 5), OccurrenceStatus.SCHEDULED);
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(occurrenceRepository.findByEvent_Id(1L)).thenReturn(List.of(occurrence));
        when(occurrenceMapper.toDto(occurrence)).thenReturn(
                new OccurrenceResponseDto(10L, 1L, occurrence.getDate(), OccurrenceStatus.SCHEDULED,
                        LocalTime.of(8, 0), LocalTime.of(9, 30)));

        List<OccurrenceResponseDto> result = service.findOccurrencesByEventId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
    }

    // ---------- helpers ----------

    private CreateRecurringEventRequestDto recurringDto(DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate) {
        return new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, dayOfWeek, startDate, endDate, 1L, 1L);
    }

    private RecurringEventResponseDto dummyRecurringResponseDto(Long id) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, LocalTime.of(8, 0), 90,
                DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19), null, null);
    }

    private UniqueEventResponseDto dummyUniqueResponseDto(Long id) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, 20, LocalTime.of(10, 0), 60,
                LocalDate.of(2026, 3, 10), "evento especial");
    }
}
