package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestAccessControl;
import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import ar.edu.utn.frc.siga.space.service.command.ClassroomDeactivation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestDeactivationHandler")
class RoomRequestDeactivationHandlerTest {

    private static final String ACTOR = "auxiliar@frc.utn.edu.ar";
    private static final long CENTRAL = 1L;
    private static final long NORTE = 2L;

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private RoomRequestAccessControl accessControl;
    @Mock
    private SubjectService subjectService;

    private RoomRequestDeactivationHandler handler;
    private final List<RoomRequestItem> persisted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        handler = new RoomRequestDeactivationHandler(itemRepository, accessControl, subjectService);
        lenient().when(itemRepository.findOpenRequestingClassrooms(anySet(), anyCollection())).thenAnswer(inv -> {
            Collection<RoomRequestStatus> statuses = inv.getArgument(0);
            return persisted.stream().filter(i -> statuses.contains(i.getStatus())).toList();
        });
        lenient().when(subjectService.findByIdsIncludingDeactivated(anyCollection())).thenReturn(List.of(
                new SubjectResponseDto(50L, 101, "Fisica I", "ANUAL", null, true, null)));
        authenticateAs(ACTOR);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, null, List.of()));
    }

    private RoomRequestItem item(long itemId, long requestId, RoomRequestStatus status, Long derivedBuildingId,
            long preferredClassroom) {
        RoomRequest request = RoomRequest.builder().id(requestId).teacherName("Ada Lovelace").subjectId(50L).build();
        RoomRequestItem item = RoomRequestItem.builder().id(itemId).request(request).position(1).status(status)
                .derivedBuildingId(derivedBuildingId).build();
        item.getPreferences().add(RoomPreference.builder().item(item).classroomId(preferredClassroom).position(1)
                .build());
        persisted.add(item);
        return item;
    }

    private static ClassroomDeactivation deactivation(Long... classroomIds) {
        return new ClassroomDeactivation(Set.of(classroomIds), DeactivationTarget.BUILDING);
    }

    @Test
    @DisplayName("con alcance de un edificio: cuenta los 2 items abiertos y el detalle trae solo el derivado a ese edificio")
    void detalleLimitadoAlAlcance() {
        item(10L, 100L, RoomRequestStatus.DERIVED_TO_BUILDING, CENTRAL, 204L);
        item(11L, 101L, RoomRequestStatus.DERIVED_TO_BUILDING, NORTE, 204L);
        when(accessControl.readScope(ACTOR)).thenReturn(Optional.of(Set.of(CENTRAL)));

        DeactivationImpactDto impact = handler.preview(deactivation(204L));

        assertThat(impact.affectedRoomRequests()).isEqualTo(2);
        assertThat(impact.visibleRoomRequests()).hasSize(1);
        assertThat(impact.visibleRoomRequests().getFirst().itemId()).isEqualTo(10L);
        assertThat(impact.releasedClasses()).isZero();
        assertThat(impact.mergedClasses()).isZero();
    }

    @Test
    @DisplayName("con alcance de un edificio, un item sin edificio derivado no es visible pero cuenta")
    void itemSinEdificioDerivadoNoEsVisible() {
        item(10L, 100L, RoomRequestStatus.NEW, null, 204L);
        when(accessControl.readScope(ACTOR)).thenReturn(Optional.of(Set.of(CENTRAL)));

        DeactivationImpactDto impact = handler.preview(deactivation(204L));

        assertThat(impact.affectedRoomRequests()).isEqualTo(1);
        assertThat(impact.visibleRoomRequests()).isEmpty();
    }

    @Test
    @DisplayName("un item RESOLVED o CANCELLED no cuenta; la consulta pide solo los cuatro estados abiertos")
    void soloCuentaLosEstadosAbiertos() {
        item(10L, 100L, RoomRequestStatus.NOTIFIED, CENTRAL, 204L);
        item(11L, 101L, RoomRequestStatus.RESOLVED, CENTRAL, 204L);
        item(12L, 102L, RoomRequestStatus.CANCELLED, CENTRAL, 204L);
        when(accessControl.readScope(ACTOR)).thenReturn(Optional.empty());

        DeactivationImpactDto impact = handler.preview(deactivation(204L));

        assertThat(impact.affectedRoomRequests()).isEqualTo(1);
        assertThat(impact.visibleRoomRequests()).extracting(r -> r.itemId()).containsExactly(10L);
        verify(itemRepository).findOpenRequestingClassrooms(
                Set.of(RoomRequestStatus.NEW, RoomRequestStatus.DERIVED_TO_BUILDING,
                        RoomRequestStatus.IN_EVALUATION, RoomRequestStatus.NOTIFIED),
                Set.of(204L));
    }

    @Test
    @DisplayName("sin restriccion de alcance: el detalle trae estado, docente, materia y las aulas de la baja que el pedido pide")
    void detalleCompleto() {
        RoomRequestItem item = item(10L, 100L, RoomRequestStatus.IN_EVALUATION, CENTRAL, 204L);
        item.getAllocations().add(RoomRequestItemAllocation.builder().item(item).occurrenceId(1L).classroomId(205L)
                .position(1).build());
        item.getAllocations().add(RoomRequestItemAllocation.builder().item(item).occurrenceId(1L).classroomId(999L)
                .position(2).build());
        when(accessControl.readScope(ACTOR)).thenReturn(Optional.empty());

        DeactivationImpactDto impact = handler.apply(deactivation(204L, 205L));

        assertThat(impact.visibleRoomRequests()).singleElement().satisfies(detail -> {
            assertThat(detail.itemId()).isEqualTo(10L);
            assertThat(detail.requestId()).isEqualTo(100L);
            assertThat(detail.status()).isEqualTo("IN_EVALUATION");
            assertThat(detail.teacherName()).isEqualTo("Ada Lovelace");
            assertThat(detail.subjectName()).isEqualTo("Fisica I");
            assertThat(detail.classroomIds()).containsExactlyInAnyOrder(204L, 205L);
        });
    }

    @Test
    @DisplayName("sin autenticacion: cuenta los items y el detalle queda vacio")
    void sinAutenticacionDetalleVacio() {
        SecurityContextHolder.clearContext();
        item(10L, 100L, RoomRequestStatus.NEW, CENTRAL, 204L);

        DeactivationImpactDto impact = handler.preview(deactivation(204L));

        assertThat(impact.affectedRoomRequests()).isEqualTo(1);
        assertThat(impact.visibleRoomRequests()).isEmpty();
        verifyNoInteractions(accessControl);
    }

    @Test
    @DisplayName("sin items que pidan las aulas: impacto vacio y no consulta materias")
    void sinItemsNoConsultaMaterias() {
        when(accessControl.readScope(ACTOR)).thenReturn(Optional.empty());

        DeactivationImpactDto impact = handler.preview(deactivation(204L));

        assertThat(impact.affectedRoomRequests()).isZero();
        assertThat(impact.visibleRoomRequests()).isEmpty();
        verify(subjectService, never()).findByIdsIncludingDeactivated(anyCollection());
    }
}
