package ar.edu.utn.frc.siga.buildingactivity.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivityType;
import ar.edu.utn.frc.siga.buildingactivity.model.RoomRequestItemRef;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record BuildingActivityDto(Long id, BuildingActivityType type, BuildingRef building, String actor,
                                  @Schema(description = "Nombre y apellido de quien hizo el movimiento. Null si el actor es el sistema o ya no existe como usuario.")
                                  String actorFullName, AllocationSource source, String reason, Instant occurredAt, boolean seen,
                                  RoomRequestItemRef roomRequestItem, List<ActivityLine> lines) {

    public record BuildingRef(Long id, String name) {
    }
}
