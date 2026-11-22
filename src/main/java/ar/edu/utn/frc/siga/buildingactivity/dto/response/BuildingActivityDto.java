package ar.edu.utn.frc.siga.buildingactivity.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivityType;
import ar.edu.utn.frc.siga.buildingactivity.model.RoomRequestItemRef;

import java.time.Instant;
import java.util.List;

public record BuildingActivityDto(Long id, BuildingActivityType type, BuildingRef building, String actor,
                                  AllocationSource source, String reason, Instant occurredAt, boolean seen,
                                  RoomRequestItemRef roomRequestItem, List<ActivityLine> lines) {

    public record BuildingRef(Long id, String name) {
    }
}
