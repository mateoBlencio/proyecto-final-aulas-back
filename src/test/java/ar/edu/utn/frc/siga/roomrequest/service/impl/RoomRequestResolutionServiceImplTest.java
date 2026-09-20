package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestResolutionServiceImpl.cancel")
class RoomRequestResolutionServiceImplTest {

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private RoomRequestTransitionValidator transitionValidator;
    @Mock
    private AllocationService allocationService;
    @Mock
    private RoomRequestComposer composer;

    @InjectMocks
    private RoomRequestResolutionServiceImpl service;

    @Test
    @DisplayName("reason vacío o nulo → 400, sin tocar el repositorio")
    void reasonObligatorio() {
        assertThatThrownBy(() -> service.cancel(1L, "", "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        assertThatThrownBy(() -> service.cancel(1L, null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);

        verifyNoInteractions(itemRepository, transitionValidator, allocationService, composer);
    }

    @Test
    @DisplayName("ítem inexistente → 404")
    void itemInexistente() {
        when(itemRepository.findWithRequestById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L, "motivo", "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RoomRequestStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("se puede cancelar desde cualquier estado no final, incluido RESOLVED")
    void cancelaDesdeCualquierEstadoNoFinal(RoomRequestStatus status) {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(status).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.cancel(1L, "motivo", "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(item.getDecisionReason()).isEqualTo("motivo");
        assertThat(item.getDecidedBy()).isEqualTo("subsecretaria@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("CANCELLED → cancel de nuevo: la transición se rechaza")
    void yaCanceladoSeRechaza() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.CANCELLED).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        doThrowOnTransitionTo(RoomRequestStatus.CANCELLED, RoomRequestStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(1L, "motivo", "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);

        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("ítem con aulas asignadas: libera todas las ocurrencias en una sola llamada")
    void liberaAulasAsignadas() {
        RoomRequestItemAllocation allocation1 = RoomRequestItemAllocation.builder()
                .occurrenceId(10L).classroomId(100L).position(1).build();
        RoomRequestItemAllocation allocation2 = RoomRequestItemAllocation.builder()
                .occurrenceId(11L).classroomId(101L).position(2).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.PRE_APPROVED)
                .allocations(List.of(allocation1, allocation2)).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.cancel(1L, "se cayó el laboratorio", "subsecretaria@frc.utn.edu.ar");

        ArgumentCaptor<DeallocationCommand> captor = ArgumentCaptor.forClass(DeallocationCommand.class);
        verify(allocationService).deallocate(captor.capture());
        AllocationTarget.Occurrences target = (AllocationTarget.Occurrences) captor.getValue().targets().getFirst();
        assertThat(target.occurrenceIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("ítem sin nada asignado (PENDING): no llama a deallocate")
    void sinAsignacionesNoLiberaNada() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.PENDING).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.cancel(1L, "el docente se arrepintió", "subsecretaria@frc.utn.edu.ar");

        verify(allocationService, never()).deallocate(any());
    }

    private void doThrowOnTransitionTo(RoomRequestStatus current, RoomRequestStatus target) {
        org.mockito.Mockito.doThrow(new InvalidRoomRequestTransitionException(current, target))
                .when(transitionValidator).validateTransition(current, target);
    }

    private static RoomRequestItemResponseDto mockResponse() {
        return new RoomRequestItemResponseDto(1L, 1, RoomRequestStatus.CANCELLED, "subsecretaria@frc.utn.edu.ar",
                null, "motivo", List.of(), null, null, null, null, null, null, 1, false, false, null,
                false, null, null, List.of());
    }
}
