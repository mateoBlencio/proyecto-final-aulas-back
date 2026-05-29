package PF.classroom_allocation.solver.mapper;

// @Mapper(componentModel = "spring")
public interface SchedulingMapper {

//    AcademicClass toClassDomain(ClassDTO dto);
//    List<AcademicClass> toClassDomain(List<ClassDTO> dtos);
//
//    Room toRoomDomain(RoomDTO dto);
//    List<Room> toRoomDomain(List<RoomDTO> dtos);
//
//    @Mapping(target = "className",    source = "academicClass.name")
//    @Mapping(target = "roomName",     source = "room.name")
//    @Mapping(target = "enrolled",     source = "academicClass.enrolled")
//    @Mapping(target = "capacity",     source = "room.capacity")
//    @Mapping(target = "idleCapacity", expression = "java(assignment.getIdleCapacity())")
//    @Mapping(target = "startTime",    expression = "java(formatTime(assignment.getAcademicClass().getStartTime()))")
//    @Mapping(target = "endTime",      expression = "java(formatTime(assignment.getAcademicClass().getEndTime()))")
//    AssignmentDTO toAssignmentDTO(Assignment assignment);
//
//    List<AssignmentDTO> toAssignmentDTOs(List<Assignment> assignments);
//
//    default SchedulingResultDTO toResultDTO(List<Assignment> assignments) {
//        return SchedulingResultDTO.builder()
//                .status("OPTIMAL")
//                .objectiveValue(assignments.stream().mapToInt(Assignment::getIdleCapacity).sum() * 1.0)
//                .assignments(toAssignmentDTOs(assignments))
//                .build();
//    }
//
//    default String formatTime(int minutes) {
//        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
//    }
}
