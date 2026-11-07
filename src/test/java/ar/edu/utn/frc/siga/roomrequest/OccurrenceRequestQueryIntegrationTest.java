package ar.edu.utn.frc.siga.roomrequest;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(IntegrationTestData.class)
@DisplayName("OccurrenceRequestQuery.findStates (integración, JPQL contra Postgres)")
class OccurrenceRequestQueryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OccurrenceRequestQuery query;
    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private SubjectAndCommission academic;
    private Classroom classroom;

    private Long newOccurrence() {
        if (academic == null) {
            academic = testData.materiaYComision();
            classroom = testData.aula(testData.edificio());
        }
        LocalDate date = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .plusWeeks(IntegrationTestData.nextSeq() % 500);
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), DayOfWeek.MONDAY,
                LocalTime.of(8, 0), 60, date, date, 30);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        return occurrence.getId();
    }

    private Long itemOn(Long occurrenceId, RoomRequestStatus status) {
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(),
                LocalDate.now().plusDays(10), status);
        roomRequestRepository.save(request);
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) VALUES (?, ?, ?, 1)",
                item.getId(), occurrenceId, classroom.getId());
        return item.getId();
    }

    @Test
    @DisplayName("Cada estado de pedido llega como su estado de ocurrencia, incluido IN_EVALUATION sin notifiedAt")
    void mapsEachStatusAgainstRealDatabase() {
        Long newOcc = newOccurrence();
        Long derivedOcc = newOccurrence();
        Long evaluationOcc = newOccurrence();
        Long notifiedOcc = newOccurrence();
        Long resolvedOcc = newOccurrence();
        Long cancelledOcc = newOccurrence();
        Long newItem = itemOn(newOcc, RoomRequestStatus.NEW);
        Long derivedItem = itemOn(derivedOcc, RoomRequestStatus.DERIVED_TO_BUILDING);
        Long evaluationItem = itemOn(evaluationOcc, RoomRequestStatus.IN_EVALUATION);
        Long notifiedItem = itemOn(notifiedOcc, RoomRequestStatus.NOTIFIED);
        Long resolvedItem = itemOn(resolvedOcc, RoomRequestStatus.RESOLVED);
        Long cancelledItem = itemOn(cancelledOcc, RoomRequestStatus.CANCELLED);

        Integer notifiedAtCount = jdbc.queryForObject(
                "select count(*) from solicitud_aula_item where id_item = ? and fecha_notificacion is null",
                Integer.class, evaluationItem);
        Map<Long, OccurrenceRequestStateDto> states = query.findStates(
                List.of(newOcc, derivedOcc, evaluationOcc, notifiedOcc, resolvedOcc, cancelledOcc));

        assertThat(notifiedAtCount).isEqualTo(1);
        assertThat(states).containsOnly(
                Map.entry(newOcc, new OccurrenceRequestStateDto(newOcc, newItem, OccurrenceRequestState.PENDING)),
                Map.entry(derivedOcc,
                        new OccurrenceRequestStateDto(derivedOcc, derivedItem, OccurrenceRequestState.PENDING)),
                Map.entry(evaluationOcc,
                        new OccurrenceRequestStateDto(evaluationOcc, evaluationItem, OccurrenceRequestState.PENDING)),
                Map.entry(notifiedOcc,
                        new OccurrenceRequestStateDto(notifiedOcc, notifiedItem, OccurrenceRequestState.PENDING)),
                Map.entry(resolvedOcc,
                        new OccurrenceRequestStateDto(resolvedOcc, resolvedItem, OccurrenceRequestState.ACCEPTED)),
                Map.entry(cancelledOcc,
                        new OccurrenceRequestStateDto(cancelledOcc, cancelledItem, OccurrenceRequestState.CANCELLED)));
    }

    @Test
    @DisplayName("Con un ítem aceptado y otro sin aceptar sobre la misma ocurrencia gana el pendiente, en cualquier orden de alta")
    void pendingWinsOverAcceptedInBothInsertionOrders() {
        Long acceptedFirstOcc = newOccurrence();
        Long acceptedFirst = itemOn(acceptedFirstOcc, RoomRequestStatus.RESOLVED);
        Long pendingSecond = itemOn(acceptedFirstOcc, RoomRequestStatus.IN_EVALUATION);
        Long pendingFirstOcc = newOccurrence();
        Long pendingFirst = itemOn(pendingFirstOcc, RoomRequestStatus.IN_EVALUATION);
        Long acceptedSecond = itemOn(pendingFirstOcc, RoomRequestStatus.RESOLVED);

        Map<Long, OccurrenceRequestStateDto> states = query.findStates(List.of(acceptedFirstOcc, pendingFirstOcc));

        assertThat(states.get(acceptedFirstOcc))
                .isEqualTo(new OccurrenceRequestStateDto(acceptedFirstOcc, pendingSecond, OccurrenceRequestState.PENDING));
        assertThat(states.get(pendingFirstOcc))
                .isEqualTo(new OccurrenceRequestStateDto(pendingFirstOcc, pendingFirst, OccurrenceRequestState.PENDING));
        assertThat(List.of(acceptedFirst, acceptedSecond)).doesNotContainAnyElementsOf(
                List.of(states.get(acceptedFirstOcc).itemId(), states.get(pendingFirstOcc).itemId()));
    }

    @Test
    @DisplayName("Con un ítem cancelado y otro aceptado sobre la misma ocurrencia gana el aceptado")
    void acceptedWinsOverCancelled() {
        Long occurrence = newOccurrence();
        itemOn(occurrence, RoomRequestStatus.CANCELLED);
        Long accepted = itemOn(occurrence, RoomRequestStatus.RESOLVED);

        Map<Long, OccurrenceRequestStateDto> states = query.findStates(List.of(occurrence));

        assertThat(states.get(occurrence))
                .isEqualTo(new OccurrenceRequestStateDto(occurrence, accepted, OccurrenceRequestState.ACCEPTED));
    }

    @Test
    @DisplayName("Una ocurrencia sin ítems no aparece y una colección vacía devuelve mapa vacío")
    void occurrenceWithoutItemsAndEmptyCollection() {
        Long occurrence = newOccurrence();

        assertThat(query.findStates(List.of(occurrence))).isEmpty();
        assertThat(query.findStates(List.of())).isEmpty();
    }
}
