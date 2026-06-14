package PF.classroom_allocation.space.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassroomResponseDTO {
    private Integer id;
    private String roomNumber;
    private Integer floor;
    private Integer capacity;
    private Boolean available;
    private Integer buildingId;
    private String buildingName;
    private Integer classroomTypeId;
    private String classroomTypeDescription;
}
