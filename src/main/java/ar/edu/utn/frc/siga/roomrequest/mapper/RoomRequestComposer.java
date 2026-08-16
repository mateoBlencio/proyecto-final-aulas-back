package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomPreference;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resuelve por batch los datos que viven en otros módulos (materia, comisión,
 * aulas) y arma el DTO de respuesta. Un solo {@code findByIds} por tipo, para
 * no caer en N+1 (ADR-002).
 */
@Component
@RequiredArgsConstructor
public class RoomRequestComposer {

    private final RoomRequestMapper mapper;
    private final RoomRequestCatalogMapper catalogMapper;
    private final SubjectService subjectService;
    private final CommissionService commissionService;
    private final ClassroomService classroomService;

    public RoomRequestResponseDto compose(RoomRequest request) {
        return compose(List.of(request)).getFirst();
    }

    public List<RoomRequestResponseDto> compose(Collection<RoomRequest> requests) {
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> commissionIds = new LinkedHashSet<>();
        Set<Integer> classroomIds = new LinkedHashSet<>();

        for (RoomRequest request : requests) {
            if (request.getSubjectId() != null) {
                subjectIds.add(request.getSubjectId());
            }
            for (RoomRequestItem item : request.getItems()) {
                if (item.getCommissionId() != null) {
                    commissionIds.add(item.getCommissionId());
                }
                if (item.getCurrentClassroomId() != null) {
                    classroomIds.add(item.getCurrentClassroomId());
                }
                item.getPreferences().stream().map(RoomPreference::getClassroomId).forEach(classroomIds::add);
            }
        }

        Map<Long, SubjectResponseDto> subjectsById =
                Maps.byId(subjectService.findByIds(subjectIds), SubjectResponseDto::id);
        Map<Long, CommissionResponseDto> commissionsById =
                Maps.byId(commissionService.findByIds(commissionIds), CommissionResponseDto::id);
        Map<Integer, ClassroomOptionDto> classroomsById =
                Maps.byId(catalogMapper.toClassroomOptions(classroomService.findByIds(classroomIds)),
                        ClassroomOptionDto::id);

        List<RoomRequestResponseDto> result = new ArrayList<>(requests.size());
        for (RoomRequest request : requests) {
            SubjectResponseDto subject = request.getSubjectId() != null
                    ? subjectsById.get(request.getSubjectId())
                    : null;
            result.add(mapper.toDto(request, subject, composeItems(request, commissionsById, classroomsById)));
        }
        return result;
    }

    private List<RoomRequestItemResponseDto> composeItems(RoomRequest request,
                                                          Map<Long, CommissionResponseDto> commissionsById,
                                                          Map<Integer, ClassroomOptionDto> classroomsById) {
        List<RoomRequestItemResponseDto> items = new ArrayList<>(request.getItems().size());
        for (RoomRequestItem item : request.getItems()) {
            CommissionResponseDto commission = item.getCommissionId() != null
                    ? commissionsById.get(item.getCommissionId())
                    : null;
            ClassroomOptionDto currentClassroom = item.getCurrentClassroomId() != null
                    ? classroomsById.get(item.getCurrentClassroomId())
                    : null;
            List<ClassroomOptionDto> preferred = item.getPreferences().stream()
                    .map(RoomPreference::getClassroomId)
                    .map(classroomsById::get)
                    .toList();
            items.add(mapper.toDto(item, commission, currentClassroom, preferred));
        }
        return items;
    }
}
