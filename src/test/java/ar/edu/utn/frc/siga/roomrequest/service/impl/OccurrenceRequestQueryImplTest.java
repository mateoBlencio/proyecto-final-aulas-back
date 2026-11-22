package ar.edu.utn.frc.siga.roomrequest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemAllocationRepository.OccurrenceItemStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceRequestQueryImpl")
class OccurrenceRequestQueryImplTest {

    @Mock
    private RoomRequestItemAllocationRepository repository;
    @InjectMocks
    private OccurrenceRequestQueryImpl query;

    @ParameterizedTest(name = "{0} se traduce a {1}")
    @CsvSource({
            "NEW, PENDING",
            "DERIVED_TO_BUILDING, PENDING",
            "IN_EVALUATION, PENDING",
            "NOTIFIED, PENDING",
            "RESOLVED, ACCEPTED",
            "CANCELLED, CANCELLED"
    })
    @DisplayName("Cada estado de pedido se traduce al estado de ocurrencia correspondiente")
    void mapsEachRequestStatus(RoomRequestStatus status, OccurrenceRequestState expected) {
        when(repository.findItemStatuses(Set.of(5L))).thenReturn(List.of(new OccurrenceItemStatus(5L, 40L, status)));

        Map<Long, OccurrenceRequestStateDto> result = query.findStates(Set.of(5L));

        assertThat(result).containsOnlyKeys(5L);
        assertThat(result.get(5L)).isEqualTo(new OccurrenceRequestStateDto(5L, 40L, expected));
    }

    @ParameterizedTest(name = "{0} (ítem 40) y {1} (ítem 41): gana {2} del ítem {3}")
    @CsvSource({
            "RESOLVED, NOTIFIED, PENDING, 41",
            "NOTIFIED, RESOLVED, PENDING, 40",
            "CANCELLED, RESOLVED, ACCEPTED, 41",
            "RESOLVED, CANCELLED, ACCEPTED, 40",
            "CANCELLED, IN_EVALUATION, PENDING, 41",
            "IN_EVALUATION, CANCELLED, PENDING, 40"
    })
    @DisplayName("Con dos ítems sobre la misma ocurrencia gana el más fuerte, en cualquier orden")
    void strongestStateWinsInBothOrders(RoomRequestStatus first, RoomRequestStatus second,
            OccurrenceRequestState expected, long winningItem) {
        when(repository.findItemStatuses(Set.of(5L))).thenReturn(List.of(
                new OccurrenceItemStatus(5L, 40L, first), new OccurrenceItemStatus(5L, 41L, second)));

        OccurrenceRequestStateDto result = query.findStates(Set.of(5L)).get(5L);

        assertThat(result).isEqualTo(new OccurrenceRequestStateDto(5L, winningItem, expected));
    }

    @Test
    @DisplayName("Ocurrencias distintas no se mezclan entre sí")
    void differentOccurrencesAreIndependent() {
        when(repository.findItemStatuses(Set.of(5L, 6L))).thenReturn(List.of(
                new OccurrenceItemStatus(5L, 40L, RoomRequestStatus.RESOLVED),
                new OccurrenceItemStatus(6L, 41L, RoomRequestStatus.CANCELLED)));

        Map<Long, OccurrenceRequestStateDto> result = query.findStates(Set.of(5L, 6L));

        assertThat(result.get(5L).state()).isEqualTo(OccurrenceRequestState.ACCEPTED);
        assertThat(result.get(6L).state()).isEqualTo(OccurrenceRequestState.CANCELLED);
    }

    @Test
    @DisplayName("Una consulta al repositorio por lote y las ocurrencias sin ítem no aparecen")
    void occurrencesWithoutItemsAreAbsent() {
        when(repository.findItemStatuses(Set.of(5L, 6L))).thenReturn(List.of());

        assertThat(query.findStates(Set.of(5L, 6L))).isEmpty();
        verify(repository).findItemStatuses(Set.of(5L, 6L));
    }

    @Test
    @DisplayName("Colección vacía devuelve mapa vacío sin consultar el repositorio")
    void emptyCollectionDoesNotQuery() {
        assertThat(query.findStates(List.of())).isEmpty();

        verifyNoInteractions(repository);
    }
}
