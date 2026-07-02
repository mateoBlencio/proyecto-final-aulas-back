package ar.edu.utn.frc.siga.solver.mapper;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.solver.dto.request.AllocationParametersDto;
import ar.edu.utn.frc.siga.solver.dto.request.EventRequestDto;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AllocationRequestMapper {

    private final EventMapper eventMapper;

    public List<AcademicEvent> toEvents(List<EventRequestDto> dtos) {
        return eventMapper.toEvents(dtos);
    }

    public List<Classroom> toClassrooms(List<ClassroomResponseDTO> dtos, AllocationParametersDto params) {
        Set<Integer> excludedIds = params != null
                ? new HashSet<>(params.getExcludedClassroomIds()) : Set.of();
        Set<String> excludedBuildings = params != null
                ? new HashSet<>(params.getExcludedBuildingNames()) : Set.of();

        return dtos.stream()
                .filter(c -> !excludedIds.contains(c.getId()))
                .filter(c -> !excludedBuildings.contains(c.getBuildingName()))
                .map(this::toClassroom)
                .toList();
    }

    private Classroom toClassroom(ClassroomResponseDTO dto) {
        return Classroom.builder()
                .id(dto.getId())
                .roomNumber(dto.getRoomNumber())
                .capacity(dto.getCapacity())
                .build();
    }
}
