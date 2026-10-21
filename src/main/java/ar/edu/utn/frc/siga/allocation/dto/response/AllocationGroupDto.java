package ar.edu.utn.frc.siga.allocation.dto.response;

import org.springframework.modulith.NamedInterface;

import java.util.List;

/** Asignaciones del mismo evento y fecha. {@code capacity} es null si alguna aula no tiene capacidad. */
@NamedInterface("api")
public record AllocationGroupDto(
        int roomCount,
        Integer capacity,
        List<AllocationSiblingDto> siblings
) {

    public AllocationGroupDto withSiblings(List<AllocationSiblingDto> visible) {
        return new AllocationGroupDto(roomCount, capacity, visible);
    }
}
