package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

import java.util.ArrayList;
import java.util.List;

@NamedInterface("api")
public record DeactivationImpactDto(
        int releasedClasses,
        int mergedClasses,
        int affectedRoomRequests,
        List<AffectedRoomRequestDto> visibleRoomRequests
) {

    public static final DeactivationImpactDto EMPTY = new DeactivationImpactDto(0, 0, 0, List.of());

    public DeactivationImpactDto plus(DeactivationImpactDto other) {
        List<AffectedRoomRequestDto> visible = new ArrayList<>(visibleRoomRequests);
        visible.addAll(other.visibleRoomRequests);
        return new DeactivationImpactDto(releasedClasses + other.releasedClasses,
                mergedClasses + other.mergedClasses, affectedRoomRequests + other.affectedRoomRequests, visible);
    }
}
