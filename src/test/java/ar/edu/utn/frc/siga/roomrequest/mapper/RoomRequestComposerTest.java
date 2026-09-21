package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.ActiveCommissionsKey;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.Catalogs;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestComposer")
class RoomRequestComposerTest {

    @Mock
    private RoomRequestMapper mapper;
    @Mock
    private RoomRequestCatalogsResolver catalogsResolver;

    @InjectMocks
    private RoomRequestComposer composer;

    @Test
    @DisplayName("compose de varias solicitudes: los ids de materia y comisión de todos los ítems se piden en un solo resolve, no uno por solicitud")
    void compose_collectsIdsAcrossRequestsAndCallsResolverOnce() {
        RoomRequest r1 = RoomRequest.builder().id(1L).subjectId(10L).type(RoomRequestType.CONFERENCE).build();
        r1.addItem(RoomRequestItem.builder().id(100L).commissionId(5L).build());

        RoomRequest r2 = RoomRequest.builder().id(2L).subjectId(20L).type(RoomRequestType.CONFERENCE).build();
        r2.addItem(RoomRequestItem.builder().id(200L).commissionId(6L).build());

        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());

        composer.compose(List.of(r1, r2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> subjectIds = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> commissionIds = ArgumentCaptor.forClass(Set.class);
        verify(catalogsResolver, times(1)).resolve(subjectIds.capture(), commissionIds.capture(), any(), any(), any());
        assertThat(subjectIds.getValue()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(commissionIds.getValue()).containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    @DisplayName("PARTIAL_EXAM_OFF_SCHEDULE sin comisión puntual: usa las comisiones vigentes que el resolver dejó en activeCommissionIdsByKey")
    void composeItem_offScheduleWithoutCommission_usesActiveCommissionsFromCatalogs() {
        LocalDate date = LocalDate.of(2026, 5, 4);
        RoomRequest request = RoomRequest.builder().id(1L).subjectId(10L)
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).date(date).build();
        request.addItem(item);

        ActiveCommissionsKey key = new ActiveCommissionsKey(10L, date);
        Catalogs catalogs = new Catalogs(Map.of(), Map.of(
                7L, new CommissionResponseDto(7L, "CUR-7", null),
                8L, new CommissionResponseDto(8L, "CUR-8", null)),
                Map.of(), Map.of(), Map.of(), Map.of(key, List.of(7L, 8L)));
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionResponseDto>> commissions = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), commissions.capture(), any(), any(), any(), any(), any(), any());
        assertThat(commissions.getValue()).extracting(CommissionResponseDto::id).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("dos asignaciones comparten posición (ocurrencia espejo de un pedido con varias aulas): la fila resuelta es la primera, no se duplica")
    void resolveAssignedClassrooms_dedupsByPosition_keepsFirst() {
        RoomRequestItemAllocation main = RoomRequestItemAllocation.builder().classroomId(1L).position(1).build();
        RoomRequestItemAllocation mirror = RoomRequestItemAllocation.builder().classroomId(2L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).allocations(List.of(main, mirror)).build();
        RoomRequest.builder().id(1L).type(RoomRequestType.CONFERENCE).build().addItem(item);

        Catalogs catalogs = new Catalogs(Map.of(), Map.of(), Map.of(),
                Map.of(1L, new AssignedClassroomDto(1L, 101, "Edificio A", 40)), Map.of(), Map.of());
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssignedClassroomDto>> assignedClassrooms = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), any(), any(), any(), assignedClassrooms.capture(), any(), any(), any());
        assertThat(assignedClassrooms.getValue()).extracting(AssignedClassroomDto::id).containsExactly(1L);
    }

    private static Catalogs emptyCatalogs() {
        return new Catalogs(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
