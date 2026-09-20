package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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
    private RoomRequestCatalogMapper catalogMapper;
    @Mock
    private SubjectService subjectService;
    @Mock
    private CommissionService commissionService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private BuildingService buildingService;
    @Mock
    private ClassScheduleService classScheduleService;
    @Mock
    private RoomRequestItemAllocationRepository allocationRepository;

    @InjectMocks
    private RoomRequestComposer composer;

    @Test
    @DisplayName("compose de varias solicitudes: los ids de materia y comisión de todos los ítems se piden en un solo findByIds por catálogo, no uno por solicitud")
    void compose_batchesCrossModuleLookupsAcrossRequests() {
        RoomRequest r1 = RoomRequest.builder().id(1L).subjectId(10L).type(RoomRequestType.CONFERENCE).build();
        r1.addItem(RoomRequestItem.builder().id(100L).commissionId(5L).build());

        RoomRequest r2 = RoomRequest.builder().id(2L).subjectId(20L).type(RoomRequestType.CONFERENCE).build();
        r2.addItem(RoomRequestItem.builder().id(200L).commissionId(6L).build());

        composer.compose(List.of(r1, r2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> subjectIds = ArgumentCaptor.forClass(Collection.class);
        verify(subjectService, times(1)).findByIds(subjectIds.capture());
        assertThat(subjectIds.getValue()).containsExactlyInAnyOrder(10L, 20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> commissionIds = ArgumentCaptor.forClass(Collection.class);
        verify(commissionService, times(1)).findByIds(commissionIds.capture());
        assertThat(commissionIds.getValue()).containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    @DisplayName("PARTIAL_EXAM_OFF_SCHEDULE sin comisión puntual: resuelve las comisiones vigentes por fecha con una sola consulta")
    void resolveCommissions_offScheduleWithoutCommission_usesActiveCommissionsByDate() {
        LocalDate date = LocalDate.of(2026, 5, 4);
        RoomRequest request = RoomRequest.builder().id(1L).subjectId(10L)
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).date(date).build();
        request.addItem(item);

        when(classScheduleService.activeCommissionIds(10L, date)).thenReturn(List.of(7L, 8L));
        when(commissionService.findByIds(any())).thenReturn(List.of(
                new CommissionResponseDto(7L, "CUR-7", null),
                new CommissionResponseDto(8L, "CUR-8", null)));

        composer.composeItem(item);

        verify(classScheduleService, times(1)).activeCommissionIds(10L, date);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionResponseDto>> commissions = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), commissions.capture(), any(), any(), any(), any());
        assertThat(commissions.getValue()).extracting(CommissionResponseDto::id).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("dos asignaciones comparten posición (ocurrencia espejo de un pedido con varias aulas): la fila resuelta es la primera, no se duplica")
    void resolveAssignedClassrooms_dedupsByPosition_keepsFirst() {
        RoomRequestItemAllocation main = RoomRequestItemAllocation.builder().classroomId(1L).position(1).build();
        RoomRequestItemAllocation mirror = RoomRequestItemAllocation.builder().classroomId(2L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).allocations(List.of(main, mirror)).build();
        RoomRequest.builder().id(1L).type(RoomRequestType.CONFERENCE).build().addItem(item);

        ClassroomResponseDto classroom1 = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio A", 1L, "Aula");
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom1));
        when(catalogMapper.toAssignedClassroomOptions(List.of(classroom1)))
                .thenReturn(List.of(new AssignedClassroomDto(1L, 101, "Edificio A", 40)));

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssignedClassroomDto>> assignedClassrooms = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), any(), any(), assignedClassrooms.capture(), any(), any());
        assertThat(assignedClassrooms.getValue()).extracting(AssignedClassroomDto::id).containsExactly(1L);
    }
}
