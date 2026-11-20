package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivityType;
import ar.edu.utn.frc.siga.buildingactivity.model.RoomRequestItemRef;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemTakenOver;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Deja en Movimientos del edificio que perdió el pedido el retomado de Subsecretaría. El mail lo manda roomrequest. */
@Component
@RequiredArgsConstructor
class RoomRequestTakenOverListener {

    private final BuildingActivityRepository repository;
    private final ObjectMapper objectMapper;

    @ApplicationModuleListener
    void on(RoomRequestItemTakenOver event) {
        if (repository.existsByOperationId(event.operationId())) return;
        RoomRequestItemRef item = new RoomRequestItemRef(event.itemId(), event.status(), event.teacherName());
        repository.save(BuildingActivity.builder()
                .type(BuildingActivityType.ROOM_REQUEST_TAKEN_OVER)
                .buildingId(event.buildingId())
                .buildingName(event.buildingName())
                .operationId(event.operationId())
                .actor(event.actor())
                .source(AllocationSource.MANUAL)
                .reason(event.reason())
                .occurredAt(event.occurredAt())
                .lines("[]")
                .roomRequestItem(objectMapper.writeValueAsString(item))
                .build());
    }
}
