package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.AllocationTestData;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.InvalidCommissionForSubjectException;
import ar.edu.utn.frc.siga.allocation.exception.MissingAcademicReferenceException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEventKind;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.allocation.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicEventServiceImpl")
class AcademicEventServiceImplTest {

    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private RecurringEventRepository recurringEventRepository;
    @Mock
    private UniqueEventRepository uniqueEventRepository;
    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private AcademicEventComposer composer;
    @Mock
    private OccurrenceMapper occurrenceMapper;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;
    @Mock
    private SubjectCommissionService subjectCommissionService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private AllocationValidator allocationValidator;

    private AcademicEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicEventServiceImpl(eventRepository, recurringEventRepository, uniqueEventRepository,
                occurrenceRepository, allocationRepository, composer, occurrenceMapper, subjectService,
                commissionService, subjectCommissionService, allocationService, allocationValidator);
    }

    // ---------- createRecurringEvent ----------

    @Test
    @DisplayName("createRecurringEvent: valida materia y comisión vía fachada (cada una por separado, SIN cruzar que estén vinculadas — ver ADR-011), persiste el evento y sus ocurrencias generadas")
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
        verify(subjectCommissionService, never()).findBySubjectAndCommission(any(), any());

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

    // ---------- findRecurringEvent ----------

    @Test
    @DisplayName("findRecurringEvent: existe uno con la misma sextupla → devuelve su id")
    void findRecurringEventDevuelveIdExistente() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        RecurringEvent existing = AllocationTestData.recurringEvent(7L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.of(existing));

        Long result = service.findRecurringEvent(dto);

        assertThat(result).isEqualTo(7L);
        verify(eventRepository, never()).save(any());
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());
        verify(composer, never()).compose(any(AcademicEvent.class));
    }

    @Test
    @DisplayName("findRecurringEvent: no existe ninguno con esa sextupla → lanza ResourceNotFoundException")
    void findRecurringEventSinExistenteLanzaResourceNotFound() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findRecurringEvent(dto))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepository, never()).save(any());
    }

    // ---------- createUniqueEvent ----------

    @Test
    @DisplayName("createUniqueEvent: persiste el evento (con su description), su única ocurrencia SCHEDULED, y asigna el aula (source MANUAL, sin observation)")
    void createUniqueEventFeliz() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, "Examen final de la materia");
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(AllocationTestData.commissionResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService).findById(1L);
        verify(allocationValidator).validateBusinessHours(dto.startTime(), dto.startTime().plusMinutes(dto.durationMinutes()));

        ArgumentCaptor<UniqueEvent> eventCaptor = ArgumentCaptor.forClass(UniqueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDescription()).isEqualTo(dto.description());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(occurrencesCaptor.capture());
        Occurrence occurrence = occurrencesCaptor.getValue().getFirst();
        assertThat(occurrencesCaptor.getValue()).hasSize(1);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);

        // description es del evento, no una observación de la asignación: allocateManually no la recibe.
        verify(allocationService).allocateManually(eq(occurrence.getId()),
                eq(new AllocateOccurrenceRequestDto(5, null)));
    }

    @Test
    @DisplayName("createUniqueEvent: aula no disponible/solapada → la excepción de allocateManually se propaga (nada queda comiteado a medias)")
    void createUniqueEventAulaNoDisponiblePropagaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.OTRO, 1L, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(eventRepository.save(any())).thenReturn(saved);
        doThrow(new AllocationConflictException("aula ocupada"))
                .when(allocationService).allocateManually(any(), any());

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=OTRO sin subjectId ni commissionId → persiste igual, sin validar ninguna fachada")
    void createUniqueEventOtroSinMateriaNiComision() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.OTRO, null, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=PARCIAL sin subjectId → MissingAcademicReferenceException, no persiste nada")
    void createUniqueEventParcialSinSubjectIdLanzaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.PARCIAL, null, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(MissingAcademicReferenceException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=EXAMEN_FINAL con subjectId pero sin commissionId → persiste igual (commissionId nunca es obligatorio por sí solo)")
    void createUniqueEventExamenFinalSinCommissionIdPersisteIgual() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService, never()).findById(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=OTRO con commissionId pero sin subjectId → MissingAcademicReferenceException (comisión sin materia)")
    void createUniqueEventOtroConCommissionIdSinSubjectIdLanzaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.OTRO, null, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(MissingAcademicReferenceException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUniqueEvent: commissionId no pertenece a subjectId → InvalidCommissionForSubjectException, no persiste nada")
    void createUniqueEventCommissionNoPerteneceASubjectLanzaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 2L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(commissionService.findById(2L)).thenReturn(AllocationTestData.commissionResponseDto(2L));
        when(subjectCommissionService.findBySubjectAndCommission(1L, 2L))
                .thenThrow(ResourceNotFoundException.of("SubjectCommission", "1-2"));

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(InvalidCommissionForSubjectException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUniqueEvent: commissionId sí pertenece a subjectId → persiste sin problema")
    void createUniqueEventCommissionPerteneceASubjectPersiste() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, 5, null);
        UniqueEvent saved = AllocationTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(AllocationTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(AllocationTestData.commissionResponseDto(1L));
        when(subjectCommissionService.findBySubjectAndCommission(1L, 1L))
                .thenReturn(new SubjectCommissionResponseDto(1L, 1L, 1L, 30));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectCommissionService).findBySubjectAndCommission(1L, 1L);
    }

    // ---------- findUniqueEvents ----------

    @Test
    @DisplayName("findUniqueEvents: delega en el composer sobre todos los eventos únicos")
    void findUniqueEventsDelegaEnComposer() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        when(uniqueEventRepository.findAll()).thenReturn(List.of(event));
        when(composer.compose(anyCollection())).thenReturn(List.of(dummyUniqueResponseDto(3L)));

        List<AcademicEventResponseDto> result = service.findUniqueEvents();

        assertThat(result).hasSize(1);
        verify(composer).compose(anyCollection());
    }

    // ---------- updateUniqueEvent ----------

    @Test
    @DisplayName("updateUniqueEvent: evento inexistente → 404, no toca occurrence ni allocation")
    void updateUniqueEventInexistente() {
        when(uniqueEventRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateUniqueEventRequestDto dto = updateDto();

        assertThatThrownBy(() -> service.updateUniqueEvent(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(allocationService, never()).reallocate(any(), any());
        verify(allocationService, never()).allocateManually(any(), any());
    }

    @Test
    @DisplayName("updateUniqueEvent: ya tiene allocation → reallocate (no allocateManually)")
    void updateUniqueEventConAllocationExistenteReasigna() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.ASSIGNED);
        Allocation existing = Allocation.builder().id(50L).occurrence(occurrence).classroomId(5).build();
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        when(allocationRepository.findByOccurrence_Id(10L)).thenReturn(Optional.of(existing));
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        UpdateUniqueEventRequestDto dto = updateDto();
        AcademicEventResponseDto result = service.updateUniqueEvent(3L, dto);

        assertThat(result).isNotNull();
        verify(allocationValidator).validateNotPast(occurrence);
        // description es del evento (ver más abajo), no una observación: reallocate no la recibe.
        verify(allocationService).reallocate(50L, new AllocateOccurrenceRequestDto(dto.classroomId(), null));
        verify(allocationService, never()).allocateManually(any(), any());
        assertThat(occurrence.getDate()).isEqualTo(dto.date());
        assertThat(event.getEnrolled()).isEqualTo(dto.enrolled());
        assertThat(event.getDescription()).isEqualTo(dto.description());
    }

    @Test
    @DisplayName("updateUniqueEvent: sin allocation previa → allocateManually (no reallocate)")
    void updateUniqueEventSinAllocationAsignaManualmente() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.SCHEDULED);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        when(allocationRepository.findByOccurrence_Id(10L)).thenReturn(Optional.empty());
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        UpdateUniqueEventRequestDto dto = updateDto();
        service.updateUniqueEvent(3L, dto);

        verify(allocationService).allocateManually(10L, new AllocateOccurrenceRequestDto(dto.classroomId(), null));
        verify(allocationService, never()).reallocate(any(), any());
    }

    @Test
    @DisplayName("updateUniqueEvent: occurrence ya pasada → propaga la excepción del validator, sin escribir")
    void updateUniqueEventOccurrencePasada() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2020, 1, 1), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.ASSIGNED);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        doThrow(new AllocationConflictException("ya ocurrió")).when(allocationValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.updateUniqueEvent(3L, updateDto()))
                .isInstanceOf(AllocationConflictException.class);
        verify(allocationService, never()).reallocate(any(), any());
        verify(allocationService, never()).allocateManually(any(), any());
    }

    // ---------- cancelUniqueEvent ----------

    @Test
    @DisplayName("cancelUniqueEvent: pasa la única occurrence a CANCELLED")
    void cancelUniqueEventFeliz() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.ASSIGNED);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));

        service.cancelUniqueEvent(3L);

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelUniqueEvent: evento inexistente → 404")
    void cancelUniqueEventInexistente() {
        when(uniqueEventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelUniqueEvent(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelUniqueEvent: occurrence ya pasada → propaga la excepción del validator")
    void cancelUniqueEventOccurrencePasada() {
        UniqueEvent event = AllocationTestData.uniqueEvent(3L, LocalDate.of(2020, 1, 1), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.ASSIGNED);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        doThrow(new AllocationConflictException("ya ocurrió")).when(allocationValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.cancelUniqueEvent(3L))
                .isInstanceOf(AllocationConflictException.class);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED);
    }

    // ---------- findUnassignedEvents ----------

    @Test
    @DisplayName("findUnassignedEvents: 'to' anterior a 'from' → InvalidDateRangeException")
    void findUnassignedRangoInvalido() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = from.minusDays(1);

        assertThatThrownBy(() -> service.findUnassignedEvents(from, to, false))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("findUnassignedEvents: 'from' null → usa hoy como default")
    void findUnassignedFromNullUsaHoy() {
        when(occurrenceRepository.findByStatusAndDateGreaterThanEqualOrderByEvent_IdAscDateAsc(
                eq(OccurrenceStatus.SCHEDULED), eq(LocalDate.now())))
                .thenReturn(List.of());
        when(composer.compose(anyCollection())).thenReturn(List.of());

        service.findUnassignedEvents(null, null, false);

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
            Collection<AcademicEvent> events = invocation.getArgument(0);
            return events.stream().map(e -> dummyRecurringResponseDto(e.getId())).toList();
        });

        List<AcademicEventResponseDto> result = service.findUnassignedEvents(from, to, true);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findUnassignedEventIds: mismo criterio que findUnassignedEvents pero sin componer DTOs")
    void findUnassignedEventIdsNoComponeDto() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        RecurringEvent eventA = AllocationTestData.recurringEvent(1L, DayOfWeek.MONDAY, from, to);
        RecurringEvent eventB = AllocationTestData.recurringEvent(2L, DayOfWeek.TUESDAY, from, to);
        Occurrence occA = AllocationTestData.occurrence(10L, eventA, from, OccurrenceStatus.SCHEDULED);
        Occurrence occB = AllocationTestData.occurrence(11L, eventB, from.plusDays(1), OccurrenceStatus.SCHEDULED);

        when(occurrenceRepository.findByStatusAndDateBetweenOrderByEvent_IdAscDateAsc(OccurrenceStatus.SCHEDULED, from, to))
                .thenReturn(List.of(occA, occB));

        List<Long> result = service.findUnassignedEventIds(from, to, true);

        assertThat(result).containsExactly(1L, 2L);
        verifyNoInteractions(composer);
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
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, 20,
                LocalTime.of(10, 0), 60, LocalDate.of(2026, 3, 10), "evento especial",
                null, null, OccurrenceStatus.ASSIGNED, null, 0, null);
    }

    private UpdateUniqueEventRequestDto updateDto() {
        return new UpdateUniqueEventRequestDto(
                UniqueEventKind.PARCIAL, 1L, 1L, LocalDate.of(2026, 3, 15),
                LocalTime.of(11, 0), 90, 25, 7, "descripcion actualizada");
    }
}
