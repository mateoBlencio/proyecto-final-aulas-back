package ar.edu.utn.frc.siga.roomrequest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestService;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(IntegrationTestData.class)
@DisplayName("Solicitudes de aula (integración)")
class RoomRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private RoomRequestService roomRequestService;

    @Test
    @DisplayName("crear con dos pedidos y preferencias: persiste todo y lo devuelve compuesto")
    void create_withItemsAndPreferences_persistsAndComposes() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Building building = testData.edificio();
        Classroom preferred = testData.aula(building);
        Classroom current = testData.aula(building);

        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                academic.subjectId(),
                List.of(
                        item(academic.commissionId(), LocalDate.now().plusDays(10), current.getId(),
                                List.of(preferred.getId())),
                        item(academic.commissionId(), LocalDate.now().plusDays(11), null, List.of())));

        RoomRequestResponseDto created = roomRequestService.create(dto);

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.items()).allSatisfy(
                item -> assertThat(item.status()).isEqualTo(RoomRequestStatus.PENDING));
        assertThat(created.subject().id()).isEqualTo(academic.subjectId());
        assertThat(created.items()).hasSize(2);

        var first = created.items().getFirst();
        assertThat(first.position()).isEqualTo(1);
        assertThat(first.durationMinutes()).isEqualTo(120);
        assertThat(first.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(first.commission().id()).isEqualTo(academic.commissionId());
        assertThat(first.currentClassroom().id()).isEqualTo(current.getId());
        assertThat(first.preferredClassrooms()).singleElement()
                .satisfies(room -> assertThat(room.id()).isEqualTo(preferred.getId()));
        assertThat(created.items().get(1).position()).isEqualTo(2);
        assertThat(created.items().get(1).preferredClassrooms()).isEmpty();
    }

    @Test
    @DisplayName("findById: recupera la solicitud con sus pedidos y preferencias")
    void findById_returnsFullGraph() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Classroom preferred = testData.aula(testData.edificio());

        Long id = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.FINAL_EXAM,
                AcademicScope.POSTGRADO,
                "Alan Turing",
                "alan@frc.utn.edu.ar",
                "351-7654321",
                academic.subjectId(),
                List.of(item(academic.commissionId(), LocalDate.now().plusDays(5), null,
                        List.of(preferred.getId()))))).id();

        RoomRequestResponseDto found = roomRequestService.findById(id);

        assertThat(found.id()).isEqualTo(id);
        assertThat(found.items()).singleElement()
                .satisfies(item -> assertThat(item.preferredClassrooms()).hasSize(1));
    }

    @Test
    @DisplayName("conferencia sin materia ni comisión: se acepta")
    void create_conference_withoutAcademicReference_isAccepted() {
        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.CONFERENCE,
                AcademicScope.EXTENSION,
                "Grace Hopper",
                "grace@frc.utn.edu.ar",
                "351-0000000",
                null,
                List.of(item(null, LocalDate.now().plusDays(20), null, List.of()))));

        assertThat(created.subject()).isNull();
        assertThat(created.items()).singleElement()
                .satisfies(item -> assertThat(item.commission()).isNull());
    }

    @Test
    @DisplayName("parcial sin materia: se rechaza por referencia académica faltante")
    void create_partialExam_withoutSubject_isRejected() {
        CreateRoomRequestDto dto = new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Ada Lovelace",
                "ada@frc.utn.edu.ar",
                "351-1234567",
                null,
                List.of(item(null, LocalDate.now().plusDays(3), null, List.of())));

        assertThatThrownBy(() -> roomRequestService.create(dto))
                .isInstanceOf(InvalidRoomRequestException.class)
                .hasMessageContaining("subjectId es obligatorio");
    }

    @Test
    @DisplayName("ciclo de estados de un pedido: PENDING -> PRE_APPROVED -> CANCELLED, y no se puede volver")
    void itemStatusTransitions_followTheStateMachine() {
        RoomRequestResponseDto created = roomRequestService.create(oneItemRequest());
        Long id = created.id();
        Long itemId = created.items().getFirst().id();

        RoomRequestResponseDto preApproved = roomRequestService.preApproveItem(
                id, itemId, "subsecretaria@frc.utn.edu.ar", "Hay aula disponible");
        var item = preApproved.items().getFirst();
        assertThat(item.status()).isEqualTo(RoomRequestStatus.PRE_APPROVED);
        assertThat(item.decidedBy()).isEqualTo("subsecretaria@frc.utn.edu.ar");
        assertThat(item.decidedAt()).isNotNull();
        assertThat(item.decisionReason()).isEqualTo("Hay aula disponible");

        RoomRequestResponseDto cancelled = roomRequestService.cancelItem(
                id, itemId, "subsecretaria@frc.utn.edu.ar", "El docente se dio de baja");
        assertThat(cancelled.items().getFirst().status()).isEqualTo(RoomRequestStatus.CANCELLED);

        assertThatThrownBy(() ->
                roomRequestService.preApproveItem(id, itemId, "subsecretaria@frc.utn.edu.ar", null))
                .isInstanceOf(InvalidRoomRequestTransitionException.class);
    }

    @Test
    @DisplayName("decidir un pedido no toca a los otros: el parcial de abril se resuelve y el de julio queda pendiente")
    void decidingOneItem_leavesTheOthersUntouched() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        RoomRequestResponseDto created = roomRequestService.create(new CreateRoomRequestDto(
                RoomRequestType.PARTIAL_EXAM,
                AcademicScope.GRADO,
                "Barbara Liskov",
                "barbara@frc.utn.edu.ar",
                "351-2222222",
                academic.subjectId(),
                List.of(
                        item(academic.commissionId(), LocalDate.now().plusDays(30), null, List.of()),
                        item(academic.commissionId(), LocalDate.now().plusDays(120), null, List.of()))));

        Long firstItemId = created.items().getFirst().id();
        RoomRequestResponseDto decided = roomRequestService.preApproveItem(
                created.id(), firstItemId, "subsecretaria@frc.utn.edu.ar", "Aula reservada");

        assertThat(decided.items().getFirst().status()).isEqualTo(RoomRequestStatus.PRE_APPROVED);
        assertThat(decided.items().get(1).status()).isEqualTo(RoomRequestStatus.PENDING);
        assertThat(decided.items().get(1).decidedBy()).isNull();
        assertThat(decided.items().get(1).decidedAt()).isNull();
    }

    @Test
    @DisplayName("decidir un pedido que no es de esa solicitud: 404")
    void decidingItemFromAnotherRequest_isRejected() {
        RoomRequestResponseDto owner = roomRequestService.create(oneItemRequest());
        RoomRequestResponseDto other = roomRequestService.create(oneItemRequest());
        Long foreignItemId = other.items().getFirst().id();

        assertThatThrownBy(() -> roomRequestService.preApproveItem(
                owner.id(), foreignItemId, "subsecretaria@frc.utn.edu.ar", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private CreateRoomRequestDto oneItemRequest() {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        return new CreateRoomRequestDto(
                RoomRequestType.ONE_TIME_ROOM_CHANGE,
                AcademicScope.GRADO,
                "Edsger Dijkstra",
                "edsger@frc.utn.edu.ar",
                "351-1111111",
                academic.subjectId(),
                List.of(item(academic.commissionId(), LocalDate.now().plusDays(2), null, List.of())));
    }

    private CreateRoomRequestItemDto item(Long commissionId, LocalDate date,
                                          Integer currentClassroomId, List<Integer> preferredClassroomIds) {
        return new CreateRoomRequestItemDto(
                commissionId,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                30,
                35,
                1,
                currentClassroomId,
                true,
                false,
                null,
                false,
                null,
                "Observación de prueba",
                preferredClassroomIds);
    }
}
