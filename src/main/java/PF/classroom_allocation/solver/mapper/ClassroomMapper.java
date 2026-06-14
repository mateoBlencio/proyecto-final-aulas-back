package PF.classroom_allocation.solver.mapper;

import PF.classroom_allocation.solver.dto.request.AllocationParametersDto;
import PF.classroom_allocation.solver.dto.request.ClassroomRequestDto;
import PF.classroom_allocation.solver.dto.response.ClassroomSummaryDto;
import PF.classroom_allocation.solver.model.Classroom;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ClassroomMapper {

    public List<Classroom> toClassrooms(List<ClassroomRequestDto> dtos, AllocationParametersDto params) {
        Set<String> excludedIds = params != null
                ? new HashSet<>(params.getExcludedClassroomIds()) : Set.of();
        Set<String> excludedBuildings = params != null
                ? new HashSet<>(params.getExcludedBuildingNames()) : Set.of();

        return dtos.stream()
                .filter(c -> !excludedIds.contains(c.getId()))
                .filter(c -> !excludedBuildings.contains(c.getBuilding()))
                .map(this::toClassroom)
                .toList();
    }

    public Classroom toClassroom(ClassroomRequestDto dto) {
        return new Classroom(dto.getId(), dto.getName(), dto.getCapacityM2());
    }

    public ClassroomSummaryDto toSummary(ClassroomRequestDto dto) {
        return ClassroomSummaryDto.builder()
                .id(dto.getId())
                .name(dto.getName())
                .building(dto.getBuilding())
                .capacityM2(dto.getCapacityM2())
                .build();
    }
}
