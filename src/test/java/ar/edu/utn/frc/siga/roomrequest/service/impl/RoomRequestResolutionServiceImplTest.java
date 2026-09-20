package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestResolutionServiceImpl")
class RoomRequestResolutionServiceImplTest {

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private RoomRequestTransitionValidator transitionValidator;
    @Mock
    private AllocationService allocationService;
    @Mock
    private AllocationOccupancyService allocationOccupancyService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private UserService userService;
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

    @Test
    @DisplayName("findAllowedClassrooms: ítem inexistente → 404")
    void findAllowedClassrooms_itemInexistente() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAllowedClassrooms(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAllowedClassrooms: aula ocupada en la fecha/horario sale con available=false, sin excluirse")
    void findAllowedClassrooms_aulaOcupada() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L)));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(101L, date, LocalTime.of(10, 30), LocalTime.of(11, 30), 5L, 7L)));

        List<AllowedClassroomDto> result = service.findAllowedClassrooms(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(101L);
        assertThat(result.getFirst().available()).isFalse();
    }

    @Test
    @DisplayName("findAllowedClassrooms: requiresComputers filtra por el mínimo pedido")
    void findAllowedClassrooms_filtraPorComputadoras() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .requiresComputers(true).computerCount(20).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L), classroom(102L)));
        when(classroomService.findIdsWithResourceAtLeast("Cantidad de PC", 20)).thenReturn(Set.of(101L));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        List<AllowedClassroomDto> result = service.findAllowedClassrooms(1L);

        assertThat(result).extracting(AllowedClassroomDto::id).containsExactly(101L);
    }

    @Test
    @DisplayName("findAllowedClassrooms: requiresProjector filtra por ese recurso")
    void findAllowedClassrooms_filtraPorProyector() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .requiresProjector(true).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L), classroom(102L)));
        when(classroomService.findIdsWithResourceAtLeast("Proyector", 1)).thenReturn(Set.of(102L));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        List<AllowedClassroomDto> result = service.findAllowedClassrooms(1L);

        assertThat(result).extracting(AllowedClassroomDto::id).containsExactly(102L);
    }

    @Test
    @DisplayName("findAllowedClassrooms: sin aulas candidatas, devuelve lista vacía sin error")
    void findAllowedClassrooms_listaVacia() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of());
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        assertThat(service.findAllowedClassrooms(1L)).isEmpty();
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con las classroomCount aulas pedidas aparece")
    void findCandidateBuildings_edificioCompleto() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .classroomCount(2).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L))
                .thenReturn(List.of(auxiliar()));

        List<CandidateBuildingDto> result = service.findCandidateBuildings(1L);

        assertThat(result).containsExactly(new CandidateBuildingDto(1L, "Edificio Central", 2));
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con solo 1 de 2 aulas libres también aparece")
    void findCandidateBuildings_resolucionParcial() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .classroomCount(2).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(102L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L))
                .thenReturn(List.of(auxiliar()));

        List<CandidateBuildingDto> result = service.findCandidateBuildings(1L);

        assertThat(result).containsExactly(new CandidateBuildingDto(1L, "Edificio Central", 1));
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio sin ninguna aula libre no aparece")
    void findCandidateBuildings_sinAulasLibres() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(101L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));

        assertThat(service.findCandidateBuildings(1L)).isEmpty();
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con aulas libres pero sin ningún auxiliar áulico asignado no aparece")
    void findCandidateBuildings_sinAuxiliarAsignado() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of());

        assertThat(service.findCandidateBuildings(1L)).isEmpty();
    }

    private static RoomRequestItem itemFor(LocalDate date, LocalTime startTime, int durationMinutes) {
        return RoomRequestItem.builder()
                .date(date).startTime(startTime).duration(java.time.Duration.ofMinutes(durationMinutes))
                .build();
    }

    private static ClassroomResponseDto classroom(Long id) {
        return classroom(id, 1L, "Edificio Central");
    }

    private static ClassroomResponseDto classroom(Long id, Long buildingId, String buildingName) {
        return new ClassroomResponseDto(id, id.intValue(), 40, buildingId, buildingName, 1L, "Aula común");
    }

    private static UserResponseDto auxiliar() {
        return new UserResponseDto(1L, "auxiliar@frc.utn.edu.ar", "Auxiliar", "Aulico", List.of());
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
