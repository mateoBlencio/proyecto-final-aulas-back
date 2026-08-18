package ar.edu.utn.frc.siga.allocation.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UnallocatedConflictDto.class, name = "UNALLOCATED"),
        @JsonSubTypes.Type(value = OvercrowdedConflictDto.class, name = "OVERCROWDED"),
        @JsonSubTypes.Type(value = OverlapConflictDto.class, name = "OVERLAP")
})
public sealed interface AllocationConflictDto
        permits UnallocatedConflictDto, OvercrowdedConflictDto, OverlapConflictDto {
}
