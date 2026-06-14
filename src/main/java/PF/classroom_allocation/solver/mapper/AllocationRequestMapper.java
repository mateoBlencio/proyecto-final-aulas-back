package PF.classroom_allocation.solver.mapper;

import PF.classroom_allocation.solver.dto.request.AllocationParametersDto;
import PF.classroom_allocation.solver.dto.request.ClassroomRequestDto;
import PF.classroom_allocation.solver.dto.request.EventRequestDto;
import PF.classroom_allocation.solver.model.Classroom;
import PF.classroom_allocation.solver.model.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AllocationRequestMapper {

    private final EventMapper eventMapper;
    private final ClassroomMapper classroomMapper;

    public List<Event> toEvents(List<EventRequestDto> dtos) {
        return eventMapper.toEvents(dtos);
    }

    public List<Classroom> toClassrooms(List<ClassroomRequestDto> dtos, AllocationParametersDto params) {
        return classroomMapper.toClassrooms(dtos, params);
    }
}
