package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestTransitionValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private BuildingService buildingService;
    @Mock
    private UserService userService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
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
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
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
    @DisplayName("ítem sin nada asignado (NEW): no llama a deallocate")
    void sinAsignacionesNoLiberaNada() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
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

    @Test
    @DisplayName("derive: edificio activo, con auxiliar y aula libre → pasa a DERIVED_TO_BUILDING")
    void derive_ok() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.DERIVED_TO_BUILDING);
        assertThat(item.getDerivedBuildingId()).isEqualTo(1L);
        assertThat(item.getDerivedAt()).isNotNull();
    }

    @Test
    @DisplayName("derive: edificio con solo 1 de 2 aulas libres también se acepta (resolución parcial)")
    void derive_resolucionParcial() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(classroomService.findAllAvailable()).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(102L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.DERIVED_TO_BUILDING);
    }

    @Test
    @DisplayName("derive: sin ninguna aula libre en el edificio se rechaza")
    void derive_sinAulasLibres() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .date(date).startTime(LocalTime.of(10, 0)).duration(java.time.Duration.ofMinutes(60))
                .build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(101L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("derive: edificio sin auxiliar áulico asignado se rechaza (agujero #2)")
    void derive_sinAuxiliarAsignado() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(classroomService, composer);
    }

    @Test
    @DisplayName("derive: edificio inactivo se rechaza")
    void derive_edificioInactivo() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", false));

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(userService, classroomService, composer);
    }

    @Test
    @DisplayName("derive: edificio inexistente → 404")
    void derive_edificioInexistente() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(99L)).thenThrow(ResourceNotFoundException.of("Building", 99L));

        assertThatThrownBy(() -> service.derive(1L, 99L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("derive: desde IN_EVALUATION se rechaza (solo sale desde NEW)")
    void derive_desdeEnEvaluacionSeRechaza() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        doThrowOnTransitionTo(RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.DERIVED_TO_BUILDING);

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
        verifyNoInteractions(buildingService, composer);
    }

    @Test
    @DisplayName("derive: ítem inexistente → 404")
    void derive_itemInexistente() {
        when(itemRepository.findWithRequestById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.derive(99L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("return: reason vacío o nulo → 400, sin tocar el repositorio")
    void return_reasonObligatorio() {
        assertThatThrownBy(() -> service.returnItem(1L, "", "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        assertThatThrownBy(() -> service.returnItem(1L, null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);

        verifyNoInteractions(itemRepository, transitionValidator, composer);
    }

    @Test
    @DisplayName("return: DERIVED_TO_BUILDING → NEW, copia el edificio a returnedFromBuildingId y limpia derivedBuildingId/derivedAt")
    void return_ok() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.DERIVED_TO_BUILDING)
                .derivedBuildingId(5L).derivedAt(java.time.LocalDateTime.now()).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.returnItem(1L, "el laboratorio se bloqueó", "auxiliar@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
        assertThat(item.getReturnedFromBuildingId()).isEqualTo(5L);
        assertThat(item.getReturnedReason()).isEqualTo("el laboratorio se bloqueó");
        assertThat(item.getDerivedBuildingId()).isNull();
        assertThat(item.getDerivedAt()).isNull();
    }

    @Test
    @DisplayName("return: un segundo return pisa returnedFromBuildingId/returnedReason con los datos nuevos")
    void return_segundaVezPisaDatosAnteriores() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.DERIVED_TO_BUILDING)
                .derivedBuildingId(7L).derivedAt(java.time.LocalDateTime.now())
                .returnedFromBuildingId(5L).returnedReason("motivo viejo").build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.returnItem(1L, "motivo nuevo", "auxiliar@frc.utn.edu.ar");

        assertThat(item.getReturnedFromBuildingId()).isEqualTo(7L);
        assertThat(item.getReturnedReason()).isEqualTo("motivo nuevo");
    }

    @Test
    @DisplayName("return: solo sale desde DERIVED_TO_BUILDING, NEW se rechaza")
    void return_soloDesdeDerivado() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        doThrowOnTransitionTo(RoomRequestStatus.NEW, RoomRequestStatus.NEW);

        assertThatThrownBy(() -> service.returnItem(1L, "motivo", "auxiliar@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("return: ítem inexistente → 404")
    void return_itemInexistente() {
        when(itemRepository.findWithRequestById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.returnItem(99L, "motivo", "auxiliar@frc.utn.edu.ar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("notify: IN_EVALUATION con aula asignada pasa a RESOLVED, sella notifiedAt y publica RoomRequestResolved")
    void notify_ok() {
        RoomRequestItemAllocation allocation = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .allocations(new java.util.ArrayList<>(List.of(allocation))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.notify(1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(item.getNotifiedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(ar.edu.utn.frc.siga.roomrequest.model.RoomRequestResolved.class));
    }

    @Test
    @DisplayName("notify: llamado dos veces devuelve 200 las dos veces con el mismo notifiedAt, sin republicar")
    void notify_idempotente() {
        RoomRequestItemAllocation allocation = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .allocations(new java.util.ArrayList<>(List.of(allocation))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.notify(1L, "subsecretaria@frc.utn.edu.ar");
        java.time.LocalDateTime firstNotifiedAt = item.getNotifiedAt();
        service.notify(1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getNotifiedAt()).isEqualTo(firstNotifiedAt);
        verify(eventPublisher, org.mockito.Mockito.times(1))
                .publishEvent(any(ar.edu.utn.frc.siga.roomrequest.model.RoomRequestResolved.class));
    }

    @Test
    @DisplayName("notify: pedido resuelto de menos (1 de 2 aulas) funciona igual")
    void notify_resolucionParcial() {
        RoomRequestItemAllocation allocation = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2)
                .allocations(new java.util.ArrayList<>(List.of(allocation))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.notify(1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
    }

    @Test
    @DisplayName("notify: estado distinto de IN_EVALUATION se rechaza con conflicto")
    void notify_estadoInvalido() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        doThrowOnTransitionTo(RoomRequestStatus.NEW, RoomRequestStatus.RESOLVED);

        assertThatThrownBy(() -> service.notify(1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
        verifyNoInteractions(eventPublisher, composer);
    }

    @Test
    @DisplayName("notify: sin ninguna aula asignada se rechaza")
    void notify_sinAulas() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.notify(1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(eventPublisher, composer);
    }

    @Test
    @DisplayName("notify: ítem inexistente → 404")
    void notify_itemInexistente() {
        when(itemRepository.findWithRequestById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notify(99L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assign: ONE_TIME_ROOM_CHANGE reasigna la ocurrencia de la fecha del ítem y queda IN_EVALUATION")
    void assign_oneTimeRoomChange() {
        LocalDate date = LocalDate.of(2026, 3, 12);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.ONE_TIME_ROOM_CHANGE))
                .sourceRecurringEventId(50L).date(date).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(50L, null)).thenReturn(List.of(
                occurrenceSlot(500L, 50L, date)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(101L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId).containsExactly(500L);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId).containsExactly(101L);
        ArgumentCaptor<AllocationCommand> captor = ArgumentCaptor.forClass(AllocationCommand.class);
        verify(allocationService).reallocate(captor.capture());
        assertThat(((AllocationTarget.Occurrences) captor.getValue().items().getFirst().target()).occurrenceIds())
                .containsExactly(500L);
    }

    @Test
    @DisplayName("assign: PARTIAL_EXAM_IN_CLASS resuelve la ocurrencia por la fecha del ítem (BUG-01), sin pedir fecha extra")
    void assign_partialExamInClass() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.PARTIAL_EXAM_IN_CLASS))
                .sourceRecurringEventId(60L).date(date).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(60L, null)).thenReturn(List.of(occurrenceSlot(600L, 60L, date)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(102L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        verify(allocationService).reallocate(any());
    }

    @Test
    @DisplayName("assign: REGULAR_ROOM_CHANGE asigna todas las ocurrencias futuras del dayOfWeek del ítem")
    void assign_regularRoomChange() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.REGULAR_ROOM_CHANGE))
                .sourceRecurringEventId(70L).dayOfWeek(java.time.DayOfWeek.THURSDAY).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(eq(70L), any(LocalDate.class))).thenReturn(List.of(
                occurrenceSlot(701L, 70L, LocalDate.of(2026, 5, 7)),
                occurrenceSlot(702L, 70L, LocalDate.of(2026, 5, 14)),
                occurrenceSlot(703L, 70L, LocalDate.of(2026, 5, 8))));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(103L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId)
                .containsExactlyInAnyOrder(701L, 702L);
    }

    @Test
    @DisplayName("assign: REGULAR_ROOM_CHANGE sin ocurrencias futuras se rechaza")
    void assign_regularRoomChange_sinFuturas() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.REGULAR_ROOM_CHANGE))
                .sourceRecurringEventId(70L).dayOfWeek(java.time.DayOfWeek.THURSDAY).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(eq(70L), any(LocalDate.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(1L, List.of(103L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: FINAL_EXAM crea el UniqueEvent y asigna su ocurrencia (primera vez → allocate)")
    void assign_finalExam_creaEvento() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(30).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId).containsExactly(9000L);
        verify(allocationService).reallocate(any());
        verify(allocationService, never()).allocate(any());
    }

    @Test
    @DisplayName("assign: CONFERENCE crea el UniqueEvent kind OTRO")
    void assign_conference_creaEventoOtro() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.CONFERENCE))
                .date(date).startTime(LocalTime.of(18, 0)).duration(Duration.ofMinutes(60))
                .estimated(80).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(901L));
        when(academicEventService.findOccurrencesByEventId(901L)).thenReturn(List.of(
                new OccurrenceResponseDto(9001L, 901L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(18, 0), LocalTime.of(19, 0))));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(105L), null, "subsecretaria@frc.utn.edu.ar");

        ArgumentCaptor<CreateUniqueEventRequestDto> captor = ArgumentCaptor.forClass(CreateUniqueEventRequestDto.class);
        verify(academicEventService).createUniqueEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(UniqueEventKind.OTRO);
    }

    @Test
    @DisplayName("assign: reasignar sobre un tipo con evento creado reutiliza la ocurrencia existente (reallocate, no allocate)")
    void assign_reasignarTipoConEventoCreado() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItemAllocation previous = RoomRequestItemAllocation.builder()
                .occurrenceId(9000L).classroomId(104L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(30).classroomCount(1).allocations(new java.util.ArrayList<>(List.of(previous))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(200L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId).containsExactly(9000L);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId).containsExactly(200L);
        verify(academicEventService, never()).createUniqueEvent(any());
        verify(allocationService).reallocate(any());
        verify(allocationService, never()).allocate(any());
    }

    @Test
    @DisplayName("assign: 0 aulas se rechaza (eso es cancel, no assign)")
    void assign_ceroAulas() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: más aulas que classroomCount se rechaza")
    void assign_masAulasQueClassroomCount() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 105L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: ids repetidos se rechaza")
    void assign_idsRepetidos() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: menos aulas que classroomCount sin reason se rechaza")
    void assign_menosAulasSinReason() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: menos aulas que classroomCount con reason se acepta y queda IN_EVALUATION")
    void assign_menosAulasConReason() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(30).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L), "solo hay una disponible", "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getDecisionReason()).isEqualTo("solo hay una disponible");
    }

    @Test
    @DisplayName("assign: pedido ya notificado se rechaza aunque el estado lo permitiera")
    void assign_yaNotificadoSeRechaza() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1)
                .notifiedAt(java.time.LocalDateTime.now()).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, composer);
    }

    @Test
    @DisplayName("assign: conflicto de aula propaga la excepción sin dejar nada a medio escribir")
    void assign_conflicto() {
        LocalDate date = LocalDate.of(2026, 3, 12);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.ONE_TIME_ROOM_CHANGE))
                .sourceRecurringEventId(50L).date(date).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(50L, null)).thenReturn(List.of(occurrenceSlot(500L, 50L, date)));
        when(allocationService.reallocate(any()))
                .thenThrow(new ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException("ocupada"));

        assertThatThrownBy(() -> service.assign(1L, List.of(101L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException.class);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
        assertThat(item.getAllocations()).isEmpty();
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("assign: desde IN_EVALUATION distinto set (0 aulas anteriores) no exige reason si llega completo")
    void assign_reasignarSinReasonSiCompleto() {
        RoomRequestItemAllocation previous = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        LocalDate date = LocalDate.of(2026, 3, 12);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.ONE_TIME_ROOM_CHANGE))
                .sourceRecurringEventId(50L).date(date).classroomCount(1)
                .allocations(new java.util.ArrayList<>(List.of(previous))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.findSlotsByEvent(50L, null)).thenReturn(List.of(occurrenceSlot(500L, 50L, date)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(102L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId).containsExactly(102L);
    }

    @Test
    @DisplayName("assign: 3 aulas crea 1 evento con 3 ocurrencias enlazadas (roomSlot 1/2/3) y 3 filas de asignación")
    void assign_tresAulas_creaOcurrenciasSimultaneas() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(90).classroomCount(3).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(occurrenceService.createSimultaneous(9000L, 2)).thenReturn(List.of(9001L, 9002L));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L, 105L, 106L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getAllocations()).hasSize(3);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId)
                .containsExactlyInAnyOrder(9000L, 9001L, 9002L);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getPosition)
                .containsExactlyInAnyOrder(1, 2, 3);

        ArgumentCaptor<AllocationCommand> captor = ArgumentCaptor.forClass(AllocationCommand.class);
        verify(allocationService).reallocate(captor.capture());
        assertThat(captor.getValue().items()).hasSize(3);
    }

    @Test
    @DisplayName("assign: conflicto con alguna de varias aulas no deja nada a medio escribir")
    void assign_conflictoConVariasAulas_noAplicaNada() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(90).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(occurrenceService.createSimultaneous(9000L, 1)).thenReturn(List.of(9001L));
        when(allocationService.reallocate(any()))
                .thenThrow(new ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException("ocupada"));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 105L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException.class);

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
        assertThat(item.getAllocations()).isEmpty();
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("assign: reasignar de 3 aulas a otras 3 libera los mirrors viejos y no deja huérfanos")
    void assign_reasignarVariasAulas_liberaMirrorsViejos() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItemAllocation previous1 = RoomRequestItemAllocation.builder()
                .occurrenceId(9000L).classroomId(104L).position(1).build();
        RoomRequestItemAllocation previous2 = RoomRequestItemAllocation.builder()
                .occurrenceId(9001L).classroomId(105L).position(2).build();
        RoomRequestItemAllocation previous3 = RoomRequestItemAllocation.builder()
                .occurrenceId(9002L).classroomId(106L).position(3).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90))
                .estimated(90).classroomCount(3)
                .allocations(new java.util.ArrayList<>(List.of(previous1, previous2, previous3))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceService.createSimultaneous(9000L, 2)).thenReturn(List.of(9010L, 9011L));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(200L, 201L, 202L), null, "subsecretaria@frc.utn.edu.ar");

        ArgumentCaptor<DeallocationCommand> deallocCaptor = ArgumentCaptor.forClass(DeallocationCommand.class);
        verify(allocationService).deallocate(deallocCaptor.capture());
        AllocationTarget.Occurrences deallocated =
                (AllocationTarget.Occurrences) deallocCaptor.getValue().targets().getFirst();
        assertThat(deallocated.occurrenceIds()).containsExactlyInAnyOrder(9001L, 9002L);
        verify(occurrenceService).release(9001L);
        verify(occurrenceService).release(9002L);
        verify(academicEventService, never()).createUniqueEvent(any());

        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId)
                .containsExactlyInAnyOrder(9000L, 9010L, 9011L);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId)
                .containsExactlyInAnyOrder(200L, 201L, 202L);
    }

    private static RoomRequest requestOfType(RoomRequestType type) {
        return RoomRequest.builder()
                .type(type)
                .scope(ar.edu.utn.frc.siga.roomrequest.model.AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(1L)
                .build();
    }

    private static OccurrenceSlotDto occurrenceSlot(Long occurrenceId, Long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(10, 0), LocalTime.of(11, 0),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private static AcademicEventResponseDto uniqueEvent(Long eventId) {
        return new UniqueEventResponseDto(eventId, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, 30,
                LocalTime.of(9, 0), 90L, LocalDate.of(2026, 7, 1), null, null, null);
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
                false, null, null, List.of(), List.of(), null, null, null, null, null);
    }
}
