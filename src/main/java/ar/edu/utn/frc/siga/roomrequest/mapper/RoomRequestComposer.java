package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AssignedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.BuildingOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestRowHeaderDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository.ItemAssignedCount;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoomRequestComposer {

    private final RoomRequestMapper mapper;
    private final RoomRequestCatalogMapper catalogMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final ClassroomService classroomService;
    private final BuildingService buildingService;
    private final ClassScheduleService classScheduleService;
    private final RoomRequestItemAllocationRepository allocationRepository;

    private record ActiveCommissionsKey(Long subjectId, LocalDate date) {
    }

    private record Catalogs(
            Map<Long, SubjectResponseDto> subjectsById,
            Map<Long, CommissionResponseDto> commissionsById,
            Map<Long, ClassroomOptionDto> classroomsById,
            Map<Long, AssignedClassroomDto> assignedClassroomsById,
            Map<Long, BuildingOptionDto> buildingsById,
            Map<ActiveCommissionsKey, List<Long>> activeCommissionIdsByKey) {
    }

    public RoomRequestResponseDto compose(RoomRequest request) {
        return compose(List.of(request)).getFirst();
    }

    public List<RoomRequestResponseDto> compose(Collection<RoomRequest> requests) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        Set<Long> buildingIds = new LinkedHashSet<>();
        Set<ActiveCommissionsKey> activeCommissionsKeys = new LinkedHashSet<>();

        for (RoomRequest request : requests) {
            collectSubjectId(request, subjectIds);
            collectCommissionIds(request.getItems(), commissionIds);
            collectPreferredClassroomIds(request.getItems(), classroomIds);
            collectAssignedClassroomIds(request.getItems(), classroomIds);
            collectBuildingIds(request.getItems(), buildingIds);
            collectActiveCommissionsKeys(request.getItems(), activeCommissionsKeys);
        }

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, classroomIds, buildingIds, activeCommissionsKeys);

        List<RoomRequestResponseDto> result = new ArrayList<>(requests.size());
        for (RoomRequest request : requests) {
            result.add(mapper.toDto(request, resolveSubject(request, catalogs), composeItems(request.getItems(), catalogs)));
        }
        return result;
    }

    /** La fila es el ítem, no la solicitud: la cabecera se resuelve una sola vez por {@code request.id} aunque varios ítems compartan página. */
    public List<RoomRequestItemRowDto> composeRows(Collection<RoomRequestItem> items) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Long> buildingIds = new LinkedHashSet<>();

        for (RoomRequestItem item : items) {
            collectSubjectId(item.getRequest(), subjectIds);
        }
        collectCommissionIds(items, commissionIds);
        collectBuildingIds(items, buildingIds);
        Set<ActiveCommissionsKey> activeCommissionsKeys = new LinkedHashSet<>();
        collectActiveCommissionsKeys(items, activeCommissionsKeys);

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, Set.of(), buildingIds, activeCommissionsKeys);
        Map<Long, Integer> assignedCountByItemId = resolveAssignedClassroomCounts(items);

        Map<Long, RoomRequestRowHeaderDto> headersByRequestId = new LinkedHashMap<>();
        List<RoomRequestItemRowDto> result = new ArrayList<>(items.size());
        for (RoomRequestItem item : items) {
            RoomRequest request = item.getRequest();
            RoomRequestRowHeaderDto header = headersByRequestId.computeIfAbsent(request.getId(),
                    id -> mapper.toRowHeaderDto(request, resolveSubject(request, catalogs)));
            BuildingOptionDto derivedBuilding = resolveBuilding(item.getDerivedBuildingId(), catalogs);

            result.add(mapper.toRowDto(item, header, resolveCommissions(item, catalogs),
                    derivedBuilding != null ? derivedBuilding.name() : null,
                    assignedCountByItemId.getOrDefault(item.getId(), 0)));
        }
        return result;
    }

    public RoomRequestItemResponseDto composeItem(RoomRequestItem item) {
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        Set<Long> buildingIds = new LinkedHashSet<>();
        Set<ActiveCommissionsKey> activeCommissionsKeys = new LinkedHashSet<>();

        collectCommissionIds(List.of(item), commissionIds);
        collectPreferredClassroomIds(List.of(item), classroomIds);
        collectAssignedClassroomIds(List.of(item), classroomIds);
        collectBuildingIds(List.of(item), buildingIds);
        collectActiveCommissionsKeys(List.of(item), activeCommissionsKeys);

        Catalogs catalogs = resolveCatalogs(Set.of(), commissionIds, classroomIds, buildingIds, activeCommissionsKeys);
        return composeItems(List.of(item), catalogs).getFirst();
    }

    public RoomRequestItemDetailDto composeDetail(RoomRequestItem item) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        Set<Long> buildingIds = new LinkedHashSet<>();
        Set<ActiveCommissionsKey> activeCommissionsKeys = new LinkedHashSet<>();

        RoomRequest request = item.getRequest();
        collectSubjectId(request, subjectIds);
        collectCommissionIds(List.of(item), commissionIds);
        collectPreferredClassroomIds(List.of(item), classroomIds);
        collectAssignedClassroomIds(List.of(item), classroomIds);
        collectBuildingIds(List.of(item), buildingIds);
        collectActiveCommissionsKeys(List.of(item), activeCommissionsKeys);

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, classroomIds, buildingIds, activeCommissionsKeys);

        RoomRequestItemDetailHeaderDto header = mapper.toDetailHeaderDto(request, resolveSubject(request, catalogs));
        RoomRequestItemResponseDto itemDto = composeItems(List.of(item), catalogs).getFirst();

        return new RoomRequestItemDetailDto(header, itemDto);
    }

    private Catalogs resolveCatalogs(Set<Long> subjectIds, Set<Long> commissionIds, Set<Long> classroomIds,
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

    private Map<Long, Integer> resolveAssignedClassroomCounts(Collection<RoomRequestItem> items) {
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

    private void collectSubjectId(RoomRequest request, Set<Long> subjectIds) {
        if (request.getSubjectId() != null) {
            subjectIds.add(request.getSubjectId());
        }
    }

    private void collectCommissionIds(Collection<RoomRequestItem> items, Set<Long> commissionIds) {
        for (RoomRequestItem item : items) {
            if (item.getCommissionId() != null) {
                commissionIds.add(item.getCommissionId());
            }
        }
    }

    private void collectActiveCommissionsKeys(Collection<RoomRequestItem> items, Set<ActiveCommissionsKey> keys) {
        for (RoomRequestItem item : items) {
            if (item.getCommissionId() == null
                    && item.getRequest().getType() == RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE
                    && item.getDate() != null) {
                keys.add(new ActiveCommissionsKey(item.getRequest().getSubjectId(), item.getDate()));
            }
        }
    }

    private void collectPreferredClassroomIds(Collection<RoomRequestItem> items, Set<Long> classroomIds) {
        for (RoomRequestItem item : items) {
            item.getPreferences().stream().map(RoomPreference::getClassroomId).forEach(classroomIds::add);
        }
    }

    private void collectAssignedClassroomIds(Collection<RoomRequestItem> items, Set<Long> classroomIds) {
        for (RoomRequestItem item : items) {
            item.getAllocations().stream().map(RoomRequestItemAllocation::getClassroomId).forEach(classroomIds::add);
        }
    }

    private void collectBuildingIds(Collection<RoomRequestItem> items, Set<Long> buildingIds) {
        for (RoomRequestItem item : items) {
            if (item.getDerivedBuildingId() != null) {
                buildingIds.add(item.getDerivedBuildingId());
            }
            if (item.getReturnedFromBuildingId() != null) {
                buildingIds.add(item.getReturnedFromBuildingId());
            }
        }
    }

    private List<RoomRequestItemResponseDto> composeItems(Collection<RoomRequestItem> items, Catalogs catalogs) {
        List<RoomRequestItemResponseDto> result = new ArrayList<>(items.size());
        for (RoomRequestItem item : items) {
            result.add(mapper.toDto(item, resolveCommissions(item, catalogs),
                    resolvePreferredClassrooms(item, catalogs), resolveAssignedClassrooms(item, catalogs),
                    resolveBuilding(item.getDerivedBuildingId(), catalogs),
                    resolveBuilding(item.getReturnedFromBuildingId(), catalogs)));
        }
        return result;
    }

    private BuildingOptionDto resolveBuilding(Long buildingId, Catalogs catalogs) {
        return buildingId != null ? catalogs.buildingsById().get(buildingId) : null;
    }

    /** Una fila por posición (1..classroomCount), no por ocurrencia: un REGULAR_ROOM_CHANGE repite la misma aula en varias filas. */
    private List<AssignedClassroomDto> resolveAssignedClassrooms(RoomRequestItem item, Catalogs catalogs) {
        return item.getAllocations().stream()
                .collect(Collectors.toMap(RoomRequestItemAllocation::getPosition,
                        RoomRequestItemAllocation::getClassroomId, (first, ignored) -> first))
                .entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> catalogs.assignedClassroomsById().get(entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    private SubjectResponseDto resolveSubject(RoomRequest request, Catalogs catalogs) {
        return request.getSubjectId() != null ? catalogs.subjectsById().get(request.getSubjectId()) : null;
    }

    private List<CommissionResponseDto> resolveCommissions(RoomRequestItem item, Catalogs catalogs) {
        if (item.getCommissionId() != null) {
            CommissionResponseDto commission = catalogs.commissionsById().get(item.getCommissionId());
            return commission != null ? List.of(commission) : List.of();
        }
        if (item.getRequest().getType() != RoomRequestType.PARTIAL_EXAM_OFF_SCHEDULE || item.getDate() == null) {
            return List.of();
        }
        ActiveCommissionsKey key = new ActiveCommissionsKey(item.getRequest().getSubjectId(), item.getDate());
        return catalogs.activeCommissionIdsByKey().getOrDefault(key, List.of()).stream()
                .map(catalogs.commissionsById()::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ClassroomOptionDto> resolvePreferredClassrooms(RoomRequestItem item, Catalogs catalogs) {
        return item.getPreferences().stream()
                .map(RoomPreference::getClassroomId)
                .map(catalogs.classroomsById()::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
