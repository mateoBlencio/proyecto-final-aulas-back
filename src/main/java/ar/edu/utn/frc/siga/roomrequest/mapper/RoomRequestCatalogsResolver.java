package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.BuildingOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository.ItemAssignedCount;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Batchea por ID todo el catálogo cross-módulo (materia, comisión, aula, edificio) que necesita el Composer para armar sus DTOs. */
@Component
@RequiredArgsConstructor
class RoomRequestCatalogsResolver {

    private final RoomRequestCatalogMapper catalogMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final ClassroomService classroomService;
    private final BuildingService buildingService;
    private final ClassScheduleService classScheduleService;
    private final RoomRequestItemAllocationRepository allocationRepository;

    record ActiveCommissionsKey(Long subjectId, LocalDate date) {
    }

    record Catalogs(
            Map<Long, SubjectResponseDto> subjectsById,
            Map<Long, CommissionResponseDto> commissionsById,
            Map<Long, ClassroomOptionDto> classroomsById,
            Map<Long, AssignedClassroomDto> assignedClassroomsById,
            Map<Long, BuildingOptionDto> buildingsById,
            Map<ActiveCommissionsKey, List<Long>> activeCommissionIdsByKey) {
    }

    Catalogs resolve(Set<Long> subjectIds, Set<Long> commissionIds, Set<Long> classroomIds,
                     Set<Long> buildingIds, Set<ActiveCommissionsKey> activeCommissionsKeys) {
        Map<Long, SubjectResponseDto> subjectsById =
                Maps.byId(subjectService.findByIds(subjectIds), SubjectResponseDto::id);
        List<ClassroomResponseDto> classrooms = classroomService.findByIds(classroomIds);
        Map<Long, ClassroomOptionDto> classroomsById =
                Maps.byId(catalogMapper.toClassroomOptions(classrooms), ClassroomOptionDto::id);
        Map<Long, AssignedClassroomDto> assignedClassroomsById =
                Maps.byId(catalogMapper.toAssignedClassroomOptions(classrooms), AssignedClassroomDto::id);
        Map<Long, BuildingOptionDto> buildingsById =
                Maps.byId(catalogMapper.toBuildingOptions(buildingService.findByIds(buildingIds)),
                        BuildingOptionDto::id);

        Map<ActiveCommissionsKey, List<Long>> activeCommissionIdsByKey = new LinkedHashMap<>();
        Set<Long> allCommissionIds = new LinkedHashSet<>(commissionIds);
        for (ActiveCommissionsKey key : activeCommissionsKeys) {
            List<Long> activeIds = classScheduleService.activeCommissionIds(key.subjectId(), key.date());
            activeCommissionIdsByKey.put(key, activeIds);
            allCommissionIds.addAll(activeIds);
        }
        Map<Long, CommissionResponseDto> commissionsById =
                Maps.byId(commissionService.findByIds(allCommissionIds), CommissionResponseDto::id);

        return new Catalogs(subjectsById, commissionsById, classroomsById, assignedClassroomsById, buildingsById,
                activeCommissionIdsByKey);
    }

    Map<Long, Integer> resolveAssignedClassroomCounts(Collection<RoomRequestItem> items) {
        List<Long> itemIds = items.stream().map(RoomRequestItem::getId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (ItemAssignedCount count : allocationRepository.countAssignedClassroomsByItemIds(itemIds)) {
            result.put(count.itemId(), count.assignedCount().intValue());
        }
        return result;
    }
}
