package ar.edu.utn.frc.classroom_allocation.space.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BuildingResponseDto {
    private Integer id;
    private String name;
    private Integer floorCount;
    private Boolean active;
}
