package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.events.EventTestData;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.request.UpdateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.SysacadRecurringEventRefDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.exception.InvalidCommissionForSubjectException;
import ar.edu.utn.frc.siga.events.exception.MissingAcademicReferenceException;
import ar.edu.utn.frc.siga.events.exception.OccurrenceAlreadyPastException;
import ar.edu.utn.frc.siga.events.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.events.mapper.OccurrenceMapper;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.repository.RecurringEventRepository;
import ar.edu.utn.frc.siga.events.repository.UniqueEventRepository;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private AcademicEventComposer composer;
    @Mock
    private OccurrenceMapper occurrenceMapper;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;
    @Mock
    private EventScheduleValidator eventScheduleValidator;

    private AcademicEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AcademicEventServiceImpl(eventRepository, recurringEventRepository, uniqueEventRepository,
                occurrenceRepository, composer, occurrenceMapper, subjectService, commissionService, eventScheduleValidator);
    }


    @Test
    @DisplayName("createRecurringEvent: valida materia y comisión vía fachada (cada una por separado, SIN cruzar que estén vinculadas — ver ADR-011), persiste el evento y sus ocurrencias generadas")
    void createRecurringEventFeliz() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        RecurringEvent saved = EventTestData.recurringEvent(1L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(EventTestData.commissionResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyRecurringResponseDto(1L));

        AcademicEventResponseDto result = service.createRecurringEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService).findById(1L);
        verifyNoInteractions(eventScheduleValidator);

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
        assertThat(occurrences).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM));
    }


    @Test
    @DisplayName("findOrCreateRecurringEvent: existe uno con la misma sextupla → lo reusa, no crea")
    void findOrCreateReusaExistente() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        RecurringEvent existing = EventTestData.recurringEvent(7L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.of(existing));

        FindOrCreateResult<Long> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isFalse();
        assertThat(result.value()).isEqualTo(7L);
        verify(eventRepository, never()).save(any());
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());
        verify(composer, never()).compose(any(AcademicEvent.class));
    }

    @Test
    @DisplayName("findOrCreateRecurringEvent: no existe ninguno con esa sextupla → lo crea")
    void findOrCreateCreaNuevo() {
        CreateRecurringEventRequestDto dto = recurringDto(DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19));
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                dto.subjectId(), dto.commissionId(), dto.dayOfWeek(), dto.startTime(), dto.startDate(), dto.endDate()))
                .thenReturn(Optional.empty());
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(EventTestData.commissionResponseDto(1L));
        RecurringEvent saved = EventTestData.recurringEvent(9L, dto.dayOfWeek(), dto.startDate(), dto.endDate());
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyRecurringResponseDto(9L));

        FindOrCreateResult<Long> result = service.findOrCreateRecurringEvent(dto);

        assertThat(result.created()).isTrue();
        assertThat(result.value()).isEqualTo(9L);
        verify(eventRepository).save(any());
    }


    @Test
    @DisplayName("findRecurringEventId: existe evento con esa clave natural → devuelve su id, sin crear nada")
    void findRecurringEventIdEncuentraExistente() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);
        RecurringEvent existing = EventTestData.recurringEvent(7L, DayOfWeek.MONDAY, startDate, endDate);
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                1L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), startDate, endDate))
                .thenReturn(Optional.of(existing));

        Optional<Long> result = service.findRecurringEventId(
                1L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), startDate, endDate);

        assertThat(result).contains(7L);
        verify(eventRepository, never()).save(any());
        verify(subjectService, never()).findById(any());
    }

    @Test
    @DisplayName("findRecurringEventId: no existe evento con esa clave natural → vacío, no lo crea (a diferencia de findOrCreateRecurringEvent)")
    void findRecurringEventIdVacioCuandoNoExiste() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);
        when(recurringEventRepository.findBySubjectIdAndCommissionIdAndDayOfWeekAndStartTimeAndStartDateAndEndDate(
                1L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), startDate, endDate))
                .thenReturn(Optional.empty());

        Optional<Long> result = service.findRecurringEventId(
                1L, 1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), startDate, endDate);

        assertThat(result).isEmpty();
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("findRecurringEventsBySubjectAndCommission: consulta el cursado vigente (a partir de hoy) y devuelve los slots recurrentes compuestos")
    void findRecurringEventsBySubjectAndCommissionDevuelveSlots() {
        RecurringEvent lunes = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
        RecurringEvent miercoles = EventTestData.recurringEvent(2L, DayOfWeek.WEDNESDAY,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
        when(recurringEventRepository.findActiveBySubjectAndCommission(eq(1L), eq(9L), any(LocalDate.class)))
                .thenReturn(List.of(lunes, miercoles));
        when(composer.compose(anyCollection()))
                .thenReturn(List.of(dummyRecurringResponseDto(1L), dummyRecurringResponseDto(2L)));

        List<RecurringEventResponseDto> result = service.findRecurringEventsBySubjectAndCommission(1L, 9L);

        assertThat(result).extracting(RecurringEventResponseDto::id).containsExactly(1L, 2L);
        verify(recurringEventRepository).findActiveBySubjectAndCommission(eq(1L), eq(9L), any(LocalDate.class));
    }

    @Test
    @DisplayName("findRecurringEventsBySubjectAndCommission: sin cursado vigente → lista vacía")
    void findRecurringEventsBySubjectAndCommissionSinCursado() {
        when(recurringEventRepository.findActiveBySubjectAndCommission(eq(1L), eq(9L), any(LocalDate.class)))
                .thenReturn(List.of());
        when(composer.compose(anyCollection())).thenReturn(List.of());

        assertThat(service.findRecurringEventsBySubjectAndCommission(1L, 9L)).isEmpty();
    }

    @Test
    @DisplayName("findClassOccurrences: junta las ocurrencias de los eventos vigentes desde 'from' y las devuelve ordenadas por fecha")
    void findClassOccurrencesOrdenadasPorFecha() {
        RecurringEvent lunes = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
        LocalDate from = LocalDate.of(2026, 4, 1);
        when(recurringEventRepository.findActiveBySubjectAndCommission(eq(1L), eq(9L), any(LocalDate.class)))
                .thenReturn(List.of(lunes));
        Occurrence o1 = EventTestData.occurrence(11L, lunes, LocalDate.of(2026, 4, 20), OccurrenceStatus.NEEDS_ROOM);
        Occurrence o2 = EventTestData.occurrence(12L, lunes, LocalDate.of(2026, 4, 6), OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findByEvent_IdInAndDateGreaterThanEqual(List.of(1L), from))
                .thenReturn(List.of(o1, o2));
        when(occurrenceMapper.toDto(o1)).thenReturn(new OccurrenceResponseDto(11L, 1L, o1.getDate(),
                OccurrenceStatus.NEEDS_ROOM, LocalTime.of(8, 0), LocalTime.of(9, 30)));
        when(occurrenceMapper.toDto(o2)).thenReturn(new OccurrenceResponseDto(12L, 1L, o2.getDate(),
                OccurrenceStatus.NEEDS_ROOM, LocalTime.of(8, 0), LocalTime.of(9, 30)));

        List<OccurrenceResponseDto> result = service.findClassOccurrences(1L, 9L, from);

        assertThat(result).extracting(OccurrenceResponseDto::date)
                .containsExactly(LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 20));
    }

    @Test
    @DisplayName("findClassOccurrences: sin cursado vigente → lista vacía, no consulta ocurrencias")
    void findClassOccurrencesSinCursado() {
        when(recurringEventRepository.findActiveBySubjectAndCommission(eq(1L), eq(9L), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThat(service.findClassOccurrences(1L, 9L, LocalDate.of(2026, 4, 1))).isEmpty();
        verify(occurrenceRepository, never()).findByEvent_IdInAndDateGreaterThanEqual(anyCollection(), any());
    }

    @Test
    @DisplayName("syncRecurringEvent: no existe evento con esa clave natural → lo crea con sysacadHash inicial (hash de la duración escrita) y expande ocurrencias")
    void syncRecurringEventCreaNuevo() {
        SyncRecurringEventCommand cmd = syncCommand(90, 30);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of());
        when(eventRepository.saveAll(any())).thenAnswer(assignSequentialIds(5L));

        UpsertRecurringEventResult result = service.syncRecurringEvent(cmd);

        assertThat(result.eventId()).isEqualTo(5L);
        assertThat(result.created()).isTrue();
        assertThat(result.updated()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        RecurringEvent persisted = captor.getValue().getFirst();
        assertThat(persisted.getEnrolled()).isEqualTo(30);
        assertThat(persisted.getDuration()).isEqualTo(Duration.ofMinutes(90));
        assertThat(persisted.getSysacadEnabled()).isTrue();
        assertThat(persisted.getSyncedAt()).isNotNull();
        assertThat(persisted.getSysacadHash()).isEqualTo(Hashes.sha256Hex(90));

        verify(occurrenceRepository).saveAll(any());
    }

    @Test
    @DisplayName("syncRecurringEvent: evento existente → enrolled se pisa siempre, sin condición")
    void syncRecurringEventPisaEnrolledSiempre() {
        SyncRecurringEventCommand cmd = syncCommand(90, 45);
        RecurringEvent existing = existingSyncedEvent(7L, Duration.ofMinutes(90), Hashes.sha256Hex(90), true);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(existing));

        UpsertRecurringEventResult result = service.syncRecurringEvent(cmd);

        assertThat(result.created()).isFalse();
        assertThat(result.updated()).isTrue();
        assertThat(existing.getEnrolled()).isEqualTo(45);
        assertThat(existing.getSyncedAt()).isNotNull();
        assertThat(existing.getSysacadEnabled()).isTrue();
        assertThat(savedRecurringEvents()).containsExactly(existing);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occCaptor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(occCaptor.capture());
        assertThat(occCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("syncRecurringEvent: duration sin drift (nadie la tocó desde el último sync) y cambió en SysAcad → se pisa y se actualiza el hash")
    void syncRecurringEventPisaDurationSinDrift() {
        SyncRecurringEventCommand cmd = syncCommand(120, 30);
        RecurringEvent existing = existingSyncedEvent(7L, Duration.ofMinutes(90), Hashes.sha256Hex(90), true);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(existing));

        UpsertRecurringEventResult result = service.syncRecurringEvent(cmd);

        assertThat(result.updated()).isTrue();
        assertThat(existing.getDuration()).isEqualTo(Duration.ofMinutes(120));
        assertThat(existing.getSysacadHash()).isEqualTo(Hashes.sha256Hex(120));
    }

    @Test
    @DisplayName("syncRecurringEvent: duration editada a mano fuera del sync (drift) y el valor entrante difiere → NO se pisa, la edición manual se preserva")
    void syncRecurringEventNoPisaDurationConDrift() {
        SyncRecurringEventCommand cmd = syncCommand(120, 30);
        RecurringEvent existing = existingSyncedEvent(7L, Duration.ofMinutes(100), Hashes.sha256Hex(90), true);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(existing));

        UpsertRecurringEventResult result = service.syncRecurringEvent(cmd);

        assertThat(result.updated()).isTrue();
        assertThat(existing.getDuration()).isEqualTo(Duration.ofMinutes(100));
        assertThat(existing.getSysacadHash()).isEqualTo(Hashes.sha256Hex(90));
        assertThat(existing.getEnrolled()).isEqualTo(30);
        assertThat(savedRecurringEvents()).containsExactly(existing);
    }

    @Test
    @DisplayName("syncRecurringEvent: primer resync inmediato tras la creación por sync no lee drift falso (sysacadHash ya quedó seteado al crear)")
    void syncRecurringEventPrimerResyncNoFalsoDrift() {
        SyncRecurringEventCommand cmd = syncCommand(90, 30);
        RecurringEvent justCreated = existingSyncedEvent(9L, Duration.ofMinutes(90), Hashes.sha256Hex(90), true);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(justCreated));

        UpsertRecurringEventResult result = service.syncRecurringEvent(cmd);

        assertThat(result.updated()).isTrue();
        assertThat(justCreated.getDuration()).isEqualTo(Duration.ofMinutes(90));
        assertThat(justCreated.getSysacadHash()).isEqualTo(Hashes.sha256Hex(90));
        assertThat(savedRecurringEvents()).containsExactly(justCreated);
    }

    @Test
    @DisplayName("syncRecurringEvents: una sola lectura de prefetch del índice sync-owned para N comandos")
    void syncRecurringEventsPrefetchUnaSolaVez() {
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of());
        when(eventRepository.saveAll(any())).thenAnswer(assignSequentialIds(1L));

        service.syncRecurringEvents(List.of(
                syncCommand(DayOfWeek.MONDAY, 90, 30),
                syncCommand(DayOfWeek.TUESDAY, 90, 30),
                syncCommand(DayOfWeek.WEDNESDAY, 90, 30)));

        verify(recurringEventRepository, times(1)).findBySysacadHashIsNotNull();
    }

    @Test
    @DisplayName("syncRecurringEvents: dos comandos con la misma clave natural y evento inexistente → un solo insert, el segundo reconcilia el objeto en memoria")
    void syncRecurringEventsMismaClaveUnSoloInsert() {
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of());
        when(eventRepository.saveAll(any())).thenAnswer(assignSequentialIds(3L));

        List<UpsertRecurringEventResult> results = service.syncRecurringEvents(List.of(
                syncCommand(90, 30), syncCommand(120, 45)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        RecurringEvent inserted = captor.getValue().getFirst();
        assertThat(inserted.getEnrolled()).isEqualTo(45);
        assertThat(inserted.getDuration()).isEqualTo(Duration.ofMinutes(120));

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(new UpsertRecurringEventResult(3L, true, false));
        assertThat(results.get(1)).isEqualTo(new UpsertRecurringEventResult(3L, false, true));
    }

    @Test
    @DisplayName("syncRecurringEvents: mezcla create + update en una sola llamada → sólo los nuevos van a eventRepository.saveAll, resultados con created/updated correctos")
    void syncRecurringEventsMezclaCreateUpdate() {
        RecurringEvent existing = existingSyncedEvent(7L, Duration.ofMinutes(90), Hashes.sha256Hex(90), true);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(existing));
        when(eventRepository.saveAll(any())).thenAnswer(assignSequentialIds(11L));

        List<UpsertRecurringEventResult> results = service.syncRecurringEvents(List.of(
                syncCommand(90, 55),
                syncCommand(DayOfWeek.FRIDAY, 60, 20)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecurringEvent>> createdCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(createdCaptor.capture());
        assertThat(createdCaptor.getValue()).hasSize(1);
        assertThat(createdCaptor.getValue().getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(existing.getEnrolled()).isEqualTo(55);

        assertThat(results).containsExactly(
                new UpsertRecurringEventResult(7L, false, true),
                new UpsertRecurringEventResult(11L, true, false));
        verify(occurrenceRepository).saveAll(any());
    }

    @Test
    @DisplayName("syncRecurringEvents: el orden de los resultados coincide con el orden de los comandos de entrada")
    void syncRecurringEventsRespetaOrden() {
        RecurringEvent wednesday = syncedEventOn(3L, DayOfWeek.WEDNESDAY);
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(wednesday));
        when(eventRepository.saveAll(any())).thenAnswer(assignSequentialIds(20L));

        List<UpsertRecurringEventResult> results = service.syncRecurringEvents(List.of(
                syncCommand(DayOfWeek.MONDAY, 90, 30),
                syncCommand(DayOfWeek.WEDNESDAY, 90, 30),
                syncCommand(DayOfWeek.FRIDAY, 90, 30)));

        assertThat(results).extracting(UpsertRecurringEventResult::eventId).containsExactly(20L, 3L, 21L);
        assertThat(results).extracting(UpsertRecurringEventResult::created).containsExactly(true, false, true);
    }


    @Test
    @DisplayName("markRecurringEventsAbsent: marca sysacadEnabled=false sólo en los sync-owned ausentes de la corrida, sin tocar los presentes, los ya deshabilitados, ni ocurrencias/eventos")
    void markRecurringEventsAbsentMarcaSoloAusentesSyncOwned() {
        RecurringEvent present = existingSyncedEvent(1L, Duration.ofMinutes(90), Hashes.sha256Hex(90), true);
        RecurringEvent absent = existingSyncedEvent(2L, Duration.ofMinutes(60), Hashes.sha256Hex(60), true);
        RecurringEvent alreadyDisabled = existingSyncedEvent(3L, Duration.ofMinutes(45), Hashes.sha256Hex(45), false);
        when(recurringEventRepository.findBySysacadHashIsNotNull())
                .thenReturn(List.of(present, absent, alreadyDisabled));

        int affected = service.markRecurringEventsAbsent(Set.of(1L));

        assertThat(affected).isEqualTo(1);
        assertThat(absent.getSysacadEnabled()).isFalse();
        assertThat(absent.getSyncedAt()).isNotNull();
        assertThat(present.getSysacadEnabled()).isTrue();
        verify(recurringEventRepository).save(absent);
        verify(recurringEventRepository, never()).save(present);
        verify(recurringEventRepository, never()).save(alreadyDisabled);
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(occurrenceRepository);
    }

    @Test
    @DisplayName("markRecurringEventsAbsent: sin eventos sync-owned, no marca nada")
    void markRecurringEventsAbsentSinSyncOwnedNoHaceNada() {
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of());

        int affected = service.markRecurringEventsAbsent(Set.of(1L));

        assertThat(affected).isZero();
        verify(recurringEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("findSysacadRecurringEvents: mapea cada evento sync-owned a una ref liviana, campo por campo")
    void findSysacadRecurringEventsMapeaCampoPorCampo() {
        RecurringEvent first = RecurringEvent.builder()
                .id(10L)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .subjectId(1L)
                .commissionId(2L)
                .sysacadHash(Hashes.sha256Hex(90))
                .build();
        RecurringEvent second = RecurringEvent.builder()
                .id(11L)
                .enrolled(45)
                .startTime(LocalTime.of(14, 30))
                .duration(Duration.ofMinutes(120))
                .dayOfWeek(DayOfWeek.WEDNESDAY)
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 12, 15))
                .subjectId(3L)
                .commissionId(4L)
                .sysacadHash(Hashes.sha256Hex(120))
                .build();
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of(first, second));

        List<SysacadRecurringEventRefDto> result = service.findSysacadRecurringEvents();

        assertThat(result).containsExactly(
                new SysacadRecurringEventRefDto(10L, 1L, 2L, DayOfWeek.MONDAY, LocalTime.of(8, 0),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31)),
                new SysacadRecurringEventRefDto(11L, 3L, 4L, DayOfWeek.WEDNESDAY, LocalTime.of(14, 30),
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15)));
    }

    @Test
    @DisplayName("findSysacadRecurringEvents: sin eventos sync-owned → lista vacía")
    void findSysacadRecurringEventsSinSyncOwned() {
        when(recurringEventRepository.findBySysacadHashIsNotNull()).thenReturn(List.of());

        assertThat(service.findSysacadRecurringEvents()).isEmpty();
    }


    @Test
    @DisplayName("createUniqueEvent: persiste el evento (con su description) y su única ocurrencia SCHEDULED, sin aula")
    void createUniqueEventFeliz() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, "Examen final de la materia");
        UniqueEvent saved = EventTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(EventTestData.commissionResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService).findById(1L);
        verify(eventScheduleValidator).validateBusinessHours(dto.startTime(), dto.startTime().plusMinutes(dto.durationMinutes()));

        ArgumentCaptor<UniqueEvent> eventCaptor = ArgumentCaptor.forClass(UniqueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDescription()).isEqualTo(dto.description());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Occurrence>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(occurrencesCaptor.capture());
        Occurrence occurrence = occurrencesCaptor.getValue().getFirst();
        assertThat(occurrencesCaptor.getValue()).hasSize(1);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("createUniqueEvent: eventScheduleValidator rechaza la referencia académica → la excepción se propaga, no persiste nada")
    void createUniqueEventReferenciaAcademicaInvalidaPropagaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.PARCIAL, null, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, null);
        doThrow(new MissingAcademicReferenceException("subjectId es obligatorio"))
                .when(eventScheduleValidator).validateAcademicReference(dto.eventType(), dto.subjectId(), dto.commissionId());

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(MissingAcademicReferenceException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=OTRO sin subjectId ni commissionId → persiste igual, sin validar ninguna fachada")
    void createUniqueEventOtroSinMateriaNiComision() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.OTRO, null, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, null);
        UniqueEvent saved = EventTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService, never()).findById(any());
        verify(commissionService, never()).findById(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventType=EXAMEN_FINAL con subjectId pero sin commissionId → persiste igual (commissionId nunca es obligatorio por sí solo)")
    void createUniqueEventExamenFinalSinCommissionIdPersisteIgual() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, null, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, null);
        UniqueEvent saved = EventTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(subjectService).findById(1L);
        verify(commissionService, never()).findById(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventScheduleValidator rechaza la comisión (no pertenece a la materia) → la excepción se propaga, no persiste nada")
    void createUniqueEventCommissionNoPerteneceASubjectLanzaExcepcion() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 2L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, null);
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(commissionService.findById(2L)).thenReturn(EventTestData.commissionResponseDto(2L));
        doThrow(new InvalidCommissionForSubjectException("la comisión 2 no pertenece a la materia 1"))
                .when(eventScheduleValidator).validateCommissionBelongsToSubject(1L, 2L);

        assertThatThrownBy(() -> service.createUniqueEvent(dto))
                .isInstanceOf(InvalidCommissionForSubjectException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUniqueEvent: eventScheduleValidator no objeta la comisión → persiste sin problema")
    void createUniqueEventCommissionPerteneceASubjectPersiste() {
        CreateUniqueEventRequestDto dto = new CreateUniqueEventRequestDto(
                UniqueEventKind.EXAMEN_FINAL, 1L, 1L, LocalDate.of(2026, 3, 10),
                LocalTime.of(10, 0), 60, 20, null);
        UniqueEvent saved = EventTestData.uniqueEvent(3L, dto.date(), dto.startTime(),
                Duration.ofMinutes(dto.durationMinutes()));
        when(subjectService.findById(1L)).thenReturn(EventTestData.subjectResponseDto(1L));
        when(commissionService.findById(1L)).thenReturn(EventTestData.commissionResponseDto(1L));
        when(eventRepository.save(any())).thenReturn(saved);
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        AcademicEventResponseDto result = service.createUniqueEvent(dto);

        assertThat(result).isNotNull();
        verify(eventScheduleValidator).validateCommissionBelongsToSubject(1L, 1L);
    }


    @Test
    @DisplayName("findUniqueEvents: delega en el composer sobre todos los eventos únicos")
    void findUniqueEventsDelegaEnComposer() {
        UniqueEvent event = EventTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        when(uniqueEventRepository.findAll()).thenReturn(List.of(event));
        when(composer.compose(anyCollection())).thenReturn(List.of(dummyUniqueResponseDto(3L)));

        List<AcademicEventResponseDto> result = service.findUniqueEvents();

        assertThat(result).hasSize(1);
        verify(composer).compose(anyCollection());
    }


    @Test
    @DisplayName("updateUniqueEvent: evento inexistente → 404")
    void updateUniqueEventInexistente() {
        when(uniqueEventRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateUniqueEventRequestDto dto = updateDto();

        assertThatThrownBy(() -> service.updateUniqueEvent(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateUniqueEvent: actualiza los campos del evento y la fecha de su occurrence")
    void updateUniqueEventFeliz() {
        UniqueEvent event = EventTestData.uniqueEvent(3L, LocalDate.of(2026, 3, 10), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = EventTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.NEEDS_ROOM);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        when(composer.compose(any(AcademicEvent.class))).thenReturn(dummyUniqueResponseDto(3L));

        UpdateUniqueEventRequestDto dto = updateDto();
        AcademicEventResponseDto result = service.updateUniqueEvent(3L, dto);

        assertThat(result).isNotNull();
        verify(eventScheduleValidator).validateNotPast(occurrence);
        assertThat(occurrence.getDate()).isEqualTo(dto.date());
        assertThat(event.getEnrolled()).isEqualTo(dto.enrolled());
        assertThat(event.getDescription()).isEqualTo(dto.description());
    }

    @Test
    @DisplayName("updateUniqueEvent: occurrence ya pasada → propaga la excepción del validator, sin escribir")
    void updateUniqueEventOccurrencePasada() {
        UniqueEvent event = EventTestData.uniqueEvent(3L, LocalDate.of(2020, 1, 1), LocalTime.of(10, 0), Duration.ofMinutes(60));
        Occurrence occurrence = EventTestData.occurrence(10L, event, event.getDate(), OccurrenceStatus.NEEDS_ROOM);
        when(uniqueEventRepository.findById(3L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_Id(3L)).thenReturn(List.of(occurrence));
        doThrow(new OccurrenceAlreadyPastException("ya ocurrió")).when(eventScheduleValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.updateUniqueEvent(3L, updateDto()))
                .isInstanceOf(OccurrenceAlreadyPastException.class);
    }


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
        RecurringEvent event = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), null);
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
        RecurringEvent event = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), null);
        Occurrence occurrence = EventTestData.occurrence(10L, event, LocalDate.of(2026, 1, 5), OccurrenceStatus.NEEDS_ROOM);
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(occurrenceRepository.findByEvent_Id(1L)).thenReturn(List.of(occurrence));
        when(occurrenceMapper.toDto(occurrence)).thenReturn(
                new OccurrenceResponseDto(10L, 1L, occurrence.getDate(), OccurrenceStatus.NEEDS_ROOM,
                        LocalTime.of(8, 0), LocalTime.of(9, 30)));

        List<OccurrenceResponseDto> result = service.findOccurrencesByEventId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
    }


    private CreateRecurringEventRequestDto recurringDto(DayOfWeek dayOfWeek, LocalDate startDate, LocalDate endDate) {
        return new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, dayOfWeek, startDate, endDate, 1L, 1L);
    }

    private SyncRecurringEventCommand syncCommand(int durationMinutes, int enrolled) {
        return syncCommand(DayOfWeek.MONDAY, durationMinutes, enrolled);
    }

    private SyncRecurringEventCommand syncCommand(DayOfWeek dayOfWeek, int durationMinutes, int enrolled) {
        return new SyncRecurringEventCommand(1L, 1L, dayOfWeek, LocalTime.of(8, 0), durationMinutes,
                enrolled, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
    }

    private RecurringEvent existingSyncedEvent(Long id, Duration duration, String sysacadHash, boolean sysacadEnabled) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(duration)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .subjectId(1L)
                .commissionId(1L)
                .sysacadHash(sysacadHash)
                .sysacadEnabled(sysacadEnabled)
                .build();
    }

    private RecurringEvent syncedEventOn(Long id, DayOfWeek dayOfWeek) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(30)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .dayOfWeek(dayOfWeek)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .subjectId(1L)
                .commissionId(1L)
                .sysacadHash(Hashes.sha256Hex(90))
                .sysacadEnabled(true)
                .build();
    }

    private Iterable<RecurringEvent> savedRecurringEvents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RecurringEvent>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(recurringEventRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static Answer<List<RecurringEvent>> assignSequentialIds(long firstId) {
        return invocation -> {
            List<RecurringEvent> events = invocation.getArgument(0);
            long id = firstId;
            for (RecurringEvent event : events) {
                ReflectionTestUtils.setField(event, "id", id++);
            }
            return events;
        };
    }

    private RecurringEventResponseDto dummyRecurringResponseDto(Long id) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, LocalTime.of(8, 0), 90,
                DayOfWeek.MONDAY, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19), null, null);
    }

    private UniqueEventResponseDto dummyUniqueResponseDto(Long id) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, 20,
                LocalTime.of(10, 0), 60, LocalDate.of(2026, 3, 10), "evento especial", null, null);
    }

    private UpdateUniqueEventRequestDto updateDto() {
        return new UpdateUniqueEventRequestDto(
                UniqueEventKind.PARCIAL, 1L, 1L, LocalDate.of(2026, 3, 15),
                LocalTime.of(11, 0), 90, 25, "descripcion actualizada");
    }
}
