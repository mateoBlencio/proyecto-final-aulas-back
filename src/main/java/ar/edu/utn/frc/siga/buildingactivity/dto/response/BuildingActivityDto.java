package ar.edu.utn.frc.siga.buildingactivity.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;

import java.time.Instant;
import java.util.List;

public record BuildingActivityDto(Long id, BuildingRef building, String actor, AllocationSource source,
                                  String reason, Instant occurredAt, boolean seen, List<ActivityLine> lines) {

    public record BuildingRef(Long id, String name) {
    }
}
