package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AllocationService allocationService;

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

    @Test
    @DisplayName("resolveEnrolledByEventId: sin eventIds, no consulta el servicio de eventos y devuelve vacío")
    void resolveEnrolledByEventId_sinEventos() {
        assertThat(resolver.resolveEnrolledByEventId(Set.of())).isEmpty();
        verifyNoInteractions(academicEventService);
    }

    @Test
    @DisplayName("resolveEnrolledByEventId: batchea todos los eventIds en un solo findByIds")
    void resolveEnrolledByEventId_batchesEventIds() {
        when(academicEventService.findByIds(Set.of(50L, 51L))).thenReturn(List.of(
                recurringEvent(50L, 45), recurringEvent(51L, null)));

        Map<Long, Integer> result = resolver.resolveEnrolledByEventId(Set.of(50L, 51L));

        assertThat(result).containsEntry(50L, 45).containsEntry(51L, null);
    }

    @Test
    @DisplayName("resolveCurrentClassroomsByItemId: ítems sin sourceRecurringEventId no consultan eventos ni asignaciones")
    void resolveCurrentClassroomsByItemId_sinItemsConEvento() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).build();

        assertThat(resolver.resolveCurrentClassroomsByItemId(List.of(item))).isEmpty();
        verifyNoInteractions(academicEventService, allocationService);
    }

    @Test
    @DisplayName("resolveCurrentClassroomsByItemId: con fecha puntual, resuelve el aula de la ocurrencia de ese día")
    void resolveCurrentClassroomsByItemId_withDate_resolvesThatOccurrence() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).sourceRecurringEventId(50L).date(date).build();

        when(academicEventService.findOccurrencesByEventId(50L)).thenReturn(List.of(
                occurrence(900L, 50L, date.minusDays(7)),
                occurrence(901L, 50L, date),
                occurrence(902L, 50L, date.plusDays(7))));
        ClassroomResponseDto classroom = classroom(101L, "Edificio A");
        when(allocationService.findByOccurrenceIds(Set.of(901L))).thenReturn(List.of(allocation(901L, classroom)));
        when(catalogMapper.toClassroomOptions(List.of(classroom)))
                .thenReturn(List.of(new ClassroomOptionDto(101L, 101, "Edificio A")));

        Map<Long, List<ClassroomOptionDto>> result = resolver.resolveCurrentClassroomsByItemId(List.of(item));

        assertThat(result.get(1L)).extracting(ClassroomOptionDto::id).containsExactly(101L);
    }

    @Test
    @DisplayName("resolveCurrentClassroomsByItemId: split en 2 aulas para la misma fecha, devuelve las 2")
    void resolveCurrentClassroomsByItemId_split() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        RoomRequestItem item = RoomRequestItem.builder().id(1L).sourceRecurringEventId(50L).date(date).build();

        when(academicEventService.findOccurrencesByEventId(50L)).thenReturn(List.of(
                occurrence(901L, 50L, date),
                occurrence(902L, 50L, date)));
        ClassroomResponseDto classroomA = classroom(101L, "Edificio A");
        ClassroomResponseDto classroomB = classroom(102L, "Edificio B");
        when(allocationService.findByOccurrenceIds(Set.of(901L, 902L))).thenReturn(List.of(
                allocation(901L, classroomA), allocation(902L, classroomB)));
        when(catalogMapper.toClassroomOptions(List.of(classroomA, classroomB))).thenReturn(List.of(
                new ClassroomOptionDto(101L, 101, "Edificio A"), new ClassroomOptionDto(102L, 102, "Edificio B")));

        Map<Long, List<ClassroomOptionDto>> result = resolver.resolveCurrentClassroomsByItemId(List.of(item));

        assertThat(result.get(1L)).extracting(ClassroomOptionDto::id).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("resolveCurrentClassroomsByItemId: sin fecha puntual, usa la ocurrencia futura más próxima a hoy")
    void resolveCurrentClassroomsByItemId_withoutDate_usesNearestFutureOccurrence() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).sourceRecurringEventId(50L).build();
        LocalDate today = LocalDate.now();

        when(academicEventService.findOccurrencesByEventId(50L)).thenReturn(List.of(
                occurrence(900L, 50L, today.minusDays(7)),
                occurrence(901L, 50L, today.plusDays(7)),
                occurrence(902L, 50L, today.plusDays(14))));
        ClassroomResponseDto classroom = classroom(101L, "Edificio A");
        when(allocationService.findByOccurrenceIds(Set.of(901L))).thenReturn(List.of(allocation(901L, classroom)));
        when(catalogMapper.toClassroomOptions(List.of(classroom)))
                .thenReturn(List.of(new ClassroomOptionDto(101L, 101, "Edificio A")));

        Map<Long, List<ClassroomOptionDto>> result = resolver.resolveCurrentClassroomsByItemId(List.of(item));

        assertThat(result.get(1L)).extracting(ClassroomOptionDto::id).containsExactly(101L);
    }

    @Test
    @DisplayName("resolveCurrentClassroomsByItemId: sin fecha puntual y sin ocurrencias futuras, cae a la última pasada")
    void resolveCurrentClassroomsByItemId_withoutDate_fallsBackToLastPastOccurrence() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).sourceRecurringEventId(50L).build();
        LocalDate today = LocalDate.now();

        when(academicEventService.findOccurrencesByEventId(50L)).thenReturn(List.of(
                occurrence(900L, 50L, today.minusDays(14)),
                occurrence(901L, 50L, today.minusDays(7))));
        ClassroomResponseDto classroom = classroom(101L, "Edificio A");
        when(allocationService.findByOccurrenceIds(Set.of(901L))).thenReturn(List.of(allocation(901L, classroom)));
        when(catalogMapper.toClassroomOptions(List.of(classroom)))
                .thenReturn(List.of(new ClassroomOptionDto(101L, 101, "Edificio A")));

        Map<Long, List<ClassroomOptionDto>> result = resolver.resolveCurrentClassroomsByItemId(List.of(item));

        assertThat(result.get(1L)).extracting(ClassroomOptionDto::id).containsExactly(101L);
    }

    private static SubjectResponseDto subject(Long id) {
        return new SubjectResponseDto(id, 1, "Materia", "1", null);
    }

    private static RecurringEventResponseDto recurringEvent(Long id, Integer enrolled) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, enrolled, null, 0L, null, null, null, null, null);
    }

    private static OccurrenceResponseDto occurrence(Long id, Long eventId, LocalDate date) {
        return new OccurrenceResponseDto(id, eventId, date, OccurrenceStatus.NEEDS_ROOM, null, null);
    }

    private static ClassroomResponseDto classroom(Long id, String buildingName) {
        return new ClassroomResponseDto(id, id.intValue(), 40, 1L, buildingName, 1L, "Aula");
    }

    private static AllocationResponseDto allocation(Long occurrenceId, ClassroomResponseDto classroom) {
        return new AllocationResponseDto(occurrenceId, AllocationSource.MANUAL, null, null,
                occurrence(occurrenceId, 50L, LocalDate.now()), null, classroom);
    }
}
