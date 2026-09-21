package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.BuildingNotAvailableException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.exception.PartialAssignmentReasonRequiredException;
import ar.edu.utn.frc.siga.roomrequest.exception.RoomRequestAlreadyNotifiedException;
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
    private BuildingService buildingService;
    @Mock
    private UserService userService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private RoomRequestComposer composer;
    @Mock
    private RoomRequestOccurrenceResolver occurrenceResolver;
    @Mock
    private RoomRequestCandidateResolver candidateResolver;

    @InjectMocks
    private RoomRequestResolutionServiceImpl service;

    // ---------- cancel ----------

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
    @EnumSource(value = RoomRequestStatus.class, names = {"CANCELLED", "RESOLVED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("se puede cancelar desde cualquier estado no final salvo RESOLVED")
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
    @DisplayName("RESOLVED → cancel: la transición se rechaza, es terminal")
    void resueltoSeRechaza() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.RESOLVED).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        doThrowOnTransitionTo(RoomRequestStatus.RESOLVED, RoomRequestStatus.CANCELLED);

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

    // ---------- findAllowedClassrooms / findCandidateBuildings: delegación ----------
    // La lógica de disponibilidad se prueba en RoomRequestCandidateResolverTest; acá solo se
    // confirma que el service delega en el resolver y devuelve tal cual lo que este resuelve.

    @Test
    @DisplayName("findAllowedClassrooms: delega en RoomRequestCandidateResolver")
    void findAllowedClassrooms_delega() {
        List<AllowedClassroomDto> expected = List.of(
                new AllowedClassroomDto(101L, 101, 1L, "Edificio Central", 40, true));
        when(candidateResolver.findAllowedClassrooms(1L)).thenReturn(expected);

        assertThat(service.findAllowedClassrooms(1L)).isSameAs(expected);
    }

    @Test
    @DisplayName("findCandidateBuildings: delega en RoomRequestCandidateResolver")
    void findCandidateBuildings_delega() {
        List<CandidateBuildingDto> expected = List.of(new CandidateBuildingDto(1L, "Edificio Central", 2));
        when(candidateResolver.findCandidateBuildings(1L)).thenReturn(expected);

        assertThat(service.findCandidateBuildings(1L)).isSameAs(expected);
    }

    // ---------- derive ----------

    @Test
    @DisplayName("derive: edificio activo, con auxiliar y aula libre → pasa a DERIVED_TO_BUILDING")
    void derive_ok() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(candidateResolver.occupiedClassroomIds(item)).thenReturn(Set.of());
        when(candidateResolver.candidateClassrooms(item)).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.DERIVED_TO_BUILDING);
        assertThat(item.getDerivedBuildingId()).isEqualTo(1L);
        assertThat(item.getDerivedAt()).isNotNull();
    }

    @Test
    @DisplayName("derive: edificio con solo 1 de 2 aulas libres también se acepta (resolución parcial)")
    void derive_resolucionParcial() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(candidateResolver.occupiedClassroomIds(item)).thenReturn(Set.of(102L));
        when(candidateResolver.candidateClassrooms(item)).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.DERIVED_TO_BUILDING);
    }

    @Test
    @DisplayName("derive: sin ninguna aula libre en el edificio se rechaza")
    void derive_sinAulasLibres() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of(auxiliar()));
        when(candidateResolver.occupiedClassroomIds(item)).thenReturn(Set.of(101L));
        when(candidateResolver.candidateClassrooms(item)).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(BuildingNotAvailableException.class);
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("derive: edificio sin auxiliar áulico asignado se rechaza")
    void derive_sinAuxiliarAsignado() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", true));
        when(userService.findByRoleForBuilding(SystemRole.AUXILIAR_AULICO, 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(candidateResolver, composer);
    }

    @Test
    @DisplayName("derive: edificio inactivo se rechaza")
    void derive_edificioInactivo() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(buildingService.findById(1L)).thenReturn(new BuildingResponseDto(1L, "Edificio Central", false));

        assertThatThrownBy(() -> service.derive(1L, 1L, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(userService, candidateResolver, composer);
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

    // ---------- return ----------

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

    // ---------- notify ----------

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

    // ---------- assign: validación de forma ----------
    // La lógica de "qué ocurrencia(s) le corresponden al ítem" vive en RoomRequestOccurrenceResolver
    // (con su propio test); acá se prueba la orquestación de assign(): validaciones de forma,
    // manejo de reason, y cómo se arma el AllocationCommand a partir de lo que el resolver devuelve.

    @Test
    @DisplayName("assign: RESOLVED (ya notificado) se rechaza antes de validar la forma")
    void assign_yaNotificadoSeRechaza() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.RESOLVED)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(RoomRequestAlreadyNotifiedException.class);
        verifyNoInteractions(transitionValidator, allocationService, occurrenceResolver, composer);
    }

    @Test
    @DisplayName("assign: 0 aulas se rechaza (eso es cancel, no assign)")
    void assign_ceroAulas() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, occurrenceResolver, composer);
    }

    @Test
    @DisplayName("assign: más aulas que classroomCount se rechaza")
    void assign_masAulasQueClassroomCount() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 105L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, occurrenceResolver, composer);
    }

    @Test
    @DisplayName("assign: ids repetidos se rechaza")
    void assign_idsRepetidos() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationService, occurrenceResolver, composer);
    }

    @Test
    @DisplayName("assign: menos aulas que classroomCount sin reason se rechaza")
    void assign_menosAulasSinReason() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(PartialAssignmentReasonRequiredException.class);
        verifyNoInteractions(allocationService, occurrenceResolver, composer);
    }

    @Test
    @DisplayName("assign: menos aulas que classroomCount con reason se acepta y queda IN_EVALUATION")
    void assign_menosAulasConReason() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 1, false)).thenReturn(List.of(List.of(9000L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L), "solo hay una disponible", "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getDecisionReason()).isEqualTo("solo hay una disponible");
    }

    @Test
    @DisplayName("assign: conflicto de aula propaga la excepción sin dejar nada a medio escribir")
    void assign_conflicto() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 1, false)).thenReturn(List.of(List.of(500L)));
        when(allocationService.reallocate(any()))
                .thenThrow(new ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException("ocupada"));

        assertThatThrownBy(() -> service.assign(1L, List.of(101L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException.class);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
        assertThat(item.getAllocations()).isEmpty();
        verifyNoInteractions(composer);
    }

    @Test
    @DisplayName("assign: reasignar con el mismo total de aulas que classroomCount no exige reason")
    void assign_reasignarSinReasonSiCompleto() {
        RoomRequestItemAllocation previous = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1)
                .allocations(new java.util.ArrayList<>(List.of(previous))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 1, true)).thenReturn(List.of(List.of(500L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(102L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId).containsExactly(102L);
    }

    @Test
    @DisplayName("assign: reasignar llama a releaseOldMirrors antes de resolver las nuevas ocurrencias")
    void assign_reasignando_liberaMirrorsViejos() {
        RoomRequestItemAllocation previous = RoomRequestItemAllocation.builder()
                .occurrenceId(500L).classroomId(101L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.IN_EVALUATION)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1)
                .allocations(new java.util.ArrayList<>(List.of(previous))).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 1, true)).thenReturn(List.of(List.of(500L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(200L), null, "subsecretaria@frc.utn.edu.ar");

        verify(occurrenceResolver).releaseOldMirrors(item);
    }

    @Test
    @DisplayName("assign: primera vez (NEW, sin allocations) no llama a releaseOldMirrors")
    void assign_primeraVez_noLiberaMirrors() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(1).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 1, false)).thenReturn(List.of(List.of(9000L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L), null, "subsecretaria@frc.utn.edu.ar");

        verify(occurrenceResolver, never()).releaseOldMirrors(any());
    }

    @Test
    @DisplayName("assign: arma un AllocationItem por posición, usando las ocurrencias que devuelve el resolver")
    void assign_armaAllocationCommandPorPosicion() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 2, false))
                .thenReturn(List.of(List.of(9000L), List.of(9001L)));
        when(composer.composeItem(item)).thenReturn(mockResponse());

        service.assign(1L, List.of(104L, 105L), null, "subsecretaria@frc.utn.edu.ar");

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.IN_EVALUATION);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getOccurrenceId)
                .containsExactlyInAnyOrder(9000L, 9001L);
        assertThat(item.getAllocations()).extracting(RoomRequestItemAllocation::getClassroomId)
                .containsExactlyInAnyOrder(104L, 105L);

        ArgumentCaptor<AllocationCommand> captor = ArgumentCaptor.forClass(AllocationCommand.class);
        verify(allocationService).reallocate(captor.capture());
        assertThat(captor.getValue().items()).hasSize(2);
    }

    @Test
    @DisplayName("assign: conflicto con varias aulas no deja nada a medio escribir")
    void assign_conflictoConVariasAulas_noAplicaNada() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .request(requestOfType(RoomRequestType.FINAL_EXAM)).classroomCount(2).build();
        when(itemRepository.findWithRequestById(1L)).thenReturn(Optional.of(item));
        when(occurrenceResolver.resolveOccurrencesBySlot(item, 2, false))
                .thenReturn(List.of(List.of(9000L), List.of(9001L)));
        when(allocationService.reallocate(any()))
                .thenThrow(new ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException("ocupada"));

        assertThatThrownBy(() -> service.assign(1L, List.of(104L, 105L), null, "subsecretaria@frc.utn.edu.ar"))
                .isInstanceOf(ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException.class);

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
        assertThat(item.getAllocations()).isEmpty();
        verifyNoInteractions(composer);
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
                null, "motivo", List.of(), null, null, null, null, null, null, null, 1, false, false, null,
                false, null, null, List.of(), null, List.of(), null, null, null, null, null);
    }
}
