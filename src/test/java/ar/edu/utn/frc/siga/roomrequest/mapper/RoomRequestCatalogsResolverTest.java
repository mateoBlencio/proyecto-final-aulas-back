package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.ActiveCommissionsKey;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogsResolver.Catalogs;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository.ItemAssignedCount;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestCatalogsResolver")
class RoomRequestCatalogsResolverTest {

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
    private RoomRequestCatalogsResolver resolver;

    @Test
    @DisplayName("resolve: pide subjectService/commissionService por los ids exactos que le pasan")
    void resolve_batchesByGivenIds() {
        when(subjectService.findByIds(Set.of(10L))).thenReturn(List.of(subject(10L)));
        when(commissionService.findByIds(Set.of(5L))).thenReturn(List.of(new CommissionResponseDto(5L, "CUR-5", null)));

        Catalogs catalogs = resolver.resolve(Set.of(10L), Set.of(5L), Set.of(), Set.of(), Set.of());

        assertThat(catalogs.subjectsById()).containsOnlyKeys(10L);
        assertThat(catalogs.commissionsById()).containsOnlyKeys(5L);
    }

    @Test
    @DisplayName("resolve: por cada ActiveCommissionsKey consulta classScheduleService y suma esos ids al findByIds de comisiones")
    void resolve_expandsActiveCommissionsKeysIntoCommissionLookup() {
        LocalDate date = LocalDate.of(2026, 5, 4);
        ActiveCommissionsKey key = new ActiveCommissionsKey(10L, date);
        when(classScheduleService.activeCommissionIds(10L, date)).thenReturn(List.of(7L, 8L));
        when(commissionService.findByIds(any())).thenReturn(List.of(
                new CommissionResponseDto(7L, "CUR-7", null),
                new CommissionResponseDto(8L, "CUR-8", null)));

        Catalogs catalogs = resolver.resolve(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(key));

        verify(classScheduleService).activeCommissionIds(10L, date);
        assertThat(catalogs.activeCommissionIdsByKey()).containsEntry(key, List.of(7L, 8L));
        assertThat(catalogs.commissionsById()).containsOnlyKeys(7L, 8L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> commissionIds = ArgumentCaptor.forClass(Collection.class);
        verify(commissionService, times(1)).findByIds(commissionIds.capture());
        assertThat(commissionIds.getValue()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    @DisplayName("resolve: classroomIds se resuelven una sola vez y alimentan tanto las opciones como las asignadas")
    void resolve_classroomsFeedBothOptionsAndAssigned() {
        ClassroomResponseDto classroom = new ClassroomResponseDto(101L, 101, 40, 1L, "Edificio A", 1L, "Aula");
        when(classroomService.findByIds(Set.of(101L))).thenReturn(List.of(classroom));
        when(catalogMapper.toClassroomOptions(List.of(classroom)))
                .thenReturn(List.of(new ClassroomOptionDto(101L, 101, "Edificio A")));
        when(catalogMapper.toAssignedClassroomOptions(List.of(classroom)))
                .thenReturn(List.of(new AssignedClassroomDto(101L, 101, "Edificio A", 40)));

        Catalogs catalogs = resolver.resolve(Set.of(), Set.of(), Set.of(101L), Set.of(), Set.of());

        assertThat(catalogs.classroomsById()).containsOnlyKeys(101L);
        assertThat(catalogs.assignedClassroomsById()).containsOnlyKeys(101L);
        verify(classroomService, times(1)).findByIds(any());
    }

    @Test
    @DisplayName("resolveAssignedClassroomCounts: sin ítems, no consulta el repositorio y devuelve vacío")
    void resolveAssignedClassroomCounts_sinItems() {
        assertThat(resolver.resolveAssignedClassroomCounts(List.of())).isEmpty();
        verifyNoInteractions(allocationRepository);
    }

    @Test
    @DisplayName("resolveAssignedClassroomCounts: batchea todos los itemIds en una sola consulta")
    void resolveAssignedClassroomCounts_batchesItemIds() {
        RoomRequestItem item1 = RoomRequestItem.builder().id(1L).build();
        RoomRequestItem item2 = RoomRequestItem.builder().id(2L).build();
        when(allocationRepository.countAssignedClassroomsByItemIds(List.of(1L, 2L))).thenReturn(List.of(
                new ItemAssignedCount(1L, 2L), new ItemAssignedCount(2L, 0L)));

        Map<Long, Integer> result = resolver.resolveAssignedClassroomCounts(List.of(item1, item2));

        assertThat(result).containsEntry(1L, 2).containsEntry(2L, 0);
    }

    private static SubjectResponseDto subject(Long id) {
        return new SubjectResponseDto(id, 1, "Materia", "1", null);
    }
}
