package ar.edu.utn.frc.siga.buildingactivity.service;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivityType;
import ar.edu.utn.frc.siga.buildingactivity.model.RoomRequestItemRef;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemTakenOver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestTakenOverListener")
class RoomRequestTakenOverListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-18T12:00:00Z");

    @Mock
    private BuildingActivityRepository repository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RoomRequestTakenOverListener listener;

    @BeforeEach
    void setUp() {
        listener = new RoomRequestTakenOverListener(repository, objectMapper);
    }

    @Test
    @DisplayName("operationId nuevo: guarda una entrada ROOM_REQUEST_TAKEN_OVER con todos los campos y sin lineas")
    void savesTakeOverEntry() {
        when(repository.existsByOperationId("op-1")).thenReturn(false);

        listener.on(event("op-1"));

        ArgumentCaptor<BuildingActivity> captor = ArgumentCaptor.forClass(BuildingActivity.class);
        verify(repository).save(captor.capture());
        BuildingActivity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(BuildingActivityType.ROOM_REQUEST_TAKEN_OVER);
        assertThat(saved.getBuildingId()).isEqualTo(3L);
        assertThat(saved.getBuildingName()).isEqualTo("Central");
        assertThat(saved.getOperationId()).isEqualTo("op-1");
        assertThat(saved.getActor()).isEqualTo("sub@utn.edu.ar");
        assertThat(saved.getSource()).isEqualTo(AllocationSource.MANUAL);
        assertThat(saved.getReason()).isEqualTo("lo resuelve subsecretaría");
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.getLines()).isEqualTo("[]");
        assertThat(objectMapper.readValue(saved.getRoomRequestItem(), RoomRequestItemRef.class))
                .isEqualTo(new RoomRequestItemRef(42L, "IN_EVALUATION", "Ada Lovelace"));
    }

    @Test
    @DisplayName("operationId ya registrado (reentrega): no guarda nada")
    void ignoresRedeliveredOperation() {
        when(repository.existsByOperationId("op-1")).thenReturn(true);

        listener.on(event("op-1"));

        verify(repository, never()).save(any());
    }

    private RoomRequestItemTakenOver event(String operationId) {
        return new RoomRequestItemTakenOver(operationId, 42L, 3L, "Central", "IN_EVALUATION", "Ada Lovelace",
                "lo resuelve subsecretaría", "sub@utn.edu.ar", OCCURRED_AT);
    }
}
