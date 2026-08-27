package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.util.Maps;
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
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RoomRequestComposer {

    private final RoomRequestMapper mapper;
    private final RoomRequestCatalogMapper catalogMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final ClassroomService classroomService;

    private record Catalogs(
            Map<Long, SubjectResponseDto> subjectsById,
            Map<Long, CommissionResponseDto> commissionsById,
            Map<Integer, ClassroomOptionDto> classroomsById) {
    }

    public RoomRequestResponseDto compose(RoomRequest request) {
        return compose(List.of(request)).getFirst();
    }

    public List<RoomRequestResponseDto> compose(Collection<RoomRequest> requests) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();

        for (RoomRequest request : requests) {
            collectSubjectId(request, subjectIds);
            collectCommissionIds(request.getItems(), commissionIds);
            collectClassroomIds(request.getItems(), classroomIds);
        }

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, classroomIds);

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

        for (RoomRequestItem item : items) {
            collectSubjectId(item.getRequest(), subjectIds);
        }
        collectCommissionIds(items, commissionIds);

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, Set.of());

        Map<Long, RoomRequestRowHeaderDto> headersByRequestId = new LinkedHashMap<>();
        List<RoomRequestItemRowDto> result = new ArrayList<>(items.size());
        for (RoomRequestItem item : items) {
            RoomRequest request = item.getRequest();
            RoomRequestRowHeaderDto header = headersByRequestId.computeIfAbsent(request.getId(),
                    id -> mapper.toRowHeaderDto(request, resolveSubject(request, catalogs)));

            result.add(mapper.toRowDto(item, header, resolveCommission(item, catalogs)));
        }
        return result;
    }

    public RoomRequestItemDetailDto composeDetail(RoomRequestItem item) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();

        RoomRequest request = item.getRequest();
        collectSubjectId(request, subjectIds);
        collectCommissionIds(List.of(item), commissionIds);
        collectClassroomIds(List.of(item), classroomIds);

        Catalogs catalogs = resolveCatalogs(subjectIds, commissionIds, classroomIds);

        RoomRequestItemDetailHeaderDto header = mapper.toDetailHeaderDto(request, resolveSubject(request, catalogs));
        RoomRequestItemResponseDto itemDto = composeItems(List.of(item), catalogs).getFirst();

        return new RoomRequestItemDetailDto(header, itemDto);
    }

    private Catalogs resolveCatalogs(Set<Long> subjectIds, Set<Long> commissionIds, Set<Integer> classroomIds) {
        Map<Long, SubjectResponseDto> subjectsById =
                Maps.byId(subjectService.findByIds(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissionsById =
                Maps.byId(commissionService.findByIds(commissionIds), CommissionResponseDto::id);
        Map<Integer, ClassroomOptionDto> classroomsById =
                Maps.byId(catalogMapper.toClassroomOptions(classroomService.findByIds(classroomIds)),
                        ClassroomOptionDto::id);
        return new Catalogs(subjectsById, commissionsById, classroomsById);
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

    private void collectClassroomIds(Collection<RoomRequestItem> items, Set<Integer> classroomIds) {
        for (RoomRequestItem item : items) {
            if (item.getCurrentClassroomId() != null) {
                classroomIds.add(item.getCurrentClassroomId());
            }
            item.getPreferences().stream().map(RoomPreference::getClassroomId).forEach(classroomIds::add);
        }
    }

    private List<RoomRequestItemResponseDto> composeItems(Collection<RoomRequestItem> items, Catalogs catalogs) {
        List<RoomRequestItemResponseDto> result = new ArrayList<>(items.size());
        for (RoomRequestItem item : items) {
            result.add(mapper.toDto(item, resolveCommission(item, catalogs),
                    resolveCurrentClassroom(item, catalogs), resolvePreferredClassrooms(item, catalogs)));
        }
        return result;
    }

    private SubjectResponseDto resolveSubject(RoomRequest request, Catalogs catalogs) {
        return request.getSubjectId() != null ? catalogs.subjectsById().get(request.getSubjectId()) : null;
    }

    private CommissionResponseDto resolveCommission(RoomRequestItem item, Catalogs catalogs) {
        return item.getCommissionId() != null ? catalogs.commissionsById().get(item.getCommissionId()) : null;
    }

    private ClassroomOptionDto resolveCurrentClassroom(RoomRequestItem item, Catalogs catalogs) {
        return item.getCurrentClassroomId() != null ? catalogs.classroomsById().get(item.getCurrentClassroomId()) : null;
    }

    private List<ClassroomOptionDto> resolvePreferredClassrooms(RoomRequestItem item, Catalogs catalogs) {
        return item.getPreferences().stream()
                .map(RoomPreference::getClassroomId)
                .map(catalogs.classroomsById()::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
