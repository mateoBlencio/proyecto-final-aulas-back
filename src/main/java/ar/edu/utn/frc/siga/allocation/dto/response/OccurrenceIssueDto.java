package ar.edu.utn.frc.siga.allocation.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UnallocatedIssueDto.class, name = "UNALLOCATED"),
        @JsonSubTypes.Type(value = OvercrowdedIssueDto.class, name = "OVERCROWDED"),
        @JsonSubTypes.Type(value = OverlapIssueDto.class, name = "OVERLAP"),
        @JsonSubTypes.Type(value = NotPermittedIssueDto.class, name = "NOT_PERMITTED")
})
public sealed interface OccurrenceIssueDto
        permits UnallocatedIssueDto, OvercrowdedIssueDto, OverlapIssueDto, NotPermittedIssueDto {
}
