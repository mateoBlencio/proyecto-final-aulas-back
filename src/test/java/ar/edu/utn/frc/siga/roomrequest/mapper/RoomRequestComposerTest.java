package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OverlappingItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ReleasedDateDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.ActiveCommissionsKey;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.Catalogs;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestComposer")
class RoomRequestComposerTest {

    @Mock
    private RoomRequestMapper mapper;
    @Mock
    private RoomRequestCatalogsResolver catalogsResolver;
    @Mock
    private RoomRequestItemRepository itemRepository;

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
                7L, new CommissionResponseDto(7L, "CUR-7", null, null),
                8L, new CommissionResponseDto(8L, "CUR-8", null, null)),
                Map.of(), Map.of(), Map.of(), Map.of(key, List.of(7L, 8L)));
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionResponseDto>> commissions = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), commissions.capture(), any(), any(), any(), any(), any(), any(), any(), any());
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
        verify(mapper).toDto(eq(item), any(), any(), any(), assignedClassrooms.capture(), any(), any(), any(), any(), any());
        assertThat(assignedClassrooms.getValue()).extracting(AssignedClassroomDto::id).containsExactly(1L);
    }

    @Test
    @DisplayName("cambio regular reasignado: las aulas de las clases pasadas no figuran como vigentes")
    void resolveAssignedClassrooms_ignoraClasesPasadas() {
        RoomRequestItemAllocation past = RoomRequestItemAllocation.builder()
                .occurrenceId(10L).classroomId(1L).position(1).build();
        RoomRequestItemAllocation pastMirror = RoomRequestItemAllocation.builder()
                .occurrenceId(11L).classroomId(3L).position(2).build();
        RoomRequestItemAllocation future = RoomRequestItemAllocation.builder()
                .occurrenceId(20L).classroomId(2L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).allocations(List.of(past, pastMirror, future)).build();
        RoomRequest.builder().id(1L).type(RoomRequestType.REGULAR_ROOM_CHANGE).build().addItem(item);

        Catalogs catalogs = new Catalogs(Map.of(), Map.of(), Map.of(),
                Map.of(1L, new AssignedClassroomDto(1L, 101, "Edificio A", 40),
                        2L, new AssignedClassroomDto(2L, 102, "Edificio A", 40),
                        3L, new AssignedClassroomDto(3L, 103, "Edificio A", 40)), Map.of(), Map.of());
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);
        when(catalogsResolver.resolveFutureOccurrenceIdsByItemId(List.of(item))).thenReturn(Map.of(100L, Set.of(20L)));

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssignedClassroomDto>> assignedClassrooms = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), any(), any(), any(), assignedClassrooms.capture(), any(), any(), any(), any(), any());
        assertThat(assignedClassrooms.getValue()).extracting(AssignedClassroomDto::id).containsExactly(2L);
    }

    @Test
    @DisplayName("cambio regular sin ninguna clase futura: se muestran las aulas que tiene en vez de ninguna")
    void resolveAssignedClassrooms_sinClasesFuturasMuestraTodas() {
        RoomRequestItemAllocation past = RoomRequestItemAllocation.builder()
                .occurrenceId(10L).classroomId(1L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).allocations(List.of(past)).build();
        RoomRequest.builder().id(1L).type(RoomRequestType.REGULAR_ROOM_CHANGE).build().addItem(item);

        Catalogs catalogs = new Catalogs(Map.of(), Map.of(), Map.of(),
                Map.of(1L, new AssignedClassroomDto(1L, 101, "Edificio A", 40)), Map.of(), Map.of());
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);
        when(catalogsResolver.resolveFutureOccurrenceIdsByItemId(List.of(item))).thenReturn(Map.of(100L, Set.of()));

        composer.composeItem(item);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssignedClassroomDto>> assignedClassrooms = ArgumentCaptor.forClass(List.class);
        verify(mapper).toDto(eq(item), any(), any(), any(), assignedClassrooms.capture(), any(), any(), any(), any(), any());
        assertThat(assignedClassrooms.getValue()).extracting(AssignedClassroomDto::id).containsExactly(1L);
    }

    @Test
    @DisplayName("composeItem pasa al mapper las fechas liberadas que resuelve el catalogsResolver")
    void composeItem_passesReleasedTargetDatesToMapper() {
        RoomRequest request = RoomRequest.builder().id(1L).type(RoomRequestType.ONE_TIME_ROOM_CHANGE).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).build();
        request.addItem(item);
        List<ReleasedDateDto> released = List.of(new ReleasedDateDto(
                LocalDate.of(2026, 5, 4), ReleaseReason.VIRTUAL_CLASS));
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());
        when(catalogsResolver.resolveReleasedTargetDatesByItemId(List.of(item))).thenReturn(Map.of(100L, released));

        composer.composeItem(item);

        verify(mapper).toDto(eq(item), any(), any(), any(), any(), any(), any(), any(), any(), eq(released));
    }

    @Test
    @DisplayName("caso 9: la fila marca overlapsWithOtherSubject si se pisa con otra materia (misma comisión), y no si la comisión difiere")
    void composeRows_flagsOtherSubjectOverlap() {
        RoomRequestItem mine = offScheduleItem(1L, 10L, 7L, 12, 14, RoomRequestStatus.NEW);
        RoomRequestItem otherSubject = offScheduleItem(2L, 20L, 7L, 13, 15, RoomRequestStatus.NEW);
        RoomRequestItem alsoOverlapping = offScheduleItem(3L, 10L, 7L, 14, 16, RoomRequestStatus.NEW);
        RoomRequestItem otherCommission = offScheduleItem(4L, 10L, 8L, 17, 18, RoomRequestStatus.NEW);
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());
        when(itemRepository.findActiveOffScheduleItemsByDateIn(any()))
                .thenReturn(List.of(mine, otherSubject, alsoOverlapping, otherCommission));

        composer.composeRows(List.of(mine, alsoOverlapping, otherCommission), Map.of());

        verify(mapper).toRowDto(eq(mine), any(), any(), any(), any(), any(), eq(true));
        verify(mapper).toRowDto(eq(alsoOverlapping), any(), any(), any(), any(), any(), eq(true));
        verify(mapper).toRowDto(eq(otherCommission), any(), any(), any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("caso 9: el choque con la misma materia bloquea al crear y no se muestra como aviso")
    void composeRows_sameSubjectOverlapIsNotWarning() {
        RoomRequestItem mine = offScheduleItem(1L, 10L, 7L, 12, 14, RoomRequestStatus.NEW);
        RoomRequestItem sameSubject = offScheduleItem(2L, 10L, 7L, 13, 15, RoomRequestStatus.NEW);
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());
        when(itemRepository.findActiveOffScheduleItemsByDateIn(any())).thenReturn(List.of(mine, sameSubject));

        composer.composeRows(List.of(mine), Map.of());

        verify(mapper).toRowDto(eq(mine), any(), any(), any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("caso 9: un pedido cancelado no avisa ni consulta superposiciones")
    void composeRows_ignoresCancelled() {
        RoomRequestItem cancelledMine = offScheduleItem(1L, 10L, 7L, 12, 14, RoomRequestStatus.CANCELLED);
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());

        composer.composeRows(List.of(cancelledMine), Map.of());

        verify(mapper).toRowDto(eq(cancelledMine), any(), any(), any(), any(), any(), eq(false));
        verifyNoInteractions(itemRepository);
    }

    @Test
    @DisplayName("caso 9: el detalle lista los pedidos de otra materia con su materia y comisión; 'todas' sale con comisión null")
    void composeDetail_listsOverlappingItems() {
        RoomRequestItem mine = offScheduleItem(1L, 10L, 7L, 12, 14, RoomRequestStatus.NEW);
        RoomRequestItem withCommission = offScheduleItem(2L, 20L, 7L, 13, 15, RoomRequestStatus.NEW);
        RoomRequestItem allCommissions = offScheduleItem(3L, 30L, null, 12, 14, RoomRequestStatus.NEW);
        Catalogs catalogs = new Catalogs(
                Map.of(20L, subject(20L, "Física I"), 30L, subject(30L, "Análisis")),
                Map.of(7L, new CommissionResponseDto(7L, "1K1", null, null)),
                Map.of(), Map.of(), Map.of(), Map.of());
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(catalogs);
        when(itemRepository.findActiveOffScheduleItemsByDateIn(any()))
                .thenReturn(List.of(mine, withCommission, allCommissions));

        RoomRequestItemDetailDto detail = composer.composeDetail(mine, List.of());

        assertThat(detail.overlappingItems()).containsExactlyInAnyOrder(
                new OverlappingItemDto(2L, "Física I", "1K1", DATE, LocalTime.of(13, 0), LocalTime.of(15, 0)),
                new OverlappingItemDto(3L, "Análisis", null, DATE, LocalTime.of(12, 0), LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("caso 9: el detalle de un pedido que no es parcial fuera de horario no consulta superposiciones")
    void composeDetail_otherTypes_noOverlapQuery() {
        RoomRequest request = RoomRequest.builder().id(1L).subjectId(10L).type(RoomRequestType.CONFERENCE).build();
        RoomRequestItem item = RoomRequestItem.builder().id(100L).build();
        request.addItem(item);
        when(catalogsResolver.resolve(any(), any(), any(), any(), any())).thenReturn(emptyCatalogs());

        RoomRequestItemDetailDto detail = composer.composeDetail(item, List.of());

        assertThat(detail.overlappingItems()).isEmpty();
        verifyNoInteractions(itemRepository);
    }

    private static final LocalDate DATE = LocalDate.of(2026, 11, 14);

    private static SubjectResponseDto subject(Long id, String name) {
        return new SubjectResponseDto(id, 1, name, null, null, true, null);
    }

    private static RoomRequestItem offScheduleItem(Long id, Long subjectId, Long commissionId, int fromHour,
                                                    int toHour, RoomRequestStatus status) {
        RoomRequest request = RoomRequest.builder().id(id).subjectId(subjectId)
                .type(RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE).build();
        RoomRequestItem item = RoomRequestItem.builder().id(id).commissionId(commissionId).date(DATE)
                .startTime(LocalTime.of(fromHour, 0)).duration(Duration.ofHours(toHour - fromHour))
                .status(status).build();
        request.addItem(item);
        return item;
    }

    private static Catalogs emptyCatalogs() {
        return new Catalogs(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
