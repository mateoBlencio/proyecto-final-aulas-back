package ar.edu.utn.frc.siga.events;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.core.EventSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Event publications stored by Modulith before V16 have the old payload. Uses the {@link EventSerializer}
 * bean of the application, the same one Modulith uses to read the event publication registry.
 */
@DisplayName("OccurrenceVacated: compatibilidad de publicaciones pendientes (integración)")
class OccurrenceVacatedPayloadCompatibilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventSerializer eventSerializer;

    @Test
    @DisplayName("un payload viejo sin originOperationId se deserializa con originOperationId null")
    void oldPayloadDeserializesWithNullOrigin() {
        OccurrenceVacated event = eventSerializer.deserialize("{\"occurrenceId\":1}", OccurrenceVacated.class);

        assertThat(event.occurrenceId()).isEqualTo(1L);
        assertThat(event.originOperationId()).isNull();
    }

    @Test
    @DisplayName("un payload nuevo conserva originOperationId")
    void newPayloadKeepsOrigin() {
        Object payload = eventSerializer.serialize(new OccurrenceVacated(2L, "op-1"));

        OccurrenceVacated event = eventSerializer.deserialize(payload, OccurrenceVacated.class);

        assertThat(payload.toString()).contains("originOperationId");
        assertThat(event).isEqualTo(new OccurrenceVacated(2L, "op-1"));
    }
}
