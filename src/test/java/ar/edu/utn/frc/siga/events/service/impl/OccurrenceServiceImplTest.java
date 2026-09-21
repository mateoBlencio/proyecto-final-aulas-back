package ar.edu.utn.frc.siga.events.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.EventTestData;
import ar.edu.utn.frc.siga.events.exception.OccurrenceAlreadyPastException;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.events.validator.EventScheduleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceServiceImpl")
class OccurrenceServiceImplTest {

    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private EventScheduleValidator eventScheduleValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OccurrenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OccurrenceServiceImpl(occurrenceRepository, eventScheduleValidator, eventPublisher);
    }

    private Occurrence occurrence(Long id, OccurrenceStatus status) {
        AcademicEvent event = EventTestData.recurringEvent(1L, DayOfWeek.MONDAY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30));
        return EventTestData.occurrence(id, event, LocalDate.of(2026, 3, 2), status);
    }

    @Test
    @DisplayName("release: pasa a ROOM_RELEASED y publica OccurrenceVacated")
    void releaseCambiaEstadoYPublicaEvento() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));

        service.release(10L);

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
        verify(eventPublisher).publishEvent(eq(new OccurrenceVacated(10L)));
    }

    @Test
    @DisplayName("release: ocurrencia pasada → propaga la excepción del validator y no publica el evento")
    void releaseOcurrenciaPasadaNoPublica() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));
        doThrow(new OccurrenceAlreadyPastException("ya ocurrió"))
                .when(eventScheduleValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.release(10L)).isInstanceOf(OccurrenceAlreadyPastException.class);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("release: ocurrencia inexistente → ResourceNotFoundException")
    void releaseOcurrenciaInexistente() {
        when(occurrenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("requestRoom: vuelve a NEEDS_ROOM (no publica ningún evento)")
    void requestRoomVuelveANeedsRoom() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.ROOM_RELEASED);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));

        service.requestRoom(10L);

        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("requestRoom: ocurrencia pasada → propaga la excepción del validator")
    void requestRoomOcurrenciaPasada() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.ROOM_RELEASED);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));
        doThrow(new OccurrenceAlreadyPastException("ya ocurrió"))
                .when(eventScheduleValidator).validateNotPast(occurrence);

        assertThatThrownBy(() -> service.requestRoom(10L)).isInstanceOf(OccurrenceAlreadyPastException.class);
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ROOM_RELEASED);
    }

    @Test
    @DisplayName("createSimultaneous: crea count ocurrencias con roomSlot correlativo y mirrorOfOccurrenceId a la principal")
    void createSimultaneousCreaOcurrenciasEnlazadas() {
        Occurrence principal = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(principal));
        when(occurrenceRepository.findByEvent_IdAndDate(1L, LocalDate.of(2026, 3, 2)))
                .thenReturn(List.of(principal));
        when(occurrenceRepository.saveAll(org.mockito.ArgumentMatchers.<List<Occurrence>>any()))
                .thenAnswer(invocation -> {
                    List<Occurrence> toSave = invocation.getArgument(0);
                    List<Occurrence> saved = new java.util.ArrayList<>();
                    long nextId = 100L;
                    for (Occurrence o : toSave) {
                        saved.add(Occurrence.builder()
                                .id(nextId++)
                                .event(o.getEvent())
                                .date(o.getDate())
                                .status(o.getStatus())
                                .roomSlot(o.getRoomSlot())
                                .mirrorOfOccurrenceId(o.getMirrorOfOccurrenceId())
                                .build());
                    }
                    return saved;
                });

        List<Long> created = service.createSimultaneous(10L, 2);

        assertThat(created).containsExactly(100L, 101L);
        ArgumentCaptor<List<Occurrence>> captor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Occurrence::getRoomSlot).containsExactly(2, 3);
        assertThat(captor.getValue()).extracting(Occurrence::getMirrorOfOccurrenceId).containsExactly(10L, 10L);
        assertThat(captor.getValue()).extracting(Occurrence::getDate).containsExactly(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));
        assertThat(captor.getValue()).extracting(Occurrence::getStatus).containsExactly(
                OccurrenceStatus.NEEDS_ROOM, OccurrenceStatus.NEEDS_ROOM);
    }

    @Test
    @DisplayName("createSimultaneous: si ya hay ocurrencias simultáneas, sigue la numeración de roomSlot")
    void createSimultaneousContinuaLaNumeracion() {
        Occurrence principal = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        Occurrence existingMirror = Occurrence.builder()
                .id(11L).event(principal.getEvent()).date(principal.getDate())
                .status(OccurrenceStatus.NEEDS_ROOM).roomSlot(2).mirrorOfOccurrenceId(10L).build();
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(principal));
        when(occurrenceRepository.findByEvent_IdAndDate(1L, LocalDate.of(2026, 3, 2)))
                .thenReturn(List.of(principal, existingMirror));
        when(occurrenceRepository.saveAll(org.mockito.ArgumentMatchers.<List<Occurrence>>any()))
                .thenAnswer(invocation -> {
                    List<Occurrence> toSave = invocation.getArgument(0);
                    List<Occurrence> saved = new java.util.ArrayList<>();
                    long nextId = 200L;
                    for (Occurrence o : toSave) {
                        saved.add(Occurrence.builder().id(nextId++).event(o.getEvent()).date(o.getDate())
                                .status(o.getStatus()).roomSlot(o.getRoomSlot())
                                .mirrorOfOccurrenceId(o.getMirrorOfOccurrenceId()).build());
                    }
                    return saved;
                });

        service.createSimultaneous(10L, 1);

        ArgumentCaptor<List<Occurrence>> captor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Occurrence::getRoomSlot).containsExactly(3);
    }

    @Test
    @DisplayName("findSlot: ocurrencia inexistente → ResourceNotFoundException")
    void findSlotInexistente() {
        when(occurrenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSlot(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findSlot: mapea evento, fechas y horario a partir de la ocurrencia")
    void findSlotMapeaCorrectamente() {
        Occurrence occurrence = occurrence(10L, OccurrenceStatus.NEEDS_ROOM);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));

        var slot = service.findSlot(10L);

        assertThat(slot.occurrenceId()).isEqualTo(10L);
        assertThat(slot.eventId()).isEqualTo(1L);
        assertThat(slot.date()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(slot.status()).isEqualTo(OccurrenceStatus.NEEDS_ROOM);
    }
}
